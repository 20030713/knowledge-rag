package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.config.ChatModelProperties;
import com.rag.knowledge.config.RateLimitProperties;
import com.rag.knowledge.dto.rag.ChatModelStatusResponse;
import com.rag.knowledge.dto.rag.ChatMessageResponse;
import com.rag.knowledge.dto.rag.ChatSessionCreateRequest;
import com.rag.knowledge.dto.rag.ChatSessionResponse;
import com.rag.knowledge.dto.rag.HotQuestionResponse;
import com.rag.knowledge.dto.rag.RagAskRequest;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.QaFeedbackRequest;
import com.rag.knowledge.dto.rag.QaRecordResponse;
import com.rag.knowledge.dto.rag.RagCitationResponse;
import com.rag.knowledge.dto.rag.RagDebugResponse;
import com.rag.knowledge.dto.rag.RagHistoryExport;
import com.rag.knowledge.dto.rag.RagQualityOverviewResponse;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.RateLimitService;
import com.rag.knowledge.service.RagService;
import com.rag.knowledge.service.RagStreamHandler;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final ChatModelProperties chatModelProperties;

    public RagController(
            RagService ragService,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            ChatModelProperties chatModelProperties
    ) {
        this.ragService = ragService;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.chatModelProperties = chatModelProperties;
    }

    @PostMapping("/ask")
    public ApiResponse<RagAskResponse> ask(@Valid @RequestBody RagAskRequest request) {
        if (rateLimitProperties.isEnabled()) {
            rateLimitService.check("rag:ask:user:" + UserContext.getRequired().userId(), rateLimitProperties.getRagAsk());
        }
        return ApiResponse.success(ragService.ask(request));
    }

    @PostMapping("/debug")
    public ApiResponse<RagDebugResponse> debug(@Valid @RequestBody RagAskRequest request) {
        if (rateLimitProperties.isEnabled()) {
            rateLimitService.check("rag:debug:user:" + UserContext.getRequired().userId(), rateLimitProperties.getRagAsk());
        }
        return ApiResponse.success(ragService.debug(request));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@Valid @RequestBody ChatSessionCreateRequest request) {
        return ApiResponse.success(ragService.createSession(request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(@RequestParam Long kbId) {
        return ApiResponse.success(ragService.listSessions(kbId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listSessionMessages(@PathVariable Long sessionId) {
        return ApiResponse.success(ragService.listSessionMessages(sessionId));
    }

    @GetMapping("/hot-questions")
    public ApiResponse<List<HotQuestionResponse>> hotQuestions(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(ragService.listHotQuestions(kbId, limit));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        ragService.deleteSession(sessionId);
        return ApiResponse.success();
    }

    @DeleteMapping("/cache")
    public ApiResponse<Void> clearCache(@RequestParam Long kbId) {
        ragService.clearCache(kbId);
        return ApiResponse.success();
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAsk(@Valid @RequestBody RagAskRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        if (rateLimitProperties.isEnabled()) {
            rateLimitService.check("rag:ask:user:" + loginUser.userId(), rateLimitProperties.getRagAsk());
        }
        SseEmitter emitter = new SseEmitter(180_000L);
        CompletableFuture.runAsync(() -> {
            UserContext.set(loginUser);
            try {
                ragService.streamAsk(request, new RagStreamHandler() {
                    @Override
                    public void onCitations(List<RagCitationResponse> citations) {
                        sendEvent(emitter, "citations", Map.of("citations", citations));
                    }

                    @Override
                    public void onDelta(String delta) {
                        sendEvent(emitter, "delta", Map.of("content", delta));
                    }

                    @Override
                    public void onComplete(RagAskResponse response) {
                        sendEvent(emitter, "complete", response);
                    }
                });
                emitter.complete();
            } catch (Exception exception) {
                sendEvent(emitter, "error", Map.of("message", exception.getMessage() == null ? "问答失败" : exception.getMessage()));
                emitter.complete();
            } finally {
                UserContext.clear();
            }
        });
        return emitter;
    }

    @GetMapping("/history")
    public ApiResponse<List<QaRecordResponse>> listHistory(@RequestParam Long kbId) {
        return ApiResponse.success(ragService.listHistory(kbId));
    }

    @GetMapping("/history/export")
    public ResponseEntity<String> exportHistory(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "csv") String format
    ) {
        RagHistoryExport export = ragService.exportHistory(kbId, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }

    @GetMapping("/quality/overview")
    public ApiResponse<RagQualityOverviewResponse> qualityOverview(@RequestParam Long kbId) {
        return ApiResponse.success(ragService.qualityOverview(kbId));
    }

    @GetMapping("/quality/issues")
    public ApiResponse<List<QaRecordResponse>> qualityIssues(
            @RequestParam Long kbId,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(ragService.listQualityIssues(kbId, type, limit));
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception exception) {
            throw new IllegalStateException("SSE send failed", exception);
        }
    }

    @GetMapping("/model/status")
    public ApiResponse<ChatModelStatusResponse> modelStatus() {
        return ApiResponse.success(new ChatModelStatusResponse(
                chatModelProperties.isEnabled(),
                chatModelProperties.getApiKey() != null && !chatModelProperties.getApiKey().isBlank(),
                chatModelProperties.available(),
                chatModelProperties.safeBaseUrl(),
                chatModelProperties.safeEndpointPath(),
                chatModelProperties.getModel(),
                chatModelProperties.isThinkingEnabled(),
                chatModelProperties.safeReasoningEffort()
        ));
    }

    @PutMapping("/history/{id}/feedback")
    public ApiResponse<QaRecordResponse> feedback(
            @PathVariable Long id,
            @Valid @RequestBody QaFeedbackRequest request
    ) {
        return ApiResponse.success(ragService.feedback(id, request));
    }

    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteHistory(@PathVariable Long id) {
        ragService.deleteHistory(id);
        return ApiResponse.success();
    }
}
