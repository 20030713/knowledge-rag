package com.rag.knowledge.vector;

import com.rag.knowledge.config.PgVectorProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.repository.DocumentChunkMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PgVectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSearchService.class);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,.;:!?，。！？；：、（）()【】\\[\\]{}\"'`~|/\\\\<>]+");

    private final PgVectorProperties properties;
    private final TextEmbeddingService textEmbeddingService;
    private final DocumentChunkMapper documentChunkMapper;

    public PgVectorSearchService(
            PgVectorProperties properties,
            TextEmbeddingService textEmbeddingService,
            DocumentChunkMapper documentChunkMapper
    ) {
        this.properties = properties;
        this.textEmbeddingService = textEmbeddingService;
        this.documentChunkMapper = documentChunkMapper;
    }

    public boolean available() {
        if (!properties.isEnabled()) {
            return false;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            statement.execute();
            return true;
        } catch (SQLException exception) {
            log.warn("pgvector search unavailable url={}", properties.getUrl(), exception);
            return false;
        }
    }

    public List<VectorSearchResult> search(
            Long userId,
            Long kbId,
            String question,
            int topK,
            double vectorWeight,
            double keywordWeight
    ) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        long startedAt = System.nanoTime();
        long embeddingStartedAt = System.nanoTime();
        double[] questionVector = textEmbeddingService.embed(question);
        long embeddingMs = elapsedMillis(embeddingStartedAt);
        if (questionVector.length != properties.safeDimensions()) {
            log.warn("Question embedding dimension mismatch. expected={}, actual={}",
                    properties.safeDimensions(), questionVector.length);
            return List.of();
        }

        long queryStartedAt = System.nanoTime();
        Map<Long, Double> vectorScores = loadCandidates(userId, kbId, questionVector, topK);
        long vectorQueryMs = elapsedMillis(queryStartedAt);
        log.info("RAG retrieval timing kbId={}, embeddingMs={}, vectorQueryMs={}, totalMs={}",
                kbId, embeddingMs, vectorQueryMs, elapsedMillis(startedAt));
        if (vectorScores.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = documentChunkMapper.selectBatchIds(vectorScores.keySet());
        Set<String> terms = tokenize(question);
        double maxKeywordScore = chunks.stream()
                .mapToDouble(chunk -> keywordScore(chunk.getContent(), terms))
                .max()
                .orElse(0);

        return chunks.stream()
                .filter(chunk -> vectorScores.containsKey(chunk.getId()))
                .map(chunk -> scoreChunk(chunk, vectorScores.get(chunk.getId()), terms, maxKeywordScore, vectorWeight, keywordWeight))
                .filter(result -> result.finalScore() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed()
                        .thenComparing(result -> result.chunk().getChunkNo()))
                .limit(topK)
                .toList();
    }

    private Map<Long, Double> loadCandidates(Long userId, Long kbId, double[] questionVector, int topK) {
        int candidateLimit = Math.max(topK, topK * properties.safeCandidateMultiplier());
        String sql = """
                SELECT chunk_id, 1 - (embedding <=> ?::vector) AS vector_score
                FROM %s
                WHERE user_id = ? AND kb_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(properties.safeTableName());
        Map<Long, Double> scores = new LinkedHashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String vectorLiteral = PgVectorStoreService.toVectorLiteral(questionVector);
            statement.setString(1, vectorLiteral);
            statement.setLong(2, userId);
            statement.setLong(3, kbId);
            statement.setString(4, vectorLiteral);
            statement.setInt(5, candidateLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    scores.put(resultSet.getLong("chunk_id"), Math.max(0, resultSet.getDouble("vector_score")));
                }
            }
        } catch (SQLException exception) {
            log.warn("pgvector search failed", exception);
            return Map.of();
        }
        return scores;
    }

    private VectorSearchResult scoreChunk(
            DocumentChunk chunk,
            double vectorScore,
            Set<String> terms,
            double maxKeywordScore,
            double vectorWeight,
            double keywordWeight
    ) {
        double rawKeywordScore = keywordScore(chunk.getContent(), terms);
        double normalizedKeywordScore = maxKeywordScore <= 0 ? 0 : rawKeywordScore / maxKeywordScore;
        double totalWeight = vectorWeight + keywordWeight;
        double normalizedVectorWeight = totalWeight <= 0 ? 0.7 : vectorWeight / totalWeight;
        double normalizedKeywordWeight = totalWeight <= 0 ? 0.3 : keywordWeight / totalWeight;
        double finalScore = vectorScore * normalizedVectorWeight + normalizedKeywordScore * normalizedKeywordWeight;
        return new VectorSearchResult(chunk, vectorScore, rawKeywordScore, finalScore);
    }

    private Set<String> tokenize(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : SPLIT_PATTERN.split(normalized)) {
            if (term.length() >= 2) {
                terms.add(term);
            }
        }
        String cjkOnly = normalized.chars()
                .filter(this::isCjk)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        for (int index = 0; index < cjkOnly.length() - 1; index++) {
            terms.add(cjkOnly.substring(index, index + 2));
        }
        if (terms.isEmpty() && !normalized.isBlank()) {
            terms.add(normalized);
        }
        return terms;
    }

    private boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }

    private double keywordScore(String content, Set<String> terms) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            int count = countOccurrences(normalized, term);
            if (count > 0) {
                score += 1 + Math.log(count + 1);
                score += Math.min(term.length(), 12) * 0.08;
            }
        }
        return score;
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int from = 0;
        while (from < text.length()) {
            int index = text.indexOf(term, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + term.length();
        }
        return count;
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(properties.getUrl(), properties.getUsername(), properties.getPassword());
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
