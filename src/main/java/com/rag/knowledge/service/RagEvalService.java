package com.rag.knowledge.service;

import com.rag.knowledge.dto.rag.RagEvalCaseRequest;
import com.rag.knowledge.dto.rag.RagEvalCaseResponse;
import com.rag.knowledge.dto.rag.RagEvalRunResponse;
import com.rag.knowledge.dto.rag.RagEvalSummaryResponse;
import java.util.List;

public interface RagEvalService {

    List<RagEvalCaseResponse> listCases(Long kbId);

    RagEvalCaseResponse createCase(Long kbId, RagEvalCaseRequest request);

    RagEvalCaseResponse updateCase(Long id, RagEvalCaseRequest request);

    void deleteCase(Long id);

    RagEvalSummaryResponse run(Long kbId);

    List<RagEvalRunResponse> recentRuns(Long kbId, Integer limit);
}
