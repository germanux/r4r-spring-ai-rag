package com.riansares.r4r.vector;

import com.fasterxml.jackson.databind.*;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.config.*;
import com.riansares.r4r.rag.*;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.s3vectors.*;
import com.riansares.r4r.vector.config.RagVectorProperties;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.embedding.*;
import org.springframework.ai.ollama.api.*;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3vectors.*;

/** Explicit read-only diagnostic, never discovered as a default test. No Boot app, Flyway, IAM, writes or Bedrock. */
public final class NomicRetrievalDiagnostic {
    static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception failure) { System.err.println("Read-only Nomic diagnostic failed: " + failure.getClass().getSimpleName()); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        boolean run=false, generate=false;Path output=Path.of("target/nomic-readonly-diagnostic.json"), questions=null;
        for(int i=0;i<args.length;i++)switch(args[i]){
            case "--run" -> run=true;
            case "--generate-local" -> generate=true;
            case "--output" -> output=Path.of(args[++i]);
            case "--questions" -> questions=Path.of(args[++i]);
            default -> throw new IllegalArgumentException("Unknown diagnostic argument");
        }
        if(!run){System.out.println("NOT RUN: use --run for read-only PG/S3/Nomic; --generate-local additionally enables local LLM calls");return;}
        var env=new StandardEnvironment();
        // Native production imports resolve credentials internally; no private file parsing or credential output.
        env.getPropertySources().addFirst(new MapPropertySource("diagnostic-only",Map.of(
                "spring.config.location","file:./src/main/resources/application.yml","r4r.rag.openai.enabled","false")));
        ConfigDataEnvironmentPostProcessor.applyTo(env);PublicRagEnvironment.apply(env);
        var embeddingConfig=Binder.get(env).bind("r4r.rag.embedding",EmbeddingFailoverProperties.class).get();
        var options=OllamaOptions.builder().model(env.getRequiredProperty("spring.ai.ollama.embedding.options.model")).build();
        var embedding=new ObservedEmbeddingModel(OllamaApi.builder().baseUrl(embeddingConfig.providers().getFirst().baseUrl()).build(),options,
                new EmbeddingProviderRouter(embeddingConfig,options,Duration.ofSeconds(180)));
        var routing=Binder.get(env).bind("r4r.rag.vector",RagVectorProperties.class).get();
        if(!"amzn-r4r-vectors".equals(routing.s3vectors().bucket())||!"amzn-r4r-vector-index".equals(routing.s3vectors().index()))
            throw new IllegalStateException("Diagnostic requires the explicitly expected original Nomic index");
        int topK=env.getRequiredProperty("r4r.rag.retrieval.top-k",Integer.class);
        double minScore=env.getRequiredProperty("r4r.rag.retrieval.min-score",Double.class);
        var ranking=Binder.get(env).bind("r4r.rag.retrieval.rerank",NomicRerankProperties.class).orElse(NomicRerankProperties.defaults());
        var legacy=new NomicRerankProperties(1,0);
        var properties=new Properties();properties.setProperty("user",env.getRequiredProperty("spring.datasource.username"));
        properties.setProperty("password",env.getRequiredProperty("spring.datasource.password"));properties.setProperty("connectTimeout","3");
        properties.setProperty("socketTimeout","15");properties.setProperty("options","-c default_transaction_read_only=on");
        var report=new LinkedHashMap<String,Object>();report.put("topK",topK);report.put("minScore",minScore);report.put("rerank",ranking);
        var rows=JSON.createArrayNode();
        if(questions!=null)rows.addAll((com.fasterxml.jackson.databind.node.ArrayNode)JSON.readTree(questions.toFile()));
        else for(var text:List.of("¿Con qué reformaríais una cueva?","¿Con qué reformaríais una bodega?","¿Qué mortero utilizaríais para rehabilitar una cueva antigua?"))rows.addObject().put("question",text).put("lang","es");
        var selected=new AtomicReference<List<MarkdownChunk>>(List.of());
        KnowledgeVectorStore supplied=new KnowledgeVectorStore(){
            public void index(List<MarkdownChunk> chunks){throw new UnsupportedOperationException("Read-only diagnostic");}
            public void replaceSource(String source,List<MarkdownChunk> chunks){throw new UnsupportedOperationException("Read-only diagnostic");}
            public List<MarkdownChunk> search(String q,int k,double score){return selected.get();}
        };
        try(var connection=DriverManager.getConnection(env.getRequiredProperty("spring.datasource.url"),properties);
            var credentials=ProfileCredentialsProvider.builder().profileName("r4r-codex").build();
            var aws=S3VectorsClient.builder().credentialsProvider(credentials).region(Region.EU_NORTH_1)
                    .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(10)))
                    .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(12))
                            .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build();
            var generation=new AnnotationConfigApplicationContext()) {
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            if(!"on".equals(jdbc.queryForObject("SELECT current_setting('transaction_read_only')",String.class)))throw new IllegalStateException("Read-only required");
            report.put("postgresReadOnly",true);report.put("postgresRows",jdbc.queryForObject("SELECT count(*) FROM vector_store",Integer.class));
            if(generate){generation.setEnvironment(env);generation.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());generation.registerBean(KnowledgeVectorStore.class,()->supplied);generation.register(RagConfiguration.class);generation.refresh();}
            var results=new ArrayList<Map<String,Object>>();report.put("queries",results);
            for(var row:rows){
                String question=row.path("question").asText(),search=row.path("retrievalQuestion").asText(question),language=row.path("lang").asText("es");
                long start=System.nanoTime();float[] data=embedding.embed(search);double embeddingMs=(System.nanoTime()-start)/1_000_000.0;
                EmbeddingModel fixed=new EmbeddingModel(){
                    public float[] embed(String ignored){return data;}
                    public float[] embed(org.springframework.ai.document.Document ignored){throw new UnsupportedOperationException();}
                    public EmbeddingResponse call(EmbeddingRequest ignored){return new EmbeddingResponse(List.of(new Embedding(data,0)));}
                    public int dimensions(){return 768;}
                };
                var nativePg=PgVectorStore.builder(jdbc,fixed).dimensions(768).initializeSchema(false).vectorTableValidationsEnabled(true).build();
                nativePg.afterPropertiesSet();
                var beforePg=new PgVectorKnowledgeStore(nativePg,jdbc,legacy).search(search,topK,minScore);
                var afterPg=new PgVectorKnowledgeStore(nativePg,jdbc,ranking).search(search,topK,minScore);
                var s3Config=routing.s3vectors();
                var beforeS3=new S3VectorsKnowledgeStore(aws,fixed,s3Config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),legacy).search(search,topK,minScore);
                var afterS3=new S3VectorsKnowledgeStore(aws,fixed,s3Config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),ranking).search(search,topK,minScore);
                var result=new LinkedHashMap<String,Object>();result.put("question",question);result.put("embeddingInput",search);result.put("embeddingDimensions",data.length);
                result.put("embeddingMs",embeddingMs);result.put("postgresBefore",beforePg);result.put("postgresAfter",afterPg);result.put("s3Before",beforeS3);result.put("s3After",afterS3);
                if(generate){
                    var rag=generation.getBean(CitedRagService.class);selected.set(beforePg);
                    result.put("generationBefore",rag.answer(question,search,language));selected.set(afterPg);
                    result.put("generationAfter",rag.answer(question,search,language));
                    // Include original English/Spanish labels for side-by-side evidence review, never assert semantic quality from a canned response.
                    if(row.has("evidence"))result.put("expectedEvidence",row.get("evidence").booleanValue());
                }
                results.add(result);Files.createDirectories(output.toAbsolutePath().getParent());JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
                System.out.println("Completed PG/S3 read-only comparison: "+question);
            }
        }
    }
}
