package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.rag.EmbeddingConfigurationException;
import com.riansares.r4r.rag.EmbeddingUnavailableException;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-query read routing. Writes always use the transactional PostgreSQL adapter. */
public final class FailoverKnowledgeVectorStore implements KnowledgeVectorStore {
    private static final Logger log=LoggerFactory.getLogger(FailoverKnowledgeVectorStore.class);
    private final Supplier<? extends KnowledgeVectorStore> postgres;
    private final KnowledgeVectorStore s3;
    private final boolean primaryS3, fallback, readOnly;

    public FailoverKnowledgeVectorStore(Supplier<? extends KnowledgeVectorStore> postgres, KnowledgeVectorStore s3,
            String primary, boolean fallback, boolean readOnly) {
        if (!Set.of("pgvector","s3vectors").contains(primary)) throw new IllegalArgumentException("Unknown vector primary");
        if ((primary.equals("s3vectors") || fallback) && s3==null)
            throw new IllegalArgumentException("S3 Vectors must be enabled for S3 primary or failover");
        if (primary.equals("s3vectors") && fallback)
            throw new IllegalArgumentException("Vector failover applies only to pgvector primary");
        if (readOnly && !primary.equals("s3vectors")) throw new IllegalArgumentException("S3 read-only mode requires S3 primary");
        this.postgres=Objects.requireNonNull(postgres); this.s3=s3;
        this.primaryS3=primary.equals("s3vectors"); this.fallback=fallback; this.readOnly=readOnly;
    }
    @Override public void index(List<MarkdownChunk> chunks) { writable().index(chunks); }
    @Override public void replaceSource(String source,List<MarkdownChunk> chunks) { writable().replaceSource(source,chunks); }
    private KnowledgeVectorStore writable() {
        if (readOnly) throw new VectorStoreUnavailableException("Writes are disabled in S3 read-only mode",null);
        return postgres.get();
    }
    @Override public List<MarkdownChunk> search(String query,int topK,double minScore) {
        checkCancelled();
        if (primaryS3) return measured(s3,"s3vectors",query,topK,minScore);
        try { return measured(postgres.get(),"pgvector",query,topK,minScore); }
        catch (RuntimeException failure) {
            propagateCancellation(failure);
            String reason=recoverablePostgres(failure);
            if (!fallback || reason==null) throw failure;
            checkCancelled();
            log.warn("Vector fallback from=pgvector to=s3vectors reason={}",reason);
            try { return measured(s3,"s3vectors",query,topK,minScore); }
            catch (RuntimeException secondary) {
                propagateCancellation(secondary);
                checkCancelled();
                var unavailable=new VectorStoreUnavailableException("Vector retrieval unavailable",failure);
                unavailable.addSuppressed(secondary);
                throw unavailable;
            }
        }
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
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var pending=new ArrayDeque<Throwable>(); pending.add(failure);
        boolean jdbc=false, connection=false; String reason=null;
        while (!pending.isEmpty()) {
            Throwable cause=pending.removeFirst(); if (!seen.add(cause)) continue;
            if (cause instanceof CancellationException || cause instanceof InterruptedException
                    || cause instanceof java.nio.channels.ClosedByInterruptException
                    || cause instanceof java.io.InterruptedIOException && !(cause instanceof java.net.SocketTimeoutException)) {
                if (!(cause instanceof CancellationException)) Thread.currentThread().interrupt();
                return null;
            }
            if (cause instanceof EmbeddingUnavailableException || cause instanceof EmbeddingConfigurationException
                    || cause instanceof IllegalArgumentException || cause instanceof javax.net.ssl.SSLException
                    || cause instanceof org.springframework.beans.BeansException)
                return null;
            if (cause instanceof CannotGetJdbcConnectionException) { jdbc=true; reason="jdbc_connection"; }
            if (cause instanceof SQLException sql) {
                jdbc=true; String state=sql.getSQLState();
                if (state!=null && !state.isBlank()) {
                    if (state.matches("08[A-Z0-9]{3}") || Set.of("57P01","57P02","57P03","53300").contains(state)) reason="sqlstate_"+state;
                    else if (sql instanceof SQLTimeoutException && Set.of("HYT00","HYT01").contains(state)) reason="jdbc_timeout";
                    else return null; // Authentication, syntax, data/schema errors and SQL cancellation veto fallback.
                } else if (sql instanceof SQLTimeoutException) reason="jdbc_timeout";
                else if (sql instanceof SQLTransientConnectionException) reason="jdbc_connection";
                if (sql.getNextException()!=null) pending.add(sql.getNextException());
            }
            connection |= cause instanceof java.net.ConnectException || cause instanceof java.net.SocketTimeoutException;
            if (cause.getCause()!=null) pending.add(cause.getCause());
        }
        checkCancelled();
        return jdbc ? reason!=null ? reason : connection ? "jdbc_transport" : null : null;
    }
}
