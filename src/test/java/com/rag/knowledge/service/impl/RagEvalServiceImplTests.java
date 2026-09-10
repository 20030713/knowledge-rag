package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rag.knowledge.domain.entity.RagEvalCase;
import com.rag.knowledge.domain.enums.KnowledgeBaseMemberRole;
import com.rag.knowledge.dto.rag.RagAskResponse;
import com.rag.knowledge.dto.rag.RagCitationResponse;
import com.rag.knowledge.repository.RagEvalCaseMapper;
import com.rag.knowledge.repository.RagEvalRunMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.RagService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagEvalServiceImplTests {

    @Mock private RagEvalCaseMapper caseMapper;
    @Mock private RagEvalRunMapper runMapper;
    @Mock private KnowledgeBasePermissionService permissionService;
    @Mock private RagService ragService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void evaluatesRetrievalCitationAndAbstentionSeparately() {
        UserContext.set(new LoginUser(1L, "eval-user", "USER"));
        when(permissionService.requireRead(10L)).thenReturn(new KnowledgeBasePermissionService.KnowledgeBaseAccess(
                10L, 1L, 1L, KnowledgeBaseMemberRole.OWNER, true
        ));
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                answerableCase(),
                noAnswerCase()
        ));
        when(ragService.ask(any())).thenReturn(answerWithMixedCitations(), insufficientAnswer());

        var summary = service().run(10L);

        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.passedCount()).isEqualTo(2);
        assertThat(summary.passRate()).isEqualTo(1.0);
        assertThat(summary.retrievalHitRate()).isEqualTo(1.0);
        assertThat(summary.meanReciprocalRank()).isEqualTo(1.0);
        assertThat(summary.averageCitationPrecision()).isEqualTo(0.5);
        assertThat(summary.abstentionAccuracy()).isEqualTo(1.0);
        assertThat(summary.runs()).extracting(run -> run.passed()).containsOnly(true);
    }

    private RagEvalCase answerableCase() {
        RagEvalCase evalCase = baseCase(101L, "报销期限是多少？", false);
        evalCase.setExpectedAnswer("30个自然日");
        evalCase.setExpectedKeywords("30个自然日,报销时限");
        evalCase.setExpectedSource("expense-policy.md");
        return evalCase;
    }

    private RagEvalCase noAnswerCase() {
        RagEvalCase evalCase = baseCase(102L, "年假可以结转几天？", true);
        evalCase.setExpectedAnswer("当前资料不足以确认");
        evalCase.setExpectedKeywords("资料不足");
        evalCase.setExpectedSource("");
        return evalCase;
    }

    private RagEvalCase baseCase(Long id, String question, boolean noAnswer) {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.setId(id);
        evalCase.setUserId(1L);
        evalCase.setKbId(10L);
        evalCase.setQuestion(question);
        evalCase.setExpectNoAnswer(noAnswer);
        evalCase.setEnabled(true);
        return evalCase;
    }

    private RagAskResponse answerWithMixedCitations() {
        return response(
                "报销时限为费用发生后30 个自然日内提交。",
                List.of(
                        citation(201L, "expense-policy.md"),
                        citation(202L, "incident-runbook.md")
                )
        );
    }

    private RagAskResponse insufficientAnswer() {
        return response("当前资料不足以确认。", List.of());
    }

    private RagAskResponse response(String answer, List<RagCitationResponse> citations) {
        return new RagAskResponse(
                10L,
                20L,
                "question",
                answer,
                citations.size(),
                citations,
                "STRICT",
                "MODEL",
                "test-model",
                10L,
                false,
                false
        );
    }

    private RagCitationResponse citation(Long chunkId, String documentName) {
        return new RagCitationResponse(chunkId, chunkId + 100, documentName, 0, "content", 0.9, 0.9, 0.5);
    }

    private RagEvalServiceImpl service() {
        return new RagEvalServiceImpl(caseMapper, runMapper, permissionService, ragService);
    }
}
