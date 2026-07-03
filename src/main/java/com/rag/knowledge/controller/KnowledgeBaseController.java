package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.dto.kb.KnowledgeBaseBackupResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseImportResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberCandidateResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseMemberUpdateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import com.rag.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> listMine() {
        return ApiResponse.success(knowledgeBaseService.listMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> getMine(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.getMine(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeBaseUpdateRequest request
    ) {
        return ApiResponse.success(knowledgeBaseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<KnowledgeBaseMemberResponse>> listMembers(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.listMembers(id));
    }

    @GetMapping("/{id}/member-candidates")
    public ApiResponse<List<KnowledgeBaseMemberCandidateResponse>> memberCandidates(
            @PathVariable Long id,
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(knowledgeBaseService.searchMemberCandidates(id, keyword, limit));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<KnowledgeBaseMemberResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeBaseMemberRequest request
    ) {
        return ApiResponse.success(knowledgeBaseService.addMember(id, request));
    }

    @PutMapping("/{id}/members/{memberId}")
    public ApiResponse<KnowledgeBaseMemberResponse> updateMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody KnowledgeBaseMemberUpdateRequest request
    ) {
        return ApiResponse.success(knowledgeBaseService.updateMember(id, memberId, request));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        knowledgeBaseService.removeMember(id, memberId);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/backup")
    public ResponseEntity<byte[]> exportBackup(@PathVariable Long id) throws Exception {
        KnowledgeBaseBackupResponse backup = knowledgeBaseService.exportBackup(id);
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        String fileName = sanitizeFileName(backup.knowledgeBase().name()) + "-backup.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }

    @PostMapping(value = "/backup/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeBaseImportResponse> importBackup(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.success(knowledgeBaseService.importBackup(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "knowledge-base";
        }
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }
}
