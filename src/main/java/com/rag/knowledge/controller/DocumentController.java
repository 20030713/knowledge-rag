package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.doc.DocumentBatchRequest;
import com.rag.knowledge.dto.doc.DocumentBatchResponse;
import com.rag.knowledge.dto.doc.DocumentChunkResponse;
import com.rag.knowledge.dto.doc.DocumentIndexStatusResponse;
import com.rag.knowledge.dto.doc.DocumentParseResponse;
import com.rag.knowledge.dto.doc.DocumentQualityReportResponse;
import com.rag.knowledge.dto.doc.DocumentResponse;
import com.rag.knowledge.dto.doc.DocumentSearchResultResponse;
import com.rag.knowledge.dto.doc.DocumentStatusResponse;
import com.rag.knowledge.dto.doc.KnowledgeBaseIndexStatusResponse;
import com.rag.knowledge.dto.doc.VectorSyncResponse;
import com.rag.knowledge.service.DocumentService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/{id}/parse")
    public ApiResponse<DocumentParseResponse> parse(@PathVariable Long id) {
        return ApiResponse.success(documentService.parse(id));
    }

    @PostMapping("/batch/parse")
    public ApiResponse<DocumentBatchResponse> batchParse(@RequestBody DocumentBatchRequest request) {
        return ApiResponse.success(documentService.batchParse(request.ids()));
    }

    @DeleteMapping("/batch")
    public ApiResponse<DocumentBatchResponse> batchDelete(@RequestBody DocumentBatchRequest request) {
        return ApiResponse.success(documentService.batchDelete(request.ids()));
    }

    @PostMapping("/kb/{kbId}/rebuild")
    public ApiResponse<DocumentBatchResponse> rebuildKnowledgeBase(@PathVariable Long kbId) {
        return ApiResponse.success(documentService.rebuildKnowledgeBase(kbId));
    }

    @GetMapping("/{id}/chunks")
    public ApiResponse<List<DocumentChunkResponse>> listChunks(@PathVariable Long id) {
        return ApiResponse.success(documentService.listChunks(id));
    }

    @GetMapping("/kb/{kbId}/search")
    public ApiResponse<List<DocumentSearchResultResponse>> searchChunks(
            @PathVariable Long kbId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") Integer limit
    ) {
        return ApiResponse.success(documentService.searchChunks(kbId, keyword, limit));
    }

    @GetMapping("/kb/{kbId}/quality")
    public ApiResponse<DocumentQualityReportResponse> qualityReport(@PathVariable Long kbId) {
        return ApiResponse.success(documentService.qualityReport(kbId));
    }

    @GetMapping("/{id}/index-status")
    public ApiResponse<DocumentIndexStatusResponse> indexStatus(@PathVariable Long id) {
        return ApiResponse.success(documentService.indexStatus(id));
    }

    @GetMapping("/kb/{kbId}/index-status")
    public ApiResponse<KnowledgeBaseIndexStatusResponse> knowledgeBaseIndexStatus(@PathVariable Long kbId) {
        return ApiResponse.success(documentService.knowledgeBaseIndexStatus(kbId));
    }

    @PostMapping("/{id}/sync-vector")
    public ApiResponse<VectorSyncResponse> syncDocumentVectors(@PathVariable Long id) {
        return ApiResponse.success(documentService.syncDocumentVectors(id));
    }

    @PostMapping("/kb/{kbId}/sync-vector")
    public ApiResponse<VectorSyncResponse> syncKnowledgeBaseVectors(@PathVariable Long kbId) {
        return ApiResponse.success(documentService.syncKnowledgeBaseVectors(kbId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.success();
    }
}
