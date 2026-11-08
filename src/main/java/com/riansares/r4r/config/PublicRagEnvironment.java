package com.riansares.r4r.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Adapts compact public lists to the existing Spring consumers, after config imports. */
public final class PublicRagEnvironment implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }
    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment);
    }
    public static void apply(ConfigurableEnvironment env) {
        Map<String,Object> values = new LinkedHashMap<>();
        String[] generation = list(env, "RAG_GENERATION_PROVIDERS");
        if (generation != null) {
            if (generation.length != 2) throw new IllegalArgumentException("Exactly two local generation providers required");
            for (int i=0; i<2; i++) {
                String[] pair=generation[i].split("\\|", -1);
                if (pair.length != 2 || pair[1].isBlank()) throw new IllegalArgumentException("Generation provider requires URL|model");
                endpoint(pair[0]);
                String prefix="r4r.rag.generation."+(i==0 ? "pc" : "fallback");
                values.put(prefix+"-base-url",pair[0].trim()); values.put(prefix+"-model",pair[1].trim());
            }
        }
        String[] embeddings = list(env, "RAG_EMBEDDING_PROVIDERS");
        if (embeddings != null) {
            if (embeddings.length < 1 || embeddings.length > 3) throw new IllegalArgumentException("One to three embedding providers required");
            String[] names={"laptop","desktop","nas"};
            String[] timeouts={"LP","PC","NAS"};
            for (int i=0; i<embeddings.length; i++) {
                endpoint(embeddings[i]);
                String prefix="r4r.rag.embedding.providers["+i+"].";
                values.put(prefix+"name",names[i]); values.put(prefix+"base-url",embeddings[i]);
                values.put(prefix+"read-timeout",env.getRequiredProperty("RAG_EMBEDDING_"+timeouts[i]+"_TIMEOUT"));
            }
        }
        String[] origins=list(env,"RAG_CORS_ALLOWED_ORIGINS");
        if (origins != null) {
            for (String origin:origins) {
                URI uri=endpoint(origin);
                if (!(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                    throw new IllegalArgumentException("CORS requires explicit HTTP origins");
            }
            values.put("r4r.api-security.allowed-origins",String.join(",",origins));
        }
        // Explicit native Spring/test properties remain higher priority than the derived adapter.
        env.getPropertySources().remove("public-rag-lists");
        env.getPropertySources().addLast(new MapPropertySource("public-rag-lists",values));
    }
    private static String[] list(ConfigurableEnvironment env, String key) {
        String text=env.getProperty(key);
        if (text==null) return null;
        text=text.trim();
        if (text.length()>1 && (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("'") && text.endsWith("'")))
            text=text.substring(1,text.length()-1);
        String[] values=text.split(",",-1);
        for (int i=0;i<values.length;i++) {
            values[i]=values[i].trim();
            if (values[i].isEmpty()) throw new IllegalArgumentException("Empty entry in "+key);
        }
        return values;
    }
    private static URI endpoint(String text) {
        try {
            URI uri=URI.create(text.trim());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Provider URLs and origins must be valid HTTP(S) addresses without credentials");
        }
    }
}
