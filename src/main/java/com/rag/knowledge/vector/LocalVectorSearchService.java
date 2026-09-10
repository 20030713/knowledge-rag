package com.rag.knowledge.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.repository.DocumentChunkMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalVectorSearchService implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorSearchService.class);
    private final DocumentChunkMapper documentChunkMapper;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final KeywordScorer keywordScorer;

    public LocalVectorSearchService(
            DocumentChunkMapper documentChunkMapper,
            TextEmbeddingService textEmbeddingService,
            VectorSearchProperties properties
    ) {
        this(documentChunkMapper, textEmbeddingService, properties, new ObjectMapper(), new KeywordScorer());
    }

    @Autowired
    public LocalVectorSearchService(
            DocumentChunkMapper documentChunkMapper,
            TextEmbeddingService textEmbeddingService,
            VectorSearchProperties properties,
            ObjectMapper objectMapper,
            KeywordScorer keywordScorer
    ) {
        this.documentChunkMapper = documentChunkMapper;
        this.textEmbeddingService = textEmbeddingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.keywordScorer = keywordScorer;
    }

    public List<VectorSearchResult> search(Long userId, Long kbId, String question, int topK) {
        return search(
                userId,
                kbId,
                question,
                topK,
                properties.getVectorWeight(),
                properties.getKeywordWeight()
        );
    }

    @Override
    public List<VectorSearchResult> search(
            Long userId,
            Long kbId,
            String question,
            int topK,
            double vectorWeight,
            double keywordWeight
    ) {
        List<DocumentChunk> chunks = documentChunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getUserId, userId)
                .eq(DocumentChunk::getKbId, kbId)
                .orderByDesc(DocumentChunk::getCreatedAt)
                .last("LIMIT " + properties.safeMaxChunksToScan()));
        if (chunks.isEmpty()) {
            return List.of();
        }

        double[] resolvedQuestionVector;
        try {
            resolvedQuestionVector = textEmbeddingService.embed(question);
        } catch (RuntimeException exception) {
            log.warn("Question embedding unavailable; continuing with BM25-only retrieval", exception);
            resolvedQuestionVector = new double[0];
        }
        final double[] questionVector = resolvedQuestionVector;
        Map<Long, Double> keywordScores = keywordScorer.score(chunks, question);
        double maxKeywordScore = keywordScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);

        return chunks.stream()
                .map(chunk -> scoreChunk(chunk, questionVector, keywordScores, maxKeywordScore, vectorWeight, keywordWeight))
                .filter(result -> result.finalScore() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed()
                        .thenComparing(result -> result.chunk().getChunkNo()))
                .limit(topK)
                .toList();
    }

    private VectorSearchResult scoreChunk(
            DocumentChunk chunk,
            double[] questionVector,
            Map<Long, Double> keywordScores,
            double maxKeywordScore,
            double vectorWeight,
            double keywordWeight
    ) {
        double vectorScore = questionVector.length == 0 ? 0 : cosine(questionVector, readStoredEmbedding(chunk));
        double rawKeywordScore = keywordScores.getOrDefault(chunk.getId(), 0.0);
        double normalizedKeywordScore = maxKeywordScore <= 0 ? 0 : rawKeywordScore / maxKeywordScore;
        double totalWeight = vectorWeight + keywordWeight;
        double normalizedVectorWeight = totalWeight <= 0 ? properties.normalizedVectorWeight() : vectorWeight / totalWeight;
        double normalizedKeywordWeight = totalWeight <= 0 ? properties.normalizedKeywordWeight() : keywordWeight / totalWeight;
        double finalScore = vectorScore * normalizedVectorWeight + normalizedKeywordScore * normalizedKeywordWeight;
        return new VectorSearchResult(chunk, vectorScore, rawKeywordScore, finalScore);
    }

    private double[] readStoredEmbedding(DocumentChunk chunk) {
        String embeddingJson = chunk.getEmbeddingJson();
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return textEmbeddingService.embed(chunk.getContent());
        }
        try {
            double[] vector = objectMapper.readValue(embeddingJson, double[].class);
            if (vector.length != textEmbeddingService.dimensions()) {
                return textEmbeddingService.embed(chunk.getContent());
            }
            return vector;
        } catch (Exception exception) {
            log.debug("Invalid stored embedding for chunk id={}", chunk.getId(), exception);
            return textEmbeddingService.embed(chunk.getContent());
        }
    }

    private double cosine(double[] left, double[] right) {
        double score = 0;
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            score += left[index] * right[index];
        }
        return score;
    }

}
