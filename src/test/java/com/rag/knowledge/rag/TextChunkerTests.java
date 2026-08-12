package com.rag.knowledge.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTests {

    private final TextChunker textChunker = new TextChunker();

    @Test
    void splitShouldCleanBlankAndReturnEmptyList() {
        assertThat(textChunker.split(" \n\n\t ")).isEmpty();
    }

    @Test
    void splitShouldKeepChunksSmallAndReadable() {
        String text = "a".repeat(900);

        List<String> chunks = textChunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(420));
    }

    @Test
    void splitShouldPreferParagraphBoundaryAndCleanRepeatedBlankLines() {
        String text = """
                第一段介绍 Redis 缓存雪崩。




                第二段介绍缓存击穿。


                第三段介绍缓存穿透。
                """;

        List<String> chunks = textChunker.split(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).doesNotContain("\n\n\n");
        assertThat(chunks.get(0)).contains("第一段介绍 Redis 缓存雪崩。");
        assertThat(chunks.get(0)).contains("第二段介绍缓存击穿。");
    }

    @Test
    void splitShouldKeepOverlapBetweenLongChunks() {
        String text = "缓存雪崩需要随机 TTL。".repeat(60);

        List<String> chunks = textChunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(1)).contains(chunks.get(0).substring(chunks.get(0).length() - 20));
    }

    @Test
    void splitShouldUseKnowledgeBaseSpecificOptions() {
        String text = "a".repeat(900);

        List<String> chunks = textChunker.split(text, new TextChunker.SplitOptions(300, 40, 120));

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(300));
        assertThat(chunks.get(1)).startsWith(chunks.get(0).substring(chunks.get(0).length() - 40));
    }
}
