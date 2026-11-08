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
        providers(env, values, "RAG_GENERATION_PROVIDERS", "generation", true);
        providers(env, values, "RAG_EMBEDDING_PROVIDERS", "embedding", false);
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
    private static void providers(ConfigurableEnvironment env, Map<String,Object> values,
            String key, String type, boolean generation) {
        String[] entries=list(env,key);
        if (entries==null) return;
        for (int i=0;i<entries.length;i++) {
            String[] parts=entries[i].split("\\|",-1);
            if (parts.length!=(generation ? 3 : 2) || generation && parts[1].isBlank())
                throw new IllegalArgumentException(generation ? "Provider requires URL|model|timeout" : "Provider requires URL|timeout");
            String url=endpoint(parts[0]).toString();
            java.time.Duration timeout;
            try { timeout=org.springframework.boot.convert.DurationStyle.detectAndParse(parts[parts.length-1].trim()); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid provider timeout"); }
            if (timeout.toMillis()<2) throw new IllegalArgumentException("Positive provider timeout required");
            String prefix="r4r.rag."+type+".providers["+i+"].";
            // Stable endpoint identity, without exposing an address or inventing a machine role.
            String id="ollama-"+java.util.UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            values.put(prefix+"name",id);
            values.put(prefix+"base-url",url);
            values.put(prefix+"read-timeout",timeout);
            if (generation) values.put(prefix+"model",parts[1].trim());
        }
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
