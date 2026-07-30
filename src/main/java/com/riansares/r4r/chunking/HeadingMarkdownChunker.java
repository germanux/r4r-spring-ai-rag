package com.riansares.r4r.chunking;

import com.riansares.r4r.document.KnowledgeDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeadingMarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    private final int maxChunkChars;

    public HeadingMarkdownChunker(int maxChunkChars) {
        if (maxChunkChars < 64) {
            throw new IllegalArgumentException("maxChunkChars must be at least 64");
        }
        this.maxChunkChars = maxChunkChars;
    }

    public List<MarkdownChunk> chunk(KnowledgeDocument document) {
        List<Section> sections = splitIntoSections(document.content());
        List<MarkdownChunk> chunks = new ArrayList<>();
        int index = 0;
        for (Section section : sections) {
            for (String part : splitBounded(section.content())) {
                if (!part.isBlank()) {
                    chunks.add(new MarkdownChunk(document.source(), section.headingPath(), index++, part));
                }
            }
        }
        return List.copyOf(chunks);
    }

    private List<Section> splitIntoSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        StringBuilder content = new StringBuilder();

        for (String line : markdown.split("\\R", -1)) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                flushSection(sections, headings, content);
                int level = matcher.group(1).length();
                while (headings.size() >= level) {
                    headings.remove(headings.size() - 1);
                }
                headings.add(matcher.group(2).trim());
                content.append(line).append('\n');
            } else {
                content.append(line).append('\n');
            }
        }
        flushSection(sections, headings, content);
        return sections;
    }

    private void flushSection(List<Section> sections, List<String> headings, StringBuilder content) {
        String value = content.toString().strip();
        if (!value.isBlank()) {
            sections.add(new Section(List.copyOf(headings), value));
        }
        content.setLength(0);
    }

    private List<String> splitBounded(String text) {
        if (text.length() <= maxChunkChars) {
            return List.of(text);
        }

        List<String> parts = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > maxChunkChars) {
            int split = remaining.lastIndexOf('\n', maxChunkChars);
            if (split < maxChunkChars / 2) {
                split = remaining.lastIndexOf(' ', maxChunkChars);
            }
            if (split < maxChunkChars / 2) {
                split = maxChunkChars;
            }
            parts.add(remaining.substring(0, split).strip());
            remaining = remaining.substring(split).stripLeading();
        }
        if (!remaining.isBlank()) {
            parts.add(remaining.strip());
        }
        return parts;
    }

    private record Section(List<String> headingPath, String content) {
    }
}
