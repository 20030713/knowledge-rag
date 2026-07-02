package com.rag.knowledge.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private static final int CHUNK_SIZE = 420;
    private static final int OVERLAP_SIZE = 60;
    private static final int MIN_BREAK_SIZE = 180;

    public List<String> split(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : splitToUnits(cleaned)) {
            appendUnit(chunks, current, unit);
        }
        flush(chunks, current);
        return chunks;
    }

    private String clean(String text) {
        String normalized = text
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B-\\u200D\\u2060]", "");

        List<String> lines = new ArrayList<>();
        boolean previousBlank = false;
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.replaceAll("[ \\t]+", " ").trim();
            if (line.isBlank()) {
                if (!previousBlank && !lines.isEmpty()) {
                    lines.add("");
                }
                previousBlank = true;
                continue;
            }
            lines.add(line);
            previousBlank = false;
        }
        return String.join("\n", lines)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<String> splitToUnits(String cleaned) {
        List<String> units = new ArrayList<>();
        for (String paragraph : cleaned.split("\\n\\s*\\n")) {
            String compact = paragraph.trim();
            if (compact.isBlank()) {
                continue;
            }
            if (compact.length() <= CHUNK_SIZE) {
                units.add(compact);
                continue;
            }
            for (String sentence : compact.split("(?<=[。！？!?；;\\.])\\s*")) {
                String unit = sentence.trim();
                if (!unit.isBlank()) {
                    units.add(unit);
                }
            }
        }
        return units;
    }

    private void appendUnit(List<String> chunks, StringBuilder current, String unit) {
        if (unit.length() > CHUNK_SIZE) {
            appendLongUnit(chunks, current, unit);
            return;
        }

        if (current.isEmpty()) {
            current.append(unit);
            return;
        }

        String separator = current.indexOf("\n") >= 0 ? "\n" : "\n\n";
        if (current.length() + separator.length() + unit.length() <= CHUNK_SIZE) {
            current.append(separator).append(unit);
            return;
        }

        String previous = flush(chunks, current);
        String overlap = overlapTail(previous);
        if (!overlap.isBlank() && overlap.length() + unit.length() + 1 <= CHUNK_SIZE) {
            current.append(overlap).append("\n").append(unit);
        } else {
            current.append(unit);
        }
    }

    private void appendLongUnit(List<String> chunks, StringBuilder current, String unit) {
        flush(chunks, current);
        String remaining = unit.trim();
        while (remaining.length() > CHUNK_SIZE) {
            int end = findBreakIndex(remaining);
            String piece = remaining.substring(0, end).trim();
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            remaining = (overlapTail(piece) + remaining.substring(end)).trim();
        }
        if (!remaining.isBlank()) {
            current.append(remaining);
        }
    }

    private int findBreakIndex(String text) {
        int limit = Math.min(CHUNK_SIZE, text.length());
        for (int index = limit; index >= MIN_BREAK_SIZE; index--) {
            char character = text.charAt(index - 1);
            if (character == '。' || character == '！' || character == '？'
                    || character == ';' || character == '；'
                    || character == '.' || Character.isWhitespace(character)) {
                return index;
            }
        }
        return limit;
    }

    private String flush(List<String> chunks, StringBuilder current) {
        String value = current.toString().trim();
        current.setLength(0);
        if (!value.isBlank()) {
            chunks.add(value);
        }
        return value;
    }

    private String overlapTail(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= OVERLAP_SIZE) {
            return compact;
        }
        return compact.substring(compact.length() - OVERLAP_SIZE);
    }
}
