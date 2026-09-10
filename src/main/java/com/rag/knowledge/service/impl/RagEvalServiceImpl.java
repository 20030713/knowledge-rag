package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.RagEvalCase;
import com.rag.knowledge.domain.entity.RagEvalRun;
import com.rag.knowledge.dto.rag.RagAskRequest;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.RagEvalCaseRequest;
import com.rag.knowledge.dto.rag.RagEvalCaseResponse;
import com.rag.knowledge.dto.rag.RagEvalRunResponse;
import com.rag.knowledge.dto.rag.RagEvalSummaryResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.RagEvalCaseMapper;
import com.rag.knowledge.repository.RagEvalRunMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.RagEvalService;
import com.rag.knowledge.service.RagService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagEvalServiceImpl implements RagEvalService {

    private final RagEvalCaseMapper caseMapper;
    private final RagEvalRunMapper runMapper;
    private final KnowledgeBasePermissionService permissionService;
    private final RagService ragService;

    public RagEvalServiceImpl(
            RagEvalCaseMapper caseMapper,
            RagEvalRunMapper runMapper,
            KnowledgeBasePermissionService permissionService,
            RagService ragService
    ) {
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.permissionService = permissionService;
        this.ragService = ragService;
    }

    @Override
    public List<RagEvalCaseResponse> listCases(Long kbId) {
        permissionService.requireRead(kbId);
        return caseMapper.selectList(new LambdaQueryWrapper<RagEvalCase>()
                        .eq(RagEvalCase::getKbId, kbId)
                        .orderByDesc(RagEvalCase::getUpdatedAt))
                .stream()
                .map(this::toCaseResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalCaseResponse createCase(Long kbId, RagEvalCaseRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireAdmin(kbId);
        LocalDateTime now = LocalDateTime.now();
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.setUserId(access.ownerUserId());
        evalCase.setKbId(kbId);
        apply(evalCase, request);
        evalCase.setCreatedAt(now);
        evalCase.setUpdatedAt(now);
        if (!loginUser.userId().equals(access.ownerUserId())) {
            evalCase.setUserId(access.ownerUserId());
        }
        caseMapper.insert(evalCase);
        return toCaseResponse(evalCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalCaseResponse updateCase(Long id, RagEvalCaseRequest request) {
        RagEvalCase evalCase = getEditableCase(id);
        apply(evalCase, request);
        evalCase.setUpdatedAt(LocalDateTime.now());
        caseMapper.updateById(evalCase);
        return toCaseResponse(evalCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long id) {
        RagEvalCase evalCase = getEditableCase(id);
        caseMapper.deleteById(evalCase.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RagEvalSummaryResponse run(Long kbId) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBasePermissionService.KnowledgeBaseAccess access = permissionService.requireRead(kbId);
        List<RagEvalCase> cases = caseMapper.selectList(new LambdaQueryWrapper<RagEvalCase>()
                .eq(RagEvalCase::getKbId, kbId)
                .eq(RagEvalCase::getEnabled, true)
                .orderByAsc(RagEvalCase::getCreatedAt));
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先添加并启用评测用例");
        }
        List<RagEvalRunResponse> runs = cases.stream()
                .map(evalCase -> runCase(loginUser.userId(), access.ownerUserId(), evalCase))
                .toList();
        int passedCount = (int) runs.stream().filter(run -> Boolean.TRUE.equals(run.passed())).count();
        double avgKeywordScore = runs.stream().mapToDouble(run -> run.keywordScore() == null ? 0 : run.keywordScore()).average().orElse(0);
        double retrievalHitRate = averageBoolean(runs.stream().map(RagEvalRunResponse::retrievalHit).toList());
        double meanReciprocalRank = runs.stream()
                .map(RagEvalRunResponse::reciprocalRank)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        double averageCitationPrecision = runs.stream()
                .map(RagEvalRunResponse::citationPrecision)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
        double abstentionAccuracy = averageBoolean(runs.stream().map(RagEvalRunResponse::abstentionCorrect).toList());
        long avgLatency = Math.round(runs.stream().mapToLong(run -> run.latencyMs() == null ? 0 : run.latencyMs()).average().orElse(0));
        return new RagEvalSummaryResponse(
                kbId,
                runs.size(),
                passedCount,
                round(passedCount * 1.0 / runs.size()),
                round(avgKeywordScore),
                round(retrievalHitRate),
                round(meanReciprocalRank),
                round(averageCitationPrecision),
                round(abstentionAccuracy),
                avgLatency,
                runs
        );
    }

    @Override
    public List<RagEvalRunResponse> recentRuns(Long kbId, Integer limit) {
        permissionService.requireRead(kbId);
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        return runMapper.selectList(new LambdaQueryWrapper<RagEvalRun>()
                        .eq(RagEvalRun::getKbId, kbId)
                        .orderByDesc(RagEvalRun::getCreatedAt)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    private RagEvalRunResponse runCase(Long runnerUserId, Long ownerUserId, RagEvalCase evalCase) {
        long startedAt = System.nanoTime();
        RagAskResponse answer = ragService.ask(new RagAskRequest(
                evalCase.getKbId(),
                null,
                evalCase.getQuestion(),
                "STRICT",
                5,
                null,
                null,
                true,
                false
        ));
        double keywordScore = keywordScore(answer.answer(), evalCase.getExpectedKeywords(), evalCase.getExpectedAnswer());
        boolean noAnswerCase = Boolean.TRUE.equals(evalCase.getExpectNoAnswer());
        List<String> expectedSources = expectedSources(evalCase.getExpectedSource());
        Boolean retrievalHit = noAnswerCase ? null : retrievalHit(answer, expectedSources);
        Double reciprocalRank = noAnswerCase ? null : reciprocalRank(answer, expectedSources);
        Double citationPrecision = noAnswerCase ? null : citationPrecision(answer, expectedSources);
        Boolean abstentionCorrect = noAnswerCase ? isAbstention(answer) : null;
        RagEvalRun run = new RagEvalRun();
        run.setUserId(ownerUserId == null ? runnerUserId : ownerUserId);
        run.setKbId(evalCase.getKbId());
        run.setCaseId(evalCase.getId());
        run.setQuestion(evalCase.getQuestion());
        run.setAnswer(answer.answer());
        run.setHitCount(answer.hitCount());
        run.setKeywordScore(round(keywordScore));
        run.setNoAnswerCase(noAnswerCase);
        run.setRetrievalHit(retrievalHit);
        run.setReciprocalRank(reciprocalRank == null ? null : round(reciprocalRank));
        run.setCitationPrecision(citationPrecision == null ? null : round(citationPrecision));
        run.setAbstentionCorrect(abstentionCorrect);
        run.setPassed(noAnswerCase
                ? Boolean.TRUE.equals(abstentionCorrect)
                : keywordScore >= 0.6 && Boolean.TRUE.equals(retrievalHit));
        run.setLatencyMs(answer.latencyMs() == null ? elapsedMillis(startedAt) : answer.latencyMs());
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);
        return toRunResponse(run);
    }

    private RagEvalCase getEditableCase(Long id) {
        RagEvalCase evalCase = caseMapper.selectById(id);
        if (evalCase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评测用例不存在");
        }
        permissionService.requireAdmin(evalCase.getKbId());
        return evalCase;
    }

    private void apply(RagEvalCase evalCase, RagEvalCaseRequest request) {
        evalCase.setQuestion(request.question().trim());
        evalCase.setExpectedAnswer(request.expectedAnswer().trim());
        evalCase.setExpectedKeywords(normalizeKeywords(request.expectedKeywords()));
        evalCase.setExpectedSource(normalizeKeywords(request.expectedSource()));
        evalCase.setExpectNoAnswer(Boolean.TRUE.equals(request.expectNoAnswer()));
        evalCase.setEnabled(request.enabled() == null || request.enabled());
    }

    private List<String> expectedSources(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，;；\\n]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private boolean retrievalHit(RagAskResponse answer, List<String> expectedSources) {
        if (expectedSources.isEmpty()) {
            return answer.hitCount() != null && answer.hitCount() > 0;
        }
        return answer.citations().stream().anyMatch(citation -> sourceMatches(citation.documentName(), expectedSources));
    }

    private double reciprocalRank(RagAskResponse answer, List<String> expectedSources) {
        if (expectedSources.isEmpty()) {
            return answer.citations().isEmpty() ? 0 : 1;
        }
        for (int index = 0; index < answer.citations().size(); index++) {
            if (sourceMatches(answer.citations().get(index).documentName(), expectedSources)) {
                return 1.0 / (index + 1);
            }
        }
        return 0;
    }

    private double citationPrecision(RagAskResponse answer, List<String> expectedSources) {
        if (answer.citations().isEmpty()) {
            return 0;
        }
        if (expectedSources.isEmpty()) {
            return 1;
        }
        long matches = answer.citations().stream()
                .filter(citation -> sourceMatches(citation.documentName(), expectedSources))
                .count();
        return matches * 1.0 / answer.citations().size();
    }

    private boolean sourceMatches(String documentName, List<String> expectedSources) {
        String normalized = documentName == null ? "" : documentName.toLowerCase(Locale.ROOT);
        return expectedSources.stream()
                .map(source -> source.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private boolean isAbstention(RagAskResponse answer) {
        String normalized = answer.answer() == null ? "" : answer.answer();
        return answer.citations().isEmpty()
                && (normalized.contains("资料不足") || normalized.contains("无法确认") || normalized.contains("不能确认"));
    }

    private double averageBoolean(List<Boolean> values) {
        List<Boolean> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return 0;
        }
        return present.stream().filter(Boolean.TRUE::equals).count() * 1.0 / present.size();
    }

    private String normalizeKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return "";
        }
        return Arrays.stream(keywords.split("[,，;；\\n]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(30)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private double keywordScore(String answer, String expectedKeywords, String expectedAnswer) {
        List<String> keywords = expectedKeywords == null || expectedKeywords.isBlank()
                ? Arrays.stream(expectedAnswer.split("[\\s,，。；;、]+")).filter(item -> item.length() >= 2).limit(12).toList()
                : Arrays.stream(expectedKeywords.split(",")).filter(item -> !item.isBlank()).toList();
        if (keywords.isEmpty()) {
            return 0;
        }
        String normalizedAnswer = normalizeForMatch(answer);
        long matched = keywords.stream()
                .map(this::normalizeForMatch)
                .filter(item -> !item.isBlank())
                .filter(normalizedAnswer::contains)
                .count();
        return matched * 1.0 / keywords.size();
    }

    private String normalizeForMatch(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\s,，。；;：:、.!！？?()（）\\[\\]【】_-]+", "");
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private RagEvalCaseResponse toCaseResponse(RagEvalCase evalCase) {
        return new RagEvalCaseResponse(
                evalCase.getId(),
                evalCase.getKbId(),
                evalCase.getQuestion(),
                evalCase.getExpectedAnswer(),
                evalCase.getExpectedKeywords(),
                evalCase.getExpectedSource(),
                evalCase.getExpectNoAnswer(),
                evalCase.getEnabled(),
                evalCase.getCreatedAt(),
                evalCase.getUpdatedAt()
        );
    }

    private RagEvalRunResponse toRunResponse(RagEvalRun run) {
        return new RagEvalRunResponse(
                run.getId(),
                run.getCaseId(),
                run.getQuestion(),
                run.getAnswer(),
                run.getHitCount(),
                run.getKeywordScore(),
                run.getNoAnswerCase(),
                run.getRetrievalHit(),
                run.getReciprocalRank(),
                run.getCitationPrecision(),
                run.getAbstentionCorrect(),
                run.getPassed(),
                run.getLatencyMs(),
                run.getCreatedAt()
        );
    }
}
