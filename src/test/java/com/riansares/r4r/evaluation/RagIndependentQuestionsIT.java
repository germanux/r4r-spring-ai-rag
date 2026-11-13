package com.riansares.r4r.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.*;
import com.riansares.r4r.rag.*;
import com.riansares.r4r.vector.*;
import com.riansares.r4r.vector.config.*;
import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

/** Phase 1 capture only: no relevance assertions, expected answers, corpus inspection or tuning. */
@Tag("independent-rag")
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named="r4r.test.independent.enabled",matches="true",
        disabledReason="NOT RUN: independent phase 1 requires explicit approval and r4r.test.independent.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagIndependentQuestionsIT {
    final ObjectMapper json=new ObjectMapper();
    final Path output=Path.of(System.getProperty("r4r.test.independent.output","target/independent-rag/phase1.json"));
    final Map<String,Object> report=new LinkedHashMap<>();
    final List<Map<String,Object>> records=new ArrayList<>();
    AnnotationConfigApplicationContext context;
    RecordingKnowledgeStore recording;
    RagRetrievalProperties retrieval;
    RagVectorProperties routing;

    @BeforeAll void prepare() throws Exception {
        for(var q:EvaluationQuestions.cases("es")) {
            var entry=new LinkedHashMap<String,Object>();entry.put("caseId",q.caseId());entry.put("name",q.name());
            entry.put("question",q.question());entry.put("status","NOT_RUN");records.add(entry);
        }
        report.put("phase",1);report.put("qualityEvaluationPerformed",false);report.put("startedAt",Instant.now().toString());
        report.put("executionScope","current backend components in-process; excludes deployed HTTP/security/SSE and startup migration/recovery gate");
        report.put("cases",records);save();
        try {
            var env=new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("independent-config-location",
                    Map.of("spring.config.location","file:./src/main/resources/application.yml")));
            ConfigDataEnvironmentPostProcessor.applyTo(env);PublicRagEnvironment.apply(env);
            var binder=Binder.get(env);
            routing=binder.bind("r4r.rag.vector",RagVectorProperties.class).get();
            retrieval=binder.bind("r4r.rag.retrieval",RagRetrievalProperties.class).get();
            var ranking=binder.bind("r4r.rag.retrieval.rerank",NomicRerankProperties.class).orElse(NomicRerankProperties.defaults());
            var titan=binder.bind("r4r.rag.vector.titan",TitanRetrievalProperties.class).get();
            report.put("configuration",Map.of("topK",retrieval.topK(),"minScore",retrieval.minScore(),"rerank",ranking,
                    "primary",routing.primary(),"failoverEnabled",routing.failoverEnabled(),"titanEnabled",titan.enabled(),
                    "openaiEnabled",com.riansares.r4r.rag.OpenAiMode.parse(env.getProperty("r4r.rag.openai.enabled", "false")) != com.riansares.r4r.rag.OpenAiMode.DISABLED));
            var source=new DriverManagerDataSource(env.getRequiredProperty("spring.datasource.url"));
            source.setUsername(env.getRequiredProperty("spring.datasource.username"));source.setPassword(env.getRequiredProperty("spring.datasource.password"));
            var jdbcProperties=new Properties();jdbcProperties.setProperty("options","-c default_transaction_read_only=on");
            jdbcProperties.setProperty("connectTimeout","3");jdbcProperties.setProperty("socketTimeout","15");source.setConnectionProperties(jdbcProperties);
            context=new AnnotationConfigApplicationContext();context.setEnvironment(env);
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.register(OllamaEmbeddingConfiguration.class,S3VectorsConfiguration.class,TitanRetrievalConfiguration.class,RagConfiguration.class);
            context.registerBean(JdbcTemplate.class,()->new JdbcTemplate(source));
            context.registerBean("independentPgVector",VectorStore.class,()->PgVectorStore.builder(context.getBean(JdbcTemplate.class),
                    context.getBean(EmbeddingModel.class)).dimensions(768).initializeSchema(false).vectorTableValidationsEnabled(true).build(),definition->definition.setLazyInit(true));
            context.registerBean("independentPgStore",PgVectorKnowledgeStore.class,()->new PgVectorKnowledgeStore(
                    context.getBean(VectorStore.class),context.getBean(JdbcTemplate.class),ranking),definition->{definition.setLazyInit(true);((org.springframework.beans.factory.support.AbstractBeanDefinition)definition).setDefaultCandidate(false);});
            var credentials=ProfileCredentialsProvider.builder().profileName("r4r-codex").build();
            context.registerBean("independentAwsCredentials",ProfileCredentialsProvider.class,()->credentials);
            if(env.getProperty("r4r.rag.vector.s3vectors.enabled",Boolean.class,false)) {
                var config=routing.s3vectors();config.validate();
                context.registerBean("s3VectorsClient",S3VectorsClient.class,()->S3VectorsClient.builder().credentialsProvider(credentials)
                        .region(Region.of(config.region())).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(config.connectTimeout()).socketTimeout(config.apiCallAttemptTimeout()))
                        .overrideConfiguration(c->c.apiCallTimeout(config.apiCallTimeout()).apiCallAttemptTimeout(config.apiCallAttemptTimeout())
                                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build());
            }
            if(titan.enabled()) {
                titan.validate();context.registerBean("titanLambdaClient",LambdaClient.class,()->LambdaClient.builder().credentialsProvider(credentials)
                        .region(Region.of(titan.region())).httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(1)).socketTimeout(titan.timeout()))
                        .overrideConfiguration(c->c.apiCallTimeout(titan.timeout()).apiCallAttemptTimeout(titan.timeout())
                                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())).build());
            }
            context.registerBean("independentRecordingStore",KnowledgeVectorStore.class,()->{
                var s3=context.containsBean("s3VectorsKnowledgeVectorStore")?context.getBean("s3VectorsKnowledgeVectorStore",KnowledgeVectorStore.class):null;
                var titanStore=context.containsBean("titanKnowledgeVectorStore")?context.getBean("titanKnowledgeVectorStore",KnowledgeVectorStore.class):null;
                recording=new RecordingKnowledgeStore(new FailoverKnowledgeVectorStore(()->context.getBean(PgVectorKnowledgeStore.class),s3,
                        routing.primary(),routing.failoverEnabled(),env.matchesProfiles("s3-readonly"),null,titanStore));
                return recording;
            });
            context.refresh();save();
        } catch(Exception failure) { report.put("setupErrorType",failure.getClass().getSimpleName());save();throw failure; }
    }
    @TestFactory Stream<DynamicTest> captureQuestions() throws Exception {
        return EvaluationQuestions.cases("es").stream().map(q->DynamicTest.dynamicTest("Phase 1 / "+q.caseId()+" / "+q.name(),()->{
            var entry=records.stream().filter(row -> q.caseId().equals(row.get("caseId"))).findFirst().orElseThrow();recording.reset();var progress=RagProgress.silent();
            var router=context.getBean(GenerationRouter.class);router.clearRequestExecution();
            try {
                // Original question is passed verbatim. Only the existing RAG's own normalization applies.
                var response=context.getBean(CitedRagService.class).answer(q.question(),null,"es",progress);
                entry.put("response",response);entry.put("status","CAPTURED");
                if(progress.usedProvider()!=null)entry.put("generationProvider",Map.of("name",progress.usedProvider().name(),"model",progress.usedProvider().model()));
            } catch(RuntimeException failure) { entry.put("status","ERROR");entry.put("errorType",failure.getClass().getSimpleName()); }
            finally {
                entry.put("retrievals",List.copyOf(recording.searches));entry.put("metrics",progress.metrics());
                entry.put("recordedAt",Instant.now().toString());router.clearRequestExecution();save();
            }
            if("ERROR".equals(entry.get("status")))throw new AssertionError("Phase 1 transport/execution failure; see recorded errorType");
            // No factuality, correctness or abstention assertions: those belong to phase 2.
        }));
    }
    @AfterAll void close() throws Exception {
        try { if(context!=null)context.close(); }
        finally { report.put("finishedAt",Instant.now().toString());save(); }
    }
    void save() throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        var temporary=Files.createTempFile(output.toAbsolutePath().getParent(),".phase1-",".json");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),report);
            try { Files.move(temporary,output,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored) { Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
