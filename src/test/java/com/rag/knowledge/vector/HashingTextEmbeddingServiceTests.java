package com.rag.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.knowledge.config.VectorSearchProperties;
import org.junit.jupiter.api.Test;

class HashingTextEmbeddingServiceTests {

    private final HashingTextEmbeddingService embeddingService = new HashingTextEmbeddingService(new VectorSearchProperties());

    @Test
    void embedShouldReturnNormalizedVector() {
        double[] vector = embeddingService.embed("Redis 缓存 Redis 限流");

        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }

        assertThat(vector).hasSize(embeddingService.dimensions());
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void embedShouldReturnZeroVectorForBlankText() {
        double[] vector = embeddingService.embed("   ");

        assertThat(vector).containsOnly(0.0);
    }
}
