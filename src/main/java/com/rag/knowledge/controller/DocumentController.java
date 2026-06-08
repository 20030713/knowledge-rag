package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import com.rag.knowledge.service.DocumentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentResponse> upload(
            @RequestParam Long kbId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(documentService.upload(kbId, file));
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> listMine(@RequestParam Long kbId) {
        return ApiResponse.success(documentService.listMine(kbId));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<DocumentStatusResponse> getStatus(@PathVariable Long id) {
        return ApiResponse.success(documentService.getStatus(id));
    }
}
