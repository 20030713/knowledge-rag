package com.rag.knowledge.vector;

import com.rag.knowledge.config.VectorSearchProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class HashingTextEmbeddingService implements TextEmbeddingService {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,，。?!？！：；、（）()《》<>\\[\\]{}\"'`~|/\\\\]+");

    private final VectorSearchProperties properties;

    public HashingTextEmbeddingService(VectorSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[dimensions()];
        for (String token : tokenize(text)) {
            int index = positiveHash(token) % dimensions();
            vector[index] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return properties.safeDimensions();
    }

    @Override
    public String modelName() {
        return "local-hash-v1";
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        List<String> tokens = new ArrayList<>();
        for (String token : SPLIT_PATTERN.split(normalized)) {
            if (token.length() >= 2) {
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

    private boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }

    private int positiveHash(String token) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte item : bytes) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash & 0x7fffffff;
    }

    private void normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
    }
}
