package com.rag.knowledge.service;

import com.rag.knowledge.dto.rag.HotQuestionResponse;
import java.util.List;

public interface HotQuestionService {

    void record(Long kbId, String question);

    List<HotQuestionResponse> list(Long kbId, Integer limit);
}
