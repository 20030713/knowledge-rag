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

    @Test
    void splitShouldRemoveRepeatedPageMarginsAndPageNumbers() {
        String text = """
                星河科技内部资料
                第一页正文介绍缓存雪崩。
                1 / 2
                \f
                星河科技内部资料
                第二页正文介绍缓存击穿。
                2 / 2
                """;

        List<String> chunks = textChunker.split(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst())
                .contains("第一页正文", "第二页正文")
                .doesNotContain("星河科技内部资料", "1 / 2", "2 / 2");
    }

    @Test
    void splitShouldCarryMarkdownHeadingIntoFollowingChunksWithoutRepeatingItInOneChunk() {
        String text = """
                # Redis 故障处理

                Redis 延迟超过阈值时先检查慢查询和热键。

                缓存不可用时切换到受限的数据库直读模式。
                """;

        List<String> chunks = textChunker.split(text, new TextChunker.SplitOptions(45, 8, 20));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).contains("# Redis 故障处理"));
        assertThat(chunks.getFirst().split("# Redis 故障处理", -1)).hasSize(2);
    }
}
