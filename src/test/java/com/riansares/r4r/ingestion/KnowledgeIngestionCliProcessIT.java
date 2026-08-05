package com.riansares.r4r.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that executes the actual KnowledgeIngestionCli as a bounded child JVM
 * and verifies its process contract without requiring live external dependencies.
 */
class KnowledgeIngestionCliProcessIT {

    private static final String RAG_DB_PASSWORD_SENTINEL = "SECRET_CREDENTIAL_SENTINEL_2026_TEST";
    private static final String AUTO_CONFIGURATION_EXCLUSIONS = String.join(",",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration",
            "org.springframework.ai.embedding.autoconfigure.EmbeddingStoragePostProcessorAutoConfiguration",
            "org.springframework.ai.chat.model.autoconfigure.ChatClientAutoConfiguration",
            "org.springframework.ai.model.autoconfigure.ModelAutoConfiguration",
            "org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration");
    private static final Pattern RESULT_PATTERN = Pattern.compile(
            "^R4R_INGESTION_RESULT=(.+)$",
            Pattern.MULTILINE);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long PROCESS_TIMEOUT_SECONDS = 120;
    private static final long GRACEFUL_SHUTDOWN_SECONDS = 5;
    private static final long FORCED_SHUTDOWN_SECONDS = 10;
    private static final long OUTPUT_DRAIN_TIMEOUT_SECONDS = 5;

    @TempDir
    private Path tempCorpus;
    private Process liveProcess;

    @AfterEach
    void tearDown() {
        destroyProcessSafely();
    }

    /**
     * Starts the child JVM with concurrent stdout/stderr drainers and a strict timeout.
     */
    private ProcessResult executeChildProcess(Path corpus, boolean mustFail) throws IOException {
        String classpath = System.getProperty("java.class.path");

        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path testClasses = projectRoot.resolve("target").resolve("test-classes");
        Path appClasses = projectRoot.resolve("target").resolve("classes");

        String classpathWithClasses;
        if (Files.isDirectory(testClasses) && Files.isDirectory(appClasses)) {
            String pathSeparator = System.getProperty("path.separator");
            classpathWithClasses = String.join(
                    pathSeparator,
                    testClasses.toString(),
                    appClasses.toString(),
                    classpath);
        } else {
            classpathWithClasses = classpath;
        }

        List<String> args = new ArrayList<>();
        args.add(javaExecutable());
        args.add("-cp");
        args.add(classpathWithClasses);
        args.add("-Dspring.autoconfigure.exclude=" + AUTO_CONFIGURATION_EXCLUSIONS);
        args.add("-Dspring.main.web-application-type=none");
        args.add("-Dr4r.catalog-on-startup=false");
        args.add("-Dr4r.knowledge.root=" + corpus.toAbsolutePath());
        args.add("-Dr4r.knowledge.max-file-bytes=1048576");
        args.add("-Dr4r.knowledge.max-chunk-chars=2000");
        args.add("-Dtest.child.process.success=" + !mustFail);
        args.add(KnowledgeIngestionCli.class.getName());

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        var env = processBuilder.environment();
        env.put("RAG_KNOWLEDGE_PATH", corpus.toAbsolutePath().toString());
        env.put("RAG_MAX_FILE_BYTES", "1048576");
        env.put("RAG_MAX_CHUNK_CHARS", "2000");
        env.put("RAG_DB_URL", "jdbc:postgresql://localhost:5432/testdb");
        env.put("RAG_DB_USER", "testuser");
        env.put("RAG_DB_PASSWORD", RAG_DB_PASSWORD_SENTINEL);
        env.put("RAG_OLLAMA_BASE_URL", "http://localhost:11434");
        env.put("RAG_GENERATION_MODEL", "llama2");
        env.put("RAG_EMBEDDING_MODEL", "nomic-embed-text");
        env.put("RAG_EMBEDDING_DIMENSIONS", "768");

        return startAndDrainProcess(processBuilder, args);
    }

    private String javaExecutable() {
        String executableName = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .startsWith("windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName).toString();
    }

    private ProcessResult startAndDrainProcess(
            ProcessBuilder processBuilder,
            List<String> commandArgs) throws IOException {
        Process process = processBuilder.start();
        liveProcess = process;

        ExecutorService drainers = Executors.newFixedThreadPool(2);
        Future<String> stdout = drainers.submit(
                () -> readFully(process.getInputStream()));
        Future<String> stderr = drainers.submit(
                () -> readFully(process.getErrorStream()));

        try {
            boolean completed = waitForProcess(process);
            if (!completed) {
                terminateProcess(process);
                throw new IOException(
                        "Child JVM exceeded the " + PROCESS_TIMEOUT_SECONDS + " second timeout");
            }

            return new ProcessResult(
                    List.copyOf(commandArgs),
                    process.exitValue(),
                    awaitOutput(stdout, "stdout"),
                    awaitOutput(stderr, "stderr"));
        } finally {
            if (process.isAlive()) {
                terminateProcess(process);
            }
            liveProcess = null;
            drainers.shutdownNow();
        }
    }

    private boolean waitForProcess(Process process) throws IOException {
        try {
            return process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the child JVM", exception);
        }
    }

    private String readFully(InputStream stream) throws IOException {
        try (var reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
            return output.toString();
        }
    }

    private String awaitOutput(Future<String> output, String streamName) throws IOException {
        try {
            return output.get(OUTPUT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while collecting child-process " + streamName,
                    exception);
        } catch (ExecutionException exception) {
            throw new IOException(
                    "Failed to collect child-process " + streamName,
                    exception.getCause());
        } catch (TimeoutException exception) {
            output.cancel(true);
            throw new IOException(
                    "Timed out while collecting child-process " + streamName,
                    exception);
        }
    }

    private void destroyProcessSafely() {
        if (liveProcess != null) {
            terminateProcess(liveProcess);
        }
        liveProcess = null;
    }

    private void terminateProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }

        process.destroy();
        try {
            if (!process.waitFor(GRACEFUL_SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(FORCED_SHUTDOWN_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private record ProcessResult(
            List<String> command,
            int exitCode,
            String stdout,
            String stderr) {
    }

    /**
     * Verifies the command contains KnowledgeIngestionCli and excludes R4rSpringAiRagApplication.
     */
    private void assertValidMainClass(List<String> command) {
        assertThat(command)
                .contains(KnowledgeIngestionCli.class.getName())
                .doesNotContain("com.riansares.r4r.R4rSpringAiRagApplication");
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
        Path doc = tempCorpus.resolve("guide.md");
        Files.writeString(doc, "# Guide\n\nStable content.", StandardCharsets.UTF_8);

        ProcessResult result = executeChildProcess(tempCorpus, false);

        assertValidMainClass(result.command());
        assertNoInfraStartup(result.stdout(), result.stderr());
        assertNoSecretLeakage(result.stdout(), result.stderr());

        assertThat(result.exitCode())
                .as(() -> String.format(
                        "Exit code should be 0 on success.%nCommand: %s%nStdout:%n%s%nStderr:%n%s",
                        result.command(),
                        result.stdout(),
                        result.stderr()))
                .isEqualTo(0);

        Matcher matcher = RESULT_PATTERN.matcher(result.stdout());

        int matchCount = 0;
        String lastResultJson = "";
        while (matcher.find()) {
            matchCount++;
            lastResultJson = matcher.group(1).trim();
        }

        assertThat(matchCount)
                .as("Exactly one R4R_INGESTION_RESULT line should be present")
                .isEqualTo(1);

        var node = OBJECT_MAPPER.readTree(lastResultJson);

        assertThat(node.path("path").asText())
                .isEqualTo(tempCorpus.toAbsolutePath().normalize().toString());

        assertThat(node.path("success").asBoolean())
                .as("success should be true")
                .isTrue();
    }

    @Test
    void deterministicallyFailedIngestion_returnsNonZeroExitCodeAndNoSecretLeak() throws Exception {
        ProcessResult result = executeChildProcess(tempCorpus, true);

        assertValidMainClass(result.command());
        assertNoInfraStartup(result.stdout(), result.stderr());
        assertNoSecretLeakage(result.stdout(), result.stderr());

        assertThat(result.exitCode())
                .as(() -> String.format(
                        "Exit code should be 4 on ingestion failure.%nCommand: %s%nStdout:%n%s%nStderr:%n%s",
                        result.command(),
                        result.stdout(),
                        result.stderr()))
                .isEqualTo(4);

        String stdout = result.stdout();
        String stderr = result.stderr();
        String combinedOutput = stdout + stderr;

        assertThat(combinedOutput).contains("ERROR");

        // On hard failure, R4R_INGESTION_RESULT may not be present (this is expected behavior)
        Matcher matcher = RESULT_PATTERN.matcher(stdout);

        if (matcher.find()) {
            String resultJson = matcher.group(1).trim();
            var node = OBJECT_MAPPER.readTree(resultJson);
            assertThat(node.path("success").asBoolean()).isFalse();
        }
    }

    @Test
    void processCleanup_destroysLiveProcess() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                System.getProperty("java.class.path"),
                TestHelperProcess.class.getName());

        Process dummyProcess = processBuilder.start();
        liveProcess = dummyProcess;

        assertThat(dummyProcess.isAlive()).isTrue();

        destroyProcessSafely();

        assertThat(dummyProcess.isAlive())
                .as("Process should be destroyed after cleanup")
                .isFalse();
    }
}
