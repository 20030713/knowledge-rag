package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import com.rag.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
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
}
