package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.UploadProperties;
import com.rag.knowledge.domain.entity.Document;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.enums.DocumentStatus;
import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.DocumentMapper;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.DocumentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "doc", "docx", "md", "txt");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UploadProperties uploadProperties;

    public DocumentServiceImpl(
            DocumentMapper documentMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            UploadProperties uploadProperties
    ) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.uploadProperties = uploadProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentResponse upload(Long kbId, MultipartFile file) {
        LoginUser loginUser = UserContext.getRequired();
        ensureKnowledgeBaseOwned(kbId, loginUser.userId());
        validateFile(file);

        String originalName = cleanFileName(file.getOriginalFilename());
        String fileType = extensionOf(originalName);
        String relativePath = buildRelativePath(kbId, fileType);
        Path target = Path.of(uploadProperties.getRootPath()).resolve(relativePath);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        LocalDateTime now = LocalDateTime.now();
        Document document = new Document();
        document.setUserId(loginUser.userId());
        document.setKbId(kbId);
        document.setFileName(originalName);
        document.setFileType(fileType);
        document.setFileUrl(relativePath.replace('\\', '/'));
        document.setFileSize(file.getSize());
        document.setStatus(DocumentStatus.UPLOADED.name());
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        return toResponse(document);
    }

    @Override
    public List<DocumentResponse> listMine(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        ensureKnowledgeBaseOwned(kbId, loginUser.userId());
        return documentMapper.selectList(new LambdaQueryWrapper<Document>()
                        .eq(Document::getUserId, loginUser.userId())
                        .eq(Document::getKbId, kbId)
                        .orderByDesc(Document::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public DocumentStatusResponse getStatus(Long id) {
        Document document = getOwnedDocument(id);
        return new DocumentStatusResponse(document.getId(), document.getStatus(), document.getErrorMsg());
    }

    private void ensureKnowledgeBaseOwned(Long kbId, Long userId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getUserId, userId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
    }

    private Document getOwnedDocument(Long id) {
        LoginUser loginUser = UserContext.getRequired();
        Document document = documentMapper.selectOne(new LambdaQueryWrapper<Document>()
                .eq(Document::getId, id)
                .eq(Document::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        if (file.getSize() > uploadProperties.maxSizeBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件大小不能超过 " + uploadProperties.getMaxSizeMb() + "MB");
        }

        String fileName = cleanFileName(file.getOriginalFilename());
        String fileType = extensionOf(fileName);
        if (!ALLOWED_TYPES.contains(fileType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 PDF、Word、Markdown 和 TXT 文档");
        }
    }

    private String cleanFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件后缀不能为空");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildRelativePath(Long kbId, String fileType) {
        String day = LocalDate.now().format(DAY_FORMATTER);
        return Path.of(String.valueOf(kbId), day, UUID.randomUUID() + "." + fileType).toString();
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getKbId(),
                document.getFileName(),
                document.getFileType(),
                document.getFileUrl(),
                document.getFileSize(),
                document.getStatus(),
                document.getErrorMsg(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
