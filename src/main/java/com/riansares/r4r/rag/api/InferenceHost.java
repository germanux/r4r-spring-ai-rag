package com.riansares.r4r.rag.api;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifies known local hosts without exposing their addresses. */
final class InferenceHost {
    private static final Pattern QWEN3_SIZE = Pattern.compile("qwen[\\s._-]*3(?:[\\s._:-]+[a-z]+)*[\\s._:-]+(\\d+(?:\\.\\d+)?)\\s*b", Pattern.CASE_INSENSITIVE);

    static Details identify(String baseUrl, String model) {
        String host;
        try { host = URI.create(baseUrl).getHost(); }
        catch (IllegalArgumentException | NullPointerException ex) { host = null; }
        if ("192.168.1.209".equals(host)) return laptop();
        if ("192.168.1.207".equals(host)) return desktop();
        String name = model == null ? "" : model.toLowerCase(Locale.ROOT);
        if (name.matches(".*qwen[\\s._-]*2[._-]5.*")) return laptop();
        Matcher size = QWEN3_SIZE.matcher(name);
        if (size.find() && Double.parseDouble(size.group(1)) >= 8) return desktop();
        return new Details("unknown", null, null, null, null);
    }

    private static Details laptop() { return new Details("laptop", "PC-ASUS-ZORIN", "Intel i7-7700HQ", "32 GB", "NVIDIA GTX 1050 · 4 GB"); }
    private static Details desktop() { return new Details("desktop", "PC-GIGA-ZORUX", "Intel i9-14900KF", "64 GB DDR5", "NVIDIA RTX 4060 · 8 GB"); }
    record Details(String type, String hostname, String cpu, String ram, String gpu) { }
}
