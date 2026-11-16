package com.riansares.r4r.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Canonical URL|model|timeout syntax for the three independent provider circuits. */
public final class ProviderList {
    public static final String OPENAI_URL = "https://api.openai.com/v1";
    private ProviderList() { }
    public record Entry(String name, String baseUrl, String model, Duration readTimeout) {
        public boolean remote() { return "OpenAI".equals(name); }
    }
    public static List<Entry> parse(String value, boolean embedding) {
        if (value == null || value.isBlank()) return List.of();
        String text = value.strip();
        if (text.length()>1 && (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("'") && text.endsWith("'")))
            text=text.substring(1,text.length()-1);
        return java.util.Arrays.stream(text.split(",",-1)).map(raw -> {
            String[] parts=raw.split("\\|",-1);
            if (parts.length!=3 || parts[1].isBlank() || parts[1].strip().length()>128
                    || parts[1].chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Provider requires URL-or-openai|model|timeout");
            boolean remote="openai".equalsIgnoreCase(parts[0].strip());
            if (embedding && remote) throw new IllegalArgumentException("OpenAI is not an embedding provider in this corpus");
            String url=remote ? OPENAI_URL : endpoint(parts[0]);
            Duration timeout;
            try { timeout=org.springframework.boot.convert.DurationStyle.detectAndParse(parts[2].strip()); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid provider timeout"); }
            if (timeout.toMillis()<2 || timeout.compareTo(Duration.ofMinutes(3))>0)
                throw new IllegalArgumentException("Provider timeout must be between 2ms and 3m");
            String id=remote ? "OpenAI" : "ollama-"+java.util.UUID.nameUUIDFromBytes(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Entry(id,url,parts[1].strip(),timeout);
        }).toList();
    }
    private static String endpoint(String text) {
        try {
            URI uri=URI.create(text.strip());
            if (!List.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
                throw new IllegalArgumentException();
            return uri.toString().replaceAll("/$", "");
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Provider must be an HTTP origin without credentials, path, query or fragment"); }
    }
}
