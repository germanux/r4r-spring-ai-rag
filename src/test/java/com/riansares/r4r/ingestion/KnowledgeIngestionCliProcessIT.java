package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that executes the actual KnowledgeIngestionCli as a bounded child JVM
 * and verifies its process contract without requiring live external dependencies.
 */
class KnowledgeIngestionCliProcessIT {

    private static final String RAG_DB_PASSWORD_SENTINEL = "SECRET_CREDENTIAL_SENTINEL_2026_TEST";
    
    private Path tempCorpus;
    private Process liveProcess;

    @BeforeEach
    void setUp() throws IOException {
        tempCorpus = Files.createTempDirectory("r4r-cli-process-it-");
    }

    @AfterEach
    void tearDown() throws IOException {
        destroyProcessSafely();
        if (tempCorpus != null) {
            try {
                deleteRecursively(tempCorpus);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Bounded helper that starts concurrent stdout/stderr drainers, waits with a substantially
     * bounded timeout, destroys then forcibly destroying a live child in finally/cleanup,
     * joins both drainers before reading buffers, and records command, exit code and output.
     */
    private ProcessResult executeChildProcess(Path corpus, boolean mustFail) throws IOException {
        String classpath = System.getProperty("java.class.path");
        
        // Resolve target/test-classes and target/classes explicitly
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path testClasses = projectRoot.resolve("target").resolve("test-classes");
        Path appClasses = projectRoot.resolve("target").resolve("classes");
        
        String classpathWithClasses;
        if (testClasses.toFile().exists() && appClasses.toFile().exists()) {
            String pathSeparator = System.getProperty("path.separator");
            classpathWithClasses = testClasses.toString() + pathSeparator + 
                                   appClasses.toString() + pathSeparator + 
                                   classpath;
        } else {
            // Fallback to current classpath
            classpathWithClasses = classpath;
        }
        
        String javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        
        List<String> args = new ArrayList<>();
        args.add(javaExecutable);
        args.add("-cp");
        args.add(classpathWithClasses);
        
        // Exclude all infrastructure auto-configurations (Spring Boot 3.2+ style)
        args.add("-Dspring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration," +
            "org.springframework.ai.embedding.autoconfigure.EmbeddingStoragePostProcessorAutoConfiguration," +
            "org.springframework.ai.chat.model.autoconfigure.ChatClientAutoConfiguration," +
            "org.springframework.ai.model.autoconfigure.ModelAutoConfiguration," +
            "org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration," +
            "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration");
        
        // Disable Spring Boot web application
        args.add("-Dspring.main.web-application-type=none");
        
        // Disable catalog startup
        args.add("-Dr4r.catalog-on-startup=false");
        
        // Add explicit environment variables for RAG configuration
        args.add("-Dr4r.knowledge.root=" + corpus.toAbsolutePath().toString());
        args.add("-Dr4r.knowledge.max-file-bytes=1048576");
        args.add("-Dr4r.knowledge.max-chunk-chars=2000");
        
        // If mustFail, trigger failure scenario
        if (mustFail) {
            args.add("-Dtest.child.process.success=false");
        } else {
            args.add("-Dtest.child.process.success=true");
        }
        
        // Add the main class - KnowledgeIngestionCli.class.getName() directly
        args.add(KnowledgeIngestionCli.class.getName());
        
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false); // Keep stdout and stderr separate
        
        // Set environment variables with bounded placeholders and sentinel credential
        var env = pb.environment();
        env.put("RAG_KNOWLEDGE_PATH", corpus.toAbsolutePath().toString());
        env.put("RAG_MAX_FILE_BYTES", "1048576");
        env.put("RAG_MAX_CHUNK_CHARS", "2000");
        env.put("RAG_DB_URL", "jdbc:postgresql://localhost:5432/testdb"); // placeholder
        env.put("RAG_DB_USER", "testuser"); // placeholder
        env.put("RAG_DB_PASSWORD", RAG_DB_PASSWORD_SENTINEL); // credential sentinel
        env.put("RAG_OLLAMA_BASE_URL", "http://localhost:11434"); // placeholder
        env.put("RAG_GENERATION_MODEL", "llama2"); // placeholder
        env.put("RAG_EMBEDDING_MODEL", "nomic-embed-text"); // placeholder
        env.put("RAG_EMBEDDING_DIMENSIONS", "768"); // placeholder
        
        return startAndDrainProcess(pb, args);
    }
    
    /**
     * Starts process with concurrent drainers and cleanup.
     */
    private ProcessResult startAndDrainProcess(ProcessBuilder pb, List<String> commandArgs) throws IOException {
        StringBuilder commandStr = new StringBuilder();
        for (String arg : commandArgs) {
            if (commandStr.length() > 0) commandStr.append(" ");
            commandStr.append(arg);
        }
        
        Process process = pb.start();
        liveProcess = process;
        
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        
        Thread stdoutThread = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (stdoutBuilder) {
                        stdoutBuilder.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                // Stream closed or error
            }
        });
        
        Thread stderrThread = new Thread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (stderrBuilder) {
                        stderrBuilder.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                // Stream closed or error
            }
        });
        
        stdoutThread.start();
        stderrThread.start();
        
        boolean exited;
        try {
            exited = process.waitFor(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exited = false;
        }
        
        // Cleanup: destroy then forcibly destroy if still alive
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS) && process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        
        // Join both drainers before reading buffers
        try {
            stdoutThread.join(5000);
            stderrThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int exitCode = process.exitValue();
        
        return new ProcessResult(
            commandStr.toString(),
            exitCode,
            stdoutBuilder.toString(),
            stderrBuilder.toString()
        );
    }
    
    /**
     * Represents the result of a child process execution.
     */
    private record ProcessResult(String command, int exitCode, String stdout, String stderr) {}
    
    private void destroyProcessSafely() {
        if (liveProcess != null && liveProcess.isAlive()) {
            liveProcess.destroy();
            try {
                if (!liveProcess.waitFor(5, TimeUnit.SECONDS)) {
                    liveProcess.destroyForcibly();
                    liveProcess.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                liveProcess.destroyForcibly();
            }
        }
        liveProcess = null;
    }
    
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try {
                        deleteRecursively(p);
                    } catch (IOException ignored) { }
                });
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Verifies the command contains KnowledgeIngestionCli and excludes R4rSpringAiRagApplication.
     */
    private void assertValidMainClass(String command) {
        assertThat(command).contains(KnowledgeIngestionCli.class.getName());
        assertThat(command).doesNotContain("R4rSpringAiRagApplication");
    }

    /**
     * Verifies no Tomcat/server-start markers in output.
     */
    private void assertNoInfraStartup(String stdout, String stderr) {
        String combined = stdout + stderr;
        assertThat(combined)
            .doesNotContain("Tomcat")
            .doesNotContain("EmbeddedTomcat")
            .doesNotContain("Web application");
    }
    
    /**
     * Verifies credential sentinel is not leaked in output.
     */
    private void assertNoSecretLeakage(String stdout, String stderr) {
        assertThat(stdout).doesNotContain(RAG_DB_PASSWORD_SENTINEL);
        assertThat(stderr).doesNotContain(RAG_DB_PASSWORD_SENTINEL);
    }
    
    @Test
    void successTest_executesChildCliAndOutputsValidResultWithNoInfraStartup() throws Exception {
        // Write a simple markdown file to corpus
        Path doc = tempCorpus.resolve("guide.md");
        Files.writeString(doc, "# Guide\n\nStable content.", StandardCharsets.UTF_8);

        ProcessResult result = executeChildProcess(tempCorpus, false);
        
        assertThat(result.command()).satisfies(this::assertValidMainClass);
        assertNoInfraStartup(result.stdout(), result.stderr());
        assertNoSecretLeakage(result.stdout(), result.stderr());

        assertThat(result.exitCode())
            .as(() -> String.format(
                "Exit code should be 0 on success.\nCommand: %s\nStdout:\n%s\nStderr:\n%s",
                result.command(), result.stdout(), result.stderr()))
            .isEqualTo(0);

        // Verify exactly one R4R_INGESTION_RESULT line
        Pattern resultPattern = Pattern.compile("^R4R_INGESTION_RESULT=(.+)$", Pattern.MULTILINE);
        Matcher matcher = resultPattern.matcher(result.stdout());

        int matchCount = 0;
        String lastResultJson = "";
        while (matcher.find()) {
            matchCount++;
            lastResultJson = matcher.group(1).trim();
        }

        assertThat(matchCount)
            .as("Exactly one R4R_INGESTION_RESULT line should be present")
            .isEqualTo(1);

        // Parse and verify JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(lastResultJson);

        assertThat(node.get("path")).isNotNull();
        String resultPath = node.get("path").asText();
        assertThat(resultPath).isEqualTo(tempCorpus.toAbsolutePath().normalize().toString());

        assertThat(node.get("success").asBoolean())
            .as("success should be true")
            .isTrue();
    }

    @Test
    void deterministicallyFailedIngestion_returnsNonZeroExitCodeAndNoSecretLeak() throws Exception {
        // Use failure configuration - no corpus needed for this test
        ProcessResult result = executeChildProcess(tempCorpus, true);
        
        assertThat(result.command()).satisfies(this::assertValidMainClass);
        assertNoInfraStartup(result.stdout(), result.stderr());
        assertNoSecretLeakage(result.stdout(), result.stderr());

        // Non-zero exit code for failure - should be classified as 4 (non-infrastructure)
        int exitCode = result.exitCode();
        assertThat(exitCode).as(() -> String.format(
            "Exit code should be 4 on ingestion failure (non-infrastructure).\nCommand: %s\nStdout:\n%s\nStderr:\n%s",
            result.command(), result.stdout(), result.stderr()))
            .isEqualTo(4);

        String stdout = result.stdout();
        String stderr = result.stderr();

        // Output should contain error markers but no secrets
        String combinedOutput = stdout + stderr;
        
        // Verify the process failed with proper error output
        assertThat(combinedOutput)
            .contains("ERROR");

        // On hard failure, R4R_INGESTION_RESULT may not be present (this is expected behavior)
        Pattern resultPattern = Pattern.compile("^R4R_INGESTION_RESULT=(.+)$", Pattern.MULTILINE);
        Matcher matcher = resultPattern.matcher(stdout);

        if (matcher.find()) {
            String resultJson = matcher.group(1).trim();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(resultJson);
            // On failure, success should be false
            assertThat(node.get("success").asBoolean()).isFalse();
        }
    }

    @Test
    void processCleanup_destroysLiveProcess() throws Exception {
        // Use a proper Java class with a valid main method that stays alive
        ProcessBuilder pb = new ProcessBuilder(
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
            "-cp", 
            System.getProperty("java.class.path"),
            TestHelperProcess.class.getName()
        );
        
        // Start a long-lived process
        Process dummyProcess = pb.start();
        liveProcess = dummyProcess;

        // Verify process is alive
        assertThat(dummyProcess.isAlive()).isTrue();

        destroyProcessSafely();

        // Verify process was destroyed
        assertThat(dummyProcess.isAlive()).as("Process should be destroyed after cleanup").isFalse();
    }
}