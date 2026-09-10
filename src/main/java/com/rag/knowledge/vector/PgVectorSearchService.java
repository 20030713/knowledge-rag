package com.rag.knowledge.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.config.PgVectorProperties;
import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.repository.DocumentChunkMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PgVectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSearchService.class);
    private final PgVectorProperties properties;
    private final TextEmbeddingService textEmbeddingService;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorSearchProperties searchProperties;
    private final KeywordScorer keywordScorer;

    public PgVectorSearchService(
            PgVectorProperties properties,
            TextEmbeddingService textEmbeddingService,
            DocumentChunkMapper documentChunkMapper,
            VectorSearchProperties searchProperties,
            KeywordScorer keywordScorer
    ) {
        this.properties = properties;
        this.textEmbeddingService = textEmbeddingService;
        this.documentChunkMapper = documentChunkMapper;
        this.searchProperties = searchProperties;
        this.keywordScorer = keywordScorer;
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
        Map<Long, Double> vectorScores = Map.of();
        long vectorQueryMs = 0;
        try {
            double[] questionVector = textEmbeddingService.embed(question);
            if (questionVector.length == properties.safeDimensions()) {
                long queryStartedAt = System.nanoTime();
                vectorScores = loadCandidates(
                        userId,
                        kbId,
                        textEmbeddingService.modelNamespace(),
                        questionVector,
                        topK
                );
                vectorQueryMs = elapsedMillis(queryStartedAt);
            } else {
                log.warn("Question embedding dimension mismatch. expected={}, actual={}",
                        properties.safeDimensions(), questionVector.length);
            }
        } catch (RuntimeException exception) {
            log.warn("Question embedding unavailable; continuing with BM25-only retrieval", exception);
        }
        long embeddingMs = elapsedMillis(embeddingStartedAt);
        log.info("RAG retrieval timing kbId={}, embeddingMs={}, vectorQueryMs={}, totalMs={}",
                kbId, embeddingMs, vectorQueryMs, elapsedMillis(startedAt));
        List<DocumentChunk> lexicalCorpus = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getKbId, kbId)
                .orderByDesc(DocumentChunk::getCreatedAt)
                .last("LIMIT " + searchProperties.safeMaxChunksToScan()));
        Map<Long, Double> keywordScores = keywordScorer.score(lexicalCorpus, question);
        int lexicalLimit = Math.max(topK, topK * searchProperties.safeCandidateMultiplier());
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>(vectorScores.keySet());
        keywordScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(lexicalLimit)
                .map(Map.Entry::getKey)
                .forEach(candidateIds::add);
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>(documentChunkMapper.selectBatchIds(candidateIds));
        double maxKeywordScore = keywordScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<Long, Double> resolvedVectorScores = vectorScores;

        return chunks.stream()
                .map(chunk -> scoreChunk(
                        chunk,
                        resolvedVectorScores.getOrDefault(chunk.getId(), 0.0),
                        keywordScores.getOrDefault(chunk.getId(), 0.0),
                        maxKeywordScore,
                        vectorWeight,
                        keywordWeight
                ))
                .filter(result -> result.finalScore() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed()
                        .thenComparing(result -> result.chunk().getChunkNo()))
                .limit(topK)
                .toList();
    }

    private Map<Long, Double> loadCandidates(
            Long userId,
            Long kbId,
            String embeddingModel,
            double[] questionVector,
            int topK
    ) {
        int candidateLimit = Math.max(topK, topK * properties.safeCandidateMultiplier());
        String sql = """
                SELECT chunk_id, 1 - (embedding <=> ?::vector) AS vector_score
                FROM %s
                WHERE user_id = ? AND kb_id = ? AND embedding_model = ?
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
            statement.setString(4, embeddingModel);
            statement.setString(5, vectorLiteral);
            statement.setInt(6, candidateLimit);
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
            double rawKeywordScore,
            double maxKeywordScore,
            double vectorWeight,
            double keywordWeight
    ) {
        double normalizedKeywordScore = maxKeywordScore <= 0 ? 0 : rawKeywordScore / maxKeywordScore;
        double totalWeight = vectorWeight + keywordWeight;
        double normalizedVectorWeight = totalWeight <= 0 ? 0.7 : vectorWeight / totalWeight;
        double normalizedKeywordWeight = totalWeight <= 0 ? 0.3 : keywordWeight / totalWeight;
        double finalScore = vectorScore * normalizedVectorWeight + normalizedKeywordScore * normalizedKeywordWeight;
        return new VectorSearchResult(chunk, vectorScore, rawKeywordScore, finalScore);
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(properties.getUrl(), properties.getUsername(), properties.getPassword());
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
