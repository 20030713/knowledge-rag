package com.rag.knowledge.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 420;
    public static final int DEFAULT_OVERLAP_SIZE = 60;
    public static final int DEFAULT_MIN_BREAK_SIZE = 180;
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:page\\s*)?\\d+(?:\\s*/\\s*\\d+)?$|^第\\s*\\d+\\s*页(?:\\s*共\\s*\\d+\\s*页)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+");

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
        return List.copyOf(chunks);
    }

    String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B-\\u200D\\u2060]", "")
                .replaceAll("(?<=\\p{L})-\\s*\\n\\s*(?=\\p{Ll})", "");

        Set<String> repeatedMargins = repeatedPageMargins(normalized);

        List<String> lines = new ArrayList<>();
        boolean previousBlank = false;
        for (String rawLine : normalized.replace('\f', '\n').split("\n")) {
            String line = rawLine.replaceAll("[ \\t]+", " ").trim();
            if (PAGE_NUMBER.matcher(line).matches() || repeatedMargins.contains(normalizeLine(line))) {
                continue;
            }
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

    private Set<String> repeatedPageMargins(String text) {
        String[] pages = text.split("\\f");
        if (pages.length < 2) {
            return Set.of();
        }
        Map<String, Integer> pageCounts = new LinkedHashMap<>();
        for (String page : pages) {
            List<String> lines = page.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
            Set<String> pageMargins = new LinkedHashSet<>();
            for (int index = 0; index < Math.min(3, lines.size()); index++) {
                addMarginCandidate(pageMargins, lines.get(index));
            }
            for (int index = Math.max(0, lines.size() - 3); index < lines.size(); index++) {
                addMarginCandidate(pageMargins, lines.get(index));
            }
            pageMargins.forEach(line -> pageCounts.merge(line, 1, Integer::sum));
        }
        int threshold = Math.max(2, (int) Math.ceil(pages.length * 0.5));
        return pageCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void addMarginCandidate(Set<String> candidates, String line) {
        String normalized = normalizeLine(line);
        if (!normalized.isBlank() && normalized.length() <= 120 && !MARKDOWN_HEADING.matcher(line).matches()) {
            candidates.add(normalized);
        }
    }

    private String normalizeLine(String line) {
        return line == null ? "" : line.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<String> splitToUnits(String cleaned, int chunkSize) {
        List<String> units = new ArrayList<>();
        String activeHeading = "";
        for (String paragraph : cleaned.split("\\n\\s*\\n")) {
            String compact = paragraph.trim();
            if (compact.isBlank()) {
                continue;
            }
            if (MARKDOWN_HEADING.matcher(compact).matches() && !compact.contains("\n")) {
                activeHeading = compact;
                continue;
            }
            String contextual = activeHeading.isBlank() || compact.startsWith(activeHeading)
                    ? compact
                    : activeHeading + "\n" + compact;
            if (contextual.length() <= chunkSize) {
                units.add(contextual);
                continue;
            }
            for (String sentence : contextual.split("(?<=[。！？!?；;\\.])\\s*")) {
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

        String appendableUnit = withoutRepeatedHeading(current, unit);
        if (appendableUnit.isBlank()) {
            return;
        }
        String separator = current.indexOf("\n") >= 0 ? "\n" : "\n\n";
        if (current.length() + separator.length() + appendableUnit.length() <= options.chunkSize()) {
            current.append(separator).append(appendableUnit);
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

    private String withoutRepeatedHeading(StringBuilder current, String unit) {
        if (current.isEmpty()) {
            return unit;
        }
        int firstLineEnd = current.indexOf("\n");
        String firstLine = firstLineEnd < 0 ? current.toString() : current.substring(0, firstLineEnd);
        if (MARKDOWN_HEADING.matcher(firstLine).matches() && unit.startsWith(firstLine + "\n")) {
            return unit.substring(firstLine.length() + 1).trim();
        }
        return unit;
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
        int start = compact.length() - overlapSize;
        int boundary = compact.indexOf(' ', start);
        if (boundary >= 0 && boundary - start <= Math.max(16, overlapSize / 3)) {
            start = boundary + 1;
        }
        return compact.substring(start);
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
