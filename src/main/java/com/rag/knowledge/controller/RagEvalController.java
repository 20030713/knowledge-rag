package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.rag.RagEvalCaseRequest;
import com.rag.knowledge.dto.rag.RagEvalCaseResponse;
import com.rag.knowledge.dto.rag.RagEvalRunResponse;
import com.rag.knowledge.dto.rag.RagEvalSummaryResponse;
import com.rag.knowledge.service.RagEvalService;
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
@RequestMapping("/api/rag/evals")
public class RagEvalController {

    private final RagEvalService ragEvalService;

    public RagEvalController(RagEvalService ragEvalService) {
        this.ragEvalService = ragEvalService;
    }

    @GetMapping("/cases")
    public ApiResponse<List<RagEvalCaseResponse>> listCases(@RequestParam Long kbId) {
        return ApiResponse.success(ragEvalService.listCases(kbId));
    }

    @PostMapping("/cases")
    public ApiResponse<RagEvalCaseResponse> createCase(
            @RequestParam Long kbId,
            @Valid @RequestBody RagEvalCaseRequest request
    ) {
        return ApiResponse.success(ragEvalService.createCase(kbId, request));
    }

    @PutMapping("/cases/{id}")
    public ApiResponse<RagEvalCaseResponse> updateCase(
            @PathVariable Long id,
            @Valid @RequestBody RagEvalCaseRequest request
    ) {
        return ApiResponse.success(ragEvalService.updateCase(id, request));
    }

    @DeleteMapping("/cases/{id}")
    public ApiResponse<Void> deleteCase(@PathVariable Long id) {
        ragEvalService.deleteCase(id);
        return ApiResponse.success();
    }

    @PostMapping("/run")
    public ApiResponse<RagEvalSummaryResponse> run(@RequestParam Long kbId) {
        return ApiResponse.success(ragEvalService.run(kbId));
    }

    @GetMapping("/runs")
    public ApiResponse<List<RagEvalRunResponse>> recentRuns(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(ragEvalService.recentRuns(kbId, limit));
    }
}
