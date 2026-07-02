package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.dashboard.DashboardOverviewResponse;
import com.rag.knowledge.dto.dashboard.DocumentTaskResponse;
import com.rag.knowledge.dto.dashboard.RuntimeConfigResponse;
import com.rag.knowledge.dto.dashboard.SystemDiagnosticsResponse;
import com.rag.knowledge.dto.dashboard.SystemHealthResponse;
import com.rag.knowledge.dto.dashboard.TaskLogResponse;
import com.rag.knowledge.dto.doc.DocumentParseResponse;
import com.rag.knowledge.dto.dashboard.TodayMetricsResponse;
import com.rag.knowledge.service.DashboardService;
import com.rag.knowledge.service.DocumentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DocumentService documentService;

    public DashboardController(DashboardService dashboardService, DocumentService documentService) {
        this.dashboardService = dashboardService;
        this.documentService = documentService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/health")
    public ApiResponse<SystemHealthResponse> health() {
        return ApiResponse.success(dashboardService.health());
    }

    @GetMapping("/metrics/today")
    public ApiResponse<TodayMetricsResponse> todayMetrics() {
        return ApiResponse.success(dashboardService.todayMetrics());
    }

    @GetMapping("/runtime-config")
    public ApiResponse<RuntimeConfigResponse> runtimeConfig() {
        return ApiResponse.success(dashboardService.runtimeConfig());
    }

    @GetMapping("/diagnostics")
    public ApiResponse<SystemDiagnosticsResponse> diagnostics() {
        return ApiResponse.success(dashboardService.diagnostics());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<DocumentTaskResponse>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(dashboardService.listDocumentTasks(status, limit));
    }

    @GetMapping("/tasks/{documentId}/logs")
    public ApiResponse<List<TaskLogResponse>> taskLogs(@PathVariable Long documentId) {
        return ApiResponse.success(dashboardService.listTaskLogs(documentId));
    }

    @PostMapping("/tasks/{documentId}/retry")
    public ApiResponse<DocumentParseResponse> retryTask(@PathVariable Long documentId) {
        return ApiResponse.success(documentService.parse(documentId));
    }
}
