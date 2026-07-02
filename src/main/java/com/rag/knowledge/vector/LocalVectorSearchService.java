package com.rag.knowledge.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.repository.DocumentChunkMapper;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalVectorSearchService implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorSearchService.class);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s,.;:!?，。！？；：、（）()【】\\[\\]{}\"'`~|/\\\\<>]+");

    private final DocumentChunkMapper documentChunkMapper;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorSearchProperties properties;
    private final ObjectMapper objectMapper;

    public LocalVectorSearchService(
            DocumentChunkMapper documentChunkMapper,
            TextEmbeddingService textEmbeddingService,
            VectorSearchProperties properties
    ) {
        this(documentChunkMapper, textEmbeddingService, properties, new ObjectMapper());
    }

    @Autowired
    public LocalVectorSearchService(
            DocumentChunkMapper documentChunkMapper,
            TextEmbeddingService textEmbeddingService,
            VectorSearchProperties properties,
            ObjectMapper objectMapper
    ) {
        this.documentChunkMapper = documentChunkMapper;
        this.textEmbeddingService = textEmbeddingService;
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        double[] questionVector = textEmbeddingService.embed(question);
        Set<String> terms = tokenize(question);
        double maxKeywordScore = chunks.stream()
                .mapToDouble(chunk -> keywordScore(chunk.getContent(), terms))
                .max()
                .orElse(0);

        return chunks.stream()
                .map(chunk -> scoreChunk(chunk, questionVector, terms, maxKeywordScore, vectorWeight, keywordWeight))
                .filter(result -> result.finalScore() > 0)
                .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed()
                        .thenComparing(result -> result.chunk().getChunkNo()))
                .limit(topK)
                .toList();
    }

    private VectorSearchResult scoreChunk(
            DocumentChunk chunk,
            double[] questionVector,
            Set<String> terms,
            double maxKeywordScore,
            double vectorWeight,
            double keywordWeight
    ) {
        double vectorScore = cosine(questionVector, readStoredEmbedding(chunk));
        double rawKeywordScore = keywordScore(chunk.getContent(), terms);
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
}
