package com.rag.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.DocumentChunk;
import com.rag.knowledge.repository.DocumentChunkMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalVectorSearchServiceTests {

    private final DocumentChunkMapper documentChunkMapper = org.mockito.Mockito.mock(DocumentChunkMapper.class);
    private final VectorSearchProperties properties = new VectorSearchProperties();
    private final LocalVectorSearchService searchService = new LocalVectorSearchService(
            documentChunkMapper,
            new HashingTextEmbeddingService(properties),
            properties
    );

    @Test
    void searchShouldRankMoreRelevantChunkFirst() {
        DocumentChunk redisChunk = chunk(1L, 1, "Redis 缓存用于保存 RAG 问答结果，并通过 TTL 控制过期时间。");
        DocumentChunk authChunk = chunk(2L, 2, "JWT 登录鉴权用于保护接口，用户需要携带 Bearer Token。");
        when(documentChunkMapper.selectList(any())).thenReturn(List.of(authChunk, redisChunk));

        List<VectorSearchResult> results = searchService.search(10L, 20L, "Redis 缓存如何工作", 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().getId()).isEqualTo(redisChunk.getId());
        assertThat(results.get(0).finalScore()).isGreaterThan(results.get(1).finalScore());
    }

    private DocumentChunk chunk(Long id, Integer chunkNo, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setUserId(10L);
        chunk.setKbId(20L);
        chunk.setDocumentId(30L);
        chunk.setChunkNo(chunkNo);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setCreatedAt(LocalDateTime.now());
        return chunk;
    }
}
