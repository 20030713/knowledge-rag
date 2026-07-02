package com.rag.knowledge.service;

import com.rag.knowledge.dto.rag.RagAskRequest;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.ChatMessageResponse;
import com.rag.knowledge.dto.rag.ChatSessionCreateRequest;
import com.rag.knowledge.dto.rag.ChatSessionResponse;
import com.rag.knowledge.dto.rag.HotQuestionResponse;
import com.rag.knowledge.dto.rag.QaFeedbackRequest;
import com.rag.knowledge.dto.rag.QaRecordResponse;
import com.rag.knowledge.dto.rag.RagDebugResponse;
import com.rag.knowledge.dto.rag.RagHistoryExport;
import com.rag.knowledge.dto.rag.RagQualityOverviewResponse;
import java.util.List;

public interface RagService {

    RagAskResponse ask(RagAskRequest request);

    void streamAsk(RagAskRequest request, RagStreamHandler handler);

    RagDebugResponse debug(RagAskRequest request);

    ChatSessionResponse createSession(ChatSessionCreateRequest request);

    List<ChatSessionResponse> listSessions(Long kbId);

    List<ChatMessageResponse> listSessionMessages(Long sessionId);

    void deleteSession(Long sessionId);

    List<HotQuestionResponse> listHotQuestions(Long kbId, Integer limit);

    void clearCache(Long kbId);

    List<QaRecordResponse> listHistory(Long kbId);

    RagHistoryExport exportHistory(Long kbId, String format);

    RagQualityOverviewResponse qualityOverview(Long kbId);

    List<QaRecordResponse> listQualityIssues(Long kbId, String type, Integer limit);

    QaRecordResponse feedback(Long id, QaFeedbackRequest request);

    void deleteHistory(Long id);
}
