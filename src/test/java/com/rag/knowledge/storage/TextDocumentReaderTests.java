package com.rag.knowledge.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rag.knowledge.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextDocumentReaderTests {

    private final TextDocumentReader reader = new TextDocumentReader();

    @TempDir
    Path tempDir;

    @Test
    void readShouldLoadPlainText() throws Exception {
        Path file = tempDir.resolve("demo.txt");
        Files.writeString(file, "企业知识库 RAG");

        String content = reader.read(file, "txt");

        assertThat(content).isEqualTo("企业知识库 RAG");
    }

    @Test
    void readShouldRejectUnsupportedType() {
        Path file = tempDir.resolve("demo.xlsx");

        assertThatThrownBy(() -> reader.read(file, "xlsx"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前仅支持解析");
    }
}
