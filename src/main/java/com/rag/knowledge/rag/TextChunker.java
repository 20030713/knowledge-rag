package com.rag.knowledge.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 420;
    public static final int DEFAULT_OVERLAP_SIZE = 60;
    public static final int DEFAULT_MIN_BREAK_SIZE = 180;

    public List<String> split(String text) {
        return split(text, new SplitOptions(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, DEFAULT_MIN_BREAK_SIZE));
    }

    public List<String> split(String text, SplitOptions options) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : splitToUnits(cleaned, options.chunkSize())) {
            appendUnit(chunks, current, unit, options);
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

    private List<String> splitToUnits(String cleaned, int chunkSize) {
        List<String> units = new ArrayList<>();
        for (String paragraph : cleaned.split("\\n\\s*\\n")) {
            String compact = paragraph.trim();
            if (compact.isBlank()) {
                continue;
            }
            if (compact.length() <= chunkSize) {
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

    private void appendUnit(List<String> chunks, StringBuilder current, String unit, SplitOptions options) {
        if (unit.length() > options.chunkSize()) {
            appendLongUnit(chunks, current, unit, options);
            return;
        }

        if (current.isEmpty()) {
            current.append(unit);
            return;
        }

        String separator = current.indexOf("\n") >= 0 ? "\n" : "\n\n";
        if (current.length() + separator.length() + unit.length() <= options.chunkSize()) {
            current.append(separator).append(unit);
            return;
        }

        String previous = flush(chunks, current);
        String overlap = overlapTail(previous, options.overlapSize());
        if (!overlap.isBlank() && overlap.length() + unit.length() + 1 <= options.chunkSize()) {
            current.append(overlap).append("\n").append(unit);
        } else {
            current.append(unit);
        }
    }

    private void appendLongUnit(List<String> chunks, StringBuilder current, String unit, SplitOptions options) {
        flush(chunks, current);
        String remaining = unit.trim();
        while (remaining.length() > options.chunkSize()) {
            int end = findBreakIndex(remaining, options.chunkSize(), options.minBreakSize());
            String piece = remaining.substring(0, end).trim();
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            remaining = (overlapTail(piece, options.overlapSize()) + remaining.substring(end)).trim();
        }
        if (!remaining.isBlank()) {
            current.append(remaining);
        }
    }

    private int findBreakIndex(String text, int chunkSize, int minBreakSize) {
        int limit = Math.min(chunkSize, text.length());
        for (int index = limit; index >= minBreakSize; index--) {
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

    private String overlapTail(String text, int overlapSize) {
        if (overlapSize == 0) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= overlapSize) {
            return compact;
        }
        return compact.substring(compact.length() - overlapSize);
    }

    public record SplitOptions(int chunkSize, int overlapSize, int minBreakSize) {
        public SplitOptions {
            if (chunkSize < 1 || overlapSize < 0 || overlapSize >= chunkSize
                    || minBreakSize < 1 || minBreakSize > chunkSize) {
                throw new IllegalArgumentException("Invalid text chunking options");
            }
        }
    }
}
