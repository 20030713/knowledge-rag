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
        long avgLatency = Math.round(runs.stream().mapToLong(run -> run.latencyMs() == null ? 0 : run.latencyMs()).average().orElse(0));
        return new RagEvalSummaryResponse(
                kbId,
                runs.size(),
                passedCount,
                round(passedCount * 1.0 / runs.size()),
                round(avgKeywordScore),
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
        RagEvalRun run = new RagEvalRun();
        run.setUserId(ownerUserId == null ? runnerUserId : ownerUserId);
        run.setKbId(evalCase.getKbId());
        run.setCaseId(evalCase.getId());
        run.setQuestion(evalCase.getQuestion());
        run.setAnswer(answer.answer());
        run.setHitCount(answer.hitCount());
        run.setKeywordScore(round(keywordScore));
        run.setPassed(keywordScore >= 0.6 && answer.hitCount() != null && answer.hitCount() > 0);
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
        evalCase.setEnabled(request.enabled() == null || request.enabled());
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
        String normalizedAnswer = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        long matched = keywords.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(normalizedAnswer::contains)
                .count();
        return matched * 1.0 / keywords.size();
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
                run.getPassed(),
                run.getLatencyMs(),
                run.getCreatedAt()
        );
    }
}
