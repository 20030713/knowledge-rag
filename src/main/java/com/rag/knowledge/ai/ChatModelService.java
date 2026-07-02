package com.rag.knowledge.ai;

import com.rag.knowledge.dto.rag.RagCitationResponse;
import com.rag.knowledge.rag.AnswerStyle;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface ChatModelService {

    Optional<String> generateAnswer(String question, List<RagCitationResponse> citations, AnswerStyle style);

    Optional<String> generateAnswer(String question, List<RagCitationResponse> citations, AnswerStyle style, String systemPrompt);

    Optional<String> streamAnswer(String question, List<RagCitationResponse> citations, AnswerStyle style, Consumer<String> onDelta);

    Optional<String> streamAnswer(String question, List<RagCitationResponse> citations, AnswerStyle style, String systemPrompt, Consumer<String> onDelta);

    boolean available();

    String cacheNamespace();

    String modelName();
}
