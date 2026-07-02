package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.rag.PromptTemplateRequest;
import com.rag.knowledge.dto.rag.PromptTemplateResponse;
import com.rag.knowledge.service.PromptTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    public PromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @GetMapping
    public ApiResponse<List<PromptTemplateResponse>> list(@RequestParam Long kbId) {
        return ApiResponse.success(promptTemplateService.list(kbId));
    }

    @PostMapping
    public ApiResponse<PromptTemplateResponse> create(
            @RequestParam Long kbId,
            @Valid @RequestBody PromptTemplateRequest request
    ) {
        return ApiResponse.success(promptTemplateService.create(kbId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromptTemplateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PromptTemplateRequest request
    ) {
        return ApiResponse.success(promptTemplateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return ApiResponse.success();
    }
}
