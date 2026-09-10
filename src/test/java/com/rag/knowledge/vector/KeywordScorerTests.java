package com.rag.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.knowledge.domain.entity.DocumentChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeywordScorerTests {

    private final KeywordScorer scorer = new KeywordScorer();

    @Test
    void bm25ShouldPreferChunkContainingRareExactIdentifier() {
        DocumentChunk generic = chunk(1L, "系统出现错误后需要检查日志并联系值班人员。");
        DocumentChunk exact = chunk(2L, "支付网关错误码 GX-4927 表示签名时间戳超过允许偏差。");
        DocumentChunk distractor = chunk(3L, "支付网关支持签名校验和请求重试。");

        Map<Long, Double> scores = scorer.score(List.of(generic, exact, distractor), "GX-4927 是什么错误？");

        assertThat(scores.get(2L)).isGreaterThan(scores.getOrDefault(3L, 0.0));
        assertThat(scores.get(2L)).isGreaterThan(0);
    }

    @Test
    void bm25ShouldReturnNoScoresForUnrelatedQuery() {
        DocumentChunk chunk = chunk(1L, "Redis 缓存采用随机过期时间。");

        assertThat(scorer.score(List.of(chunk), "员工年假结转政策")).isEmpty();
    }

    private DocumentChunk chunk(Long id, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        return chunk;
    }
}
