package com.rag.knowledge.service;

import com.rag.knowledge.dto.dashboard.DashboardOverviewResponse;
import com.rag.knowledge.dto.dashboard.DocumentTaskResponse;
import com.rag.knowledge.dto.dashboard.RuntimeConfigResponse;
import com.rag.knowledge.dto.dashboard.SystemDiagnosticsResponse;
import com.rag.knowledge.dto.dashboard.SystemHealthResponse;
import com.rag.knowledge.dto.dashboard.TaskLogResponse;
import com.rag.knowledge.dto.dashboard.TodayMetricsResponse;
import java.util.List;

public interface DashboardService {

    DashboardOverviewResponse overview();

    SystemHealthResponse health();

    TodayMetricsResponse todayMetrics();

    RuntimeConfigResponse runtimeConfig();

    SystemDiagnosticsResponse diagnostics();

    List<DocumentTaskResponse> listDocumentTasks(String status, Integer limit);

    List<TaskLogResponse> listTaskLogs(Long documentId);
}
