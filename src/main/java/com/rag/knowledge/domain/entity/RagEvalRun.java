package com.rag.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rag_eval_run")
public class RagEvalRun {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long kbId;
    private Long caseId;
    private String question;
    private String answer;
    private Integer hitCount;
    private Double keywordScore;
    private Boolean noAnswerCase;
    private Boolean retrievalHit;
    private Double reciprocalRank;
    private Double citationPrecision;
    private Boolean abstentionCorrect;
    private Boolean passed;
    private Long latencyMs;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public Double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(Double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public Boolean getNoAnswerCase() {
        return noAnswerCase;
    }

    public void setNoAnswerCase(Boolean noAnswerCase) {
        this.noAnswerCase = noAnswerCase;
    }

    public Boolean getRetrievalHit() {
        return retrievalHit;
    }

    public void setRetrievalHit(Boolean retrievalHit) {
        this.retrievalHit = retrievalHit;
    }

    public Double getReciprocalRank() {
        return reciprocalRank;
    }

    public void setReciprocalRank(Double reciprocalRank) {
        this.reciprocalRank = reciprocalRank;
    }

    public Double getCitationPrecision() {
        return citationPrecision;
    }

    public void setCitationPrecision(Double citationPrecision) {
        this.citationPrecision = citationPrecision;
    }

    public Boolean getAbstentionCorrect() {
        return abstentionCorrect;
    }

    public void setAbstentionCorrect(Boolean abstentionCorrect) {
        this.abstentionCorrect = abstentionCorrect;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
