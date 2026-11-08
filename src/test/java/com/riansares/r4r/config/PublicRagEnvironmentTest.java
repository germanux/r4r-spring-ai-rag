package com.riansares.r4r.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** Public/fictitious configuration only; never imports the repository private file. */
class PublicRagEnvironmentTest {
    @TempDir Path root;
    @Test void privateOverridesPublicAndListsReachExistingConsumers() throws Exception {
        Path base=root.resolve("public.properties"), overrides=root.resolve("private.properties");
        Files.copy(Path.of("docker-spring/.env.base"),base);
        Files.writeString(overrides,"RAG_EMBEDDING_PROVIDERS=\"http://127.0.0.1:9999|8s,http://127.0.0.1:9998|9s\"\n"
                +"RAG_GENERATION_PROVIDERS=http://127.0.0.1:9997|test-a|20s,http://127.0.0.1:9996|test-b|30s\n"
                +"POSTGRES_PASSWORD=fictitious\nRAG_CORS_ALLOWED_ORIGINS=http://localhost:8000\n");
        var env=new org.springframework.core.env.StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test-only",Map.of(
                "spring.config.location",base.toUri()+","+overrides.toUri())));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        PublicRagEnvironment.apply(env);
        assertThat(env.getProperty("POSTGRES_PASSWORD")).isEqualTo("fictitious");
        assertThat(env.getProperty("POSTGRES_DB")).isEqualTo("r4r_rag");
        assertThat(env.getProperty("r4r.rag.generation.providers[0].model")).isEqualTo("test-a");
        assertThat(env.getProperty("r4r.rag.generation.providers[1].model")).isEqualTo("test-b");
        assertThat(env.getProperty("r4r.rag.embedding.providers[0].base-url")).isEqualTo("http://127.0.0.1:9999");
        assertThat(env.getProperty("r4r.rag.embedding.providers[2].base-url")).isNull();
        assertThat(Binder.get(env).bind("r4r.api-security.allowed-origins", org.springframework.boot.context.properties.bind.Bindable.listOf(String.class)).get())
                .containsExactly("http://localhost:8000");
    }
    @Test void missingPrivateImportIsOptional() throws Exception {
        var env=new org.springframework.core.env.StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test-only",Map.of("spring.config.location",
                Path.of("docker-spring/.env.base").toUri()+"[.properties],optional:"+root.resolve("absent.properties").toUri())));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        PublicRagEnvironment.apply(env);
        assertThat(env.getProperty("r4r.rag.embedding.providers[0].base-url")).isEqualTo("http://192.168.1.209:11434");
        assertThat(env.getProperty("RAG_OPENAI_ENABLED")).isEqualTo("false");
        assertThat(env.getProperty("RAG_S3_VECTORS_ENABLED")).isEqualTo("false");
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    static class EmptyApplication { }
    @Test void springBootRegistersAdapterAndLoadsOnlyPublicConfig() throws Exception {
        // Config imports accumulate: never try to suppress a private import via a higher-priority value.
        Path publicApplication=root.resolve("application.yml");
        String yaml=Files.readString(Path.of("src/main/resources/application.yml"))
                .replace("      - optional:file:./docker-spring/.env[.properties]\n", "")
                .replace("optional:file:./docker-spring/.env.base[.properties]",
                        Path.of("docker-spring/.env.base").toAbsolutePath().toUri()+"[.properties]");
        Files.writeString(publicApplication,yaml);
        try (var context=new org.springframework.boot.builder.SpringApplicationBuilder(EmptyApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE).run(
                        "--spring.config.location="+publicApplication.toUri(),
                        "--spring.profiles.active=test",
                        "--spring.datasource.url=jdbc:postgresql://192.168.1.203:5433/r4r_rag_test",
                        "--spring.datasource.username=r4r_test")) {
            var env=context.getEnvironment();
            assertThat(env.getProperty("r4r.rag.generation.providers[0].model")).isEqualTo("qwen3:8b");
            assertThat(env.getProperty("r4r.rag.embedding.providers[0].name")).startsWith("ollama-");
            assertThat(env.getProperty("r4r.api-security.allowed-origins")).contains("https://athagon.tech");
            assertThat(env.getPropertySources().stream().map(org.springframework.core.env.PropertySource::getName))
                    .noneMatch(name -> name.contains("/.env[") || name.endsWith("/.env"));
        }
    }
    @Test void arbitraryListsPreserveEndpointTimeoutAndIdentityWhenReordered() {
        var env=new MockEnvironment().withProperty("RAG_GENERATION_PROVIDERS",
                "http://a:11434|a|20s,http://b:11434|b|30s,http://c:11434|c|40s,http://d:11434|d|50s")
                .withProperty("RAG_EMBEDDING_PROVIDERS","http://a:11434|8s,http://b:11434|9s,http://c:11434|10s,http://d:11434|11s");
        PublicRagEnvironment.apply(env);
        String identity=env.getProperty("r4r.rag.embedding.providers[3].name");
        assertThat(env.getProperty("r4r.rag.generation.providers[3].model")).isEqualTo("d");
        assertThat(env.getProperty("r4r.rag.embedding.providers[3].read-timeout",java.time.Duration.class))
                .isEqualTo(java.time.Duration.ofSeconds(11));
        env.withProperty("RAG_GENERATION_PROVIDERS","http://d:11434|d|50s")
                .withProperty("RAG_EMBEDDING_PROVIDERS","http://d:11434|11s");
        PublicRagEnvironment.apply(env);
        assertThat(env.getProperty("r4r.rag.embedding.providers[0].name")).isEqualTo(identity);
        assertThat(env.getProperty("r4r.rag.embedding.providers[0].read-timeout",java.time.Duration.class))
                .isEqualTo(java.time.Duration.ofSeconds(11));
        assertThat(env.getProperty("r4r.rag.generation.providers[1].model")).isNull();
        assertThat(env.getProperty("r4r.rag.embedding.providers[1].base-url")).isNull();
    }
    @Test void rejectsMalformedListsWithoutEchoingInput() {
        for (String value:new String[]{"http://host:11434|model", "http://user:secret@host:11434|x,http://host:11434|y"}) {
            assertThatThrownBy(() -> PublicRagEnvironment.apply(new MockEnvironment().withProperty("RAG_GENERATION_PROVIDERS",value)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        }
        assertThatThrownBy(() -> PublicRagEnvironment.apply(new MockEnvironment().withProperty("RAG_CORS_ALLOWED_ORIGINS","*")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
