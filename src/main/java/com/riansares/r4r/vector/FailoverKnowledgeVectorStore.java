package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-query read routing. Writes always use the transactional PostgreSQL adapter. */
public final class FailoverKnowledgeVectorStore implements KnowledgeVectorStore {
    private static final Logger log=LoggerFactory.getLogger(FailoverKnowledgeVectorStore.class);
    private final Supplier<? extends KnowledgeVectorStore> postgres;
    private final KnowledgeVectorStore s3;
    private final boolean primaryS3, fallback, readOnly;
    private final PostgresAccessGate gate;
    private final KnowledgeVectorStore titan;

    public FailoverKnowledgeVectorStore(Supplier<? extends KnowledgeVectorStore> postgres, KnowledgeVectorStore s3,
            String primary, boolean fallback, boolean readOnly) {
        this(postgres, s3, primary, fallback, readOnly, null);
    }
    public FailoverKnowledgeVectorStore(Supplier<? extends KnowledgeVectorStore> postgres, KnowledgeVectorStore s3,
            String primary, boolean fallback, boolean readOnly, PostgresAccessGate gate) {
        this(postgres, s3, primary, fallback, readOnly, gate, null);
    }
    public FailoverKnowledgeVectorStore(Supplier<? extends KnowledgeVectorStore> postgres, KnowledgeVectorStore s3,
            String primary, boolean fallback, boolean readOnly, PostgresAccessGate gate, KnowledgeVectorStore titan) {
        if (!Set.of("pgvector","s3vectors").contains(primary)) throw new IllegalArgumentException("Unknown vector primary");
        if ((primary.equals("s3vectors") || fallback) && s3==null)
            throw new IllegalArgumentException("S3 Vectors must be enabled for S3 primary or failover");
        if (primary.equals("s3vectors") && fallback)
            throw new IllegalArgumentException("Vector failover applies only to pgvector primary");
        if (readOnly && !primary.equals("s3vectors")) throw new IllegalArgumentException("S3 read-only mode requires S3 primary");
        this.postgres=Objects.requireNonNull(postgres); this.s3=s3;
        this.primaryS3=primary.equals("s3vectors"); this.fallback=fallback; this.readOnly=readOnly;
        this.gate = gate;
        this.titan = titan;
    }
    @Override public void index(List<MarkdownChunk> chunks) { writable().index(chunks); }
    @Override public void replaceSource(String source,List<MarkdownChunk> chunks) { writable().replaceSource(source,chunks); }
    private KnowledgeVectorStore writable() {
        if (readOnly) throw new VectorStoreUnavailableException("Writes are disabled in S3 read-only mode",null);
        if (gate != null) gate.beforeWrite();
        return postgres.get();
    }
    @Override public List<MarkdownChunk> search(String query,int topK,double minScore) {
        checkCancelled();
        if (primaryS3) return secondarySearch(query, topK, minScore);
        try {
            long started = System.nanoTime();
            String result = "failed";
            try {
                if (gate != null) gate.beforeRead();
                var chunks = postgres.get().search(query, topK, minScore);
                checkCancelled(); result = "success"; return chunks;
            } finally {
                log.info("Vector retrieval provider=pgvector result={} durationMs={}", result, (System.nanoTime()-started)/1_000_000.0);
            }
        }
        catch (RuntimeException failure) {
            propagateCancellation(failure);
            if (gate != null) gate.recordFailure(failure);
            String reason=recoverablePostgres(failure);
            // Both existing stores share Nomic. Its transport outage cannot be solved by retrying it through S3.
            if (fallback && titan != null && failure instanceof com.riansares.r4r.rag.EmbeddingUnavailableException) {
                log.warn("Vector fallback from=pgvector to=titan reason=nomic_unavailable");
                return measured(titan, "titan", query, topK, minScore);
            }
            if (!fallback || reason==null) throw failure;
            checkCancelled();
            log.warn("Vector fallback from=pgvector to=s3vectors reason={}",reason);
            try { return secondarySearch(query,topK,minScore); }
            catch (RuntimeException secondary) {
                propagateCancellation(secondary);
                checkCancelled();
                log.warn("Vector retrieval result=both_unavailable primary=pgvector secondary=s3vectors");
                var unavailable=new VectorStoreUnavailableException("Vector retrieval unavailable",failure);
                unavailable.addSuppressed(secondary);
                throw unavailable;
            }
        }
    }
    private List<MarkdownChunk> secondarySearch(String query, int topK, double minScore) {
        try { return measured(s3, "s3vectors", query, topK, minScore); }
        catch (RuntimeException failure) {
            propagateCancellation(failure);
            checkCancelled();
            // Invalid inputs/provenance are regressions, never a reason to bypass validation.
            if (titan == null || !remoteUnavailable(failure)) throw failure;
            log.warn("Vector fallback from=s3vectors to=titan reason=provider_unavailable");
            try { return measured(titan, "titan", query, topK, minScore); }
            catch (RuntimeException tertiary) {
                propagateCancellation(tertiary);
                checkCancelled();
                var unavailable = new VectorStoreUnavailableException("Vector retrieval unavailable", failure);
                unavailable.addSuppressed(tertiary);
                throw unavailable;
            }
        }
    }
    private static boolean remoteUnavailable(Throwable failure) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof software.amazon.awssdk.core.exception.SdkException
                    || cause instanceof VectorStoreUnavailableException
                    || cause instanceof com.riansares.r4r.rag.EmbeddingUnavailableException) return true;
        }
        return false;
    }
    private static List<MarkdownChunk> measured(KnowledgeVectorStore store,String provider,String query,int topK,double score) {
        long started=System.nanoTime(); String result="failed";
        try {
            var chunks=store.search(query,topK,score); checkCancelled(); result="success"; return chunks;
        } finally {
            log.info("Vector retrieval provider={} result={} durationMs={}",provider,result,(System.nanoTime()-started)/1_000_000.0);
        }
    }
    private static void propagateCancellation(RuntimeException failure) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for (Throwable cause=failure; cause!=null && seen.add(cause); cause=cause.getCause()) {
            if (cause instanceof CancellationException) throw failure;
            if (cause instanceof InterruptedException || cause instanceof java.nio.channels.ClosedByInterruptException
                    || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException)) {
                Thread.currentThread().interrupt(); throw failure;
            }
        }
    }
    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Vector retrieval cancelled");
    }
    static String recoverablePostgres(Throwable failure) {
        return PostgresFailureClassifier.reason(failure);
    }
}
