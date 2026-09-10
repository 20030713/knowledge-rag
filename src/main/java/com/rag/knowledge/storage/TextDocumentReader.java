package com.rag.knowledge.storage;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class TextDocumentReader {

    private static final Set<String> SUPPORTED_TYPES = Set.of("txt", "md", "pdf", "doc", "docx");

    public String read(Path path, String fileType) {
        if (!SUPPORTED_TYPES.contains(fileType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持解析 TXT、Markdown、PDF 和 Word 文档");
        }
        return switch (fileType) {
            case "txt", "md" -> readPlainText(path);
            case "pdf" -> readPdf(path);
            case "docx" -> readDocx(path);
            case "doc" -> readDoc(path);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的文档格式");
        };
    }

    private String readPlainText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文档内容失败");
        }
    }

    private String readPdf(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setPageEnd("\n\f\n");
            return stripper.getText(document);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "解析 PDF 文档失败");
        }
    }

    private String readDocx(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "解析 DOCX 文档失败");
        }
    }

    private String readDoc(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "解析 DOC 文档失败");
        }
    }
}
