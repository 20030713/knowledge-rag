package com.rag.knowledge.dto.dashboard;

import java.util.List;

public record DashboardOverviewResponse(
        Long knowledgeBaseCount,
        Long documentCount,
        Long chunkCount,
        Long qaCount,
        Long completedDocumentCount,
        Long failedDocumentCount,
        Long uploadedDocumentCount,
        Long parsingDocumentCount,
        Long feedbackCount,
        Long helpfulFeedbackCount,
        Long unhelpfulFeedbackCount,
        List<RecentQuestionResponse> recentQuestions,
        List<DocumentTaskResponse> recentTasks
) {
}
