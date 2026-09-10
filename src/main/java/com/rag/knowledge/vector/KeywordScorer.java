package com.rag.knowledge.vector;

import com.rag.knowledge.domain.entity.DocumentChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** BM25 lexical scoring shared by the local and pgvector retrieval paths. */
@Component
public class KeywordScorer {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,.;:!?，。！？；：、（）()【】\\[\\]{}\"'`~|/\\\\<>]+");
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public Map<Long, Double> score(List<DocumentChunk> chunks, String query) {
        List<String> queryTerms = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        if (chunks.isEmpty() || queryTerms.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> tokensByChunk = new LinkedHashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        double totalLength = 0;
        for (DocumentChunk chunk : chunks) {
            List<String> tokens = tokenize(chunk.getContent());
            tokensByChunk.put(chunk.getId(), tokens);
            totalLength += tokens.size();
            Set<String> unique = new LinkedHashSet<>(tokens);
            for (String term : queryTerms) {
                if (unique.contains(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }

        double averageLength = Math.max(1.0, totalLength / chunks.size());
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            List<String> tokens = tokensByChunk.get(chunk.getId());
            Map<String, Integer> frequencies = new HashMap<>();
            tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
            double score = 0;
            for (String term : queryTerms) {
                int tf = frequencies.getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1.0 + (chunks.size() - df + 0.5) / (df + 0.5));
                double denominator = tf + K1 * (1.0 - B + B * tokens.size() / averageLength);
                score += idf * (tf * (K1 + 1.0)) / denominator;
            }
            if (score > 0) {
                scores.put(chunk.getId(), score);
            }
        }
        return scores;
    }

    public List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = new ArrayList<>();
        for (String token : SPLIT_PATTERN.split(normalized)) {
            if (token.length() >= 2 && !containsCjk(token)) {
                tokens.add(token);
            }
        }
        String cjkOnly = normalized.chars()
                .filter(this::isCjk)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        for (int index = 0; index < cjkOnly.length() - 1; index++) {
            tokens.add(cjkOnly.substring(index, index + 2));
        }
        if (tokens.isEmpty() && !normalized.isBlank()) {
            tokens.add(normalized);
        }
        return tokens;
    }

    private boolean containsCjk(String value) {
        return value.codePoints().anyMatch(this::isCjk);
    }

    private boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }
}
