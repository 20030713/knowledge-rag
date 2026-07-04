package com.rag.knowledge.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.knowledge.config.ChatModelProperties;
import com.rag.knowledge.rag.AnswerStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleChatModelServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void explicitlyDisablesThinkingAndOmitsReasoningEffort() throws Exception {
        ChatModelProperties properties = properties(false);
        OpenAiCompatibleChatModelService service = new OpenAiCompatibleChatModelService(properties, objectMapper);

        JsonNode body = objectMapper.readTree(service.serializeRequestBody(
                "test question", List.of(), AnswerStyle.BRIEF, null, true
        ));

        assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(body.has("reasoning_effort")).isFalse();
        assertThat(body.path("stream").asBoolean()).isTrue();
    }

    @Test
    void enablesThinkingWithConfiguredReasoningEffort() throws Exception {
        ChatModelProperties properties = properties(true);
        OpenAiCompatibleChatModelService service = new OpenAiCompatibleChatModelService(properties, objectMapper);

        JsonNode body = objectMapper.readTree(service.serializeRequestBody(
                "test question", List.of(), AnswerStyle.BRIEF, null, false
        ));

        assertThat(body.path("thinking").path("type").asText()).isEqualTo("enabled");
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("high");
        assertThat(body.path("stream").asBoolean()).isFalse();
    }

    private ChatModelProperties properties(boolean thinkingEnabled) {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setModel("deepseek-v4-pro");
        properties.setThinkingEnabled(thinkingEnabled);
        properties.setReasoningEffort("high");
        return properties;
    }
}
