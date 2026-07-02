package com.rag.knowledge.service;

import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.RagCitationResponse;
import java.util.List;

public interface RagStreamHandler {

    void onCitations(List<RagCitationResponse> citations);

    void onDelta(String delta);

    void onComplete(RagAskResponse response);
}
