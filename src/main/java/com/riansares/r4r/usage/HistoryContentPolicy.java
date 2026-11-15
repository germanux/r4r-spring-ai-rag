package com.riansares.r4r.usage;

import java.util.regex.Pattern;

/** Conservative exclusion of obvious personal/sensitive material, not a guarantee of anonymisation. */
final class HistoryContentPolicy {
    private static final Pattern IDENTIFIERS = Pattern.compile(
            "(?i)[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,253}\\.[a-z]{2,}|\\b\\d{8}[a-z]\\b|\\b[a-z]{2}\\d{2}(?:[ \\t]?[a-z0-9]){11,30}\\b|(?<!\\d)(?:\\+\\d{1,3}[ .-]?)?(?:\\d[ .-]?){9,15}(?!\\d)");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?iu)\\b(mi diagnóstico|mi enfermedad|mi tratamiento médico|my diagnosis|my medical condition|my medical treatment|mi religión|my religion|mi orientación sexual|my sexual orientation|mi afiliación política|my political affiliation|VIH|HIV)\\b");
    static String exclusion(String... texts) {
        for (String text : texts) {
            if (text == null) continue;
            if (SENSITIVE.matcher(text).find()) return "sensitive_content";
            if (IDENTIFIERS.matcher(text).find()) return "personal_identifier";
        }
        return null;
    }
    private HistoryContentPolicy() {}
}
