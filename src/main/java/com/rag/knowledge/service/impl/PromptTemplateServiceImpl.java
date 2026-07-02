package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.PromptTemplate;
import com.rag.knowledge.dto.rag.PromptTemplateRequest;
import com.rag.knowledge.dto.rag.PromptTemplateResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.rag.AnswerStyle;
import com.rag.knowledge.repository.PromptTemplateMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBasePermissionService;
import com.rag.knowledge.service.PromptTemplateService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final KnowledgeBasePermissionService permissionService;

    public PromptTemplateServiceImpl(
            PromptTemplateMapper promptTemplateMapper,
            KnowledgeBasePermissionService permissionService
    ) {
        this.promptTemplateMapper = promptTemplateMapper;
        this.permissionService = permissionService;
    }

    @Override
    public List<PromptTemplateResponse> list(Long kbId) {
        permissionService.requireRead(kbId);
        return promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getKbId, kbId)
                        .orderByDesc(PromptTemplate::getEnabled)
                        .orderByDesc(PromptTemplate::getUpdatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateResponse create(Long kbId, PromptTemplateRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        permissionService.requireAdmin(kbId);
        PromptTemplate template = new PromptTemplate();
        template.setUserId(loginUser.userId());
        template.setKbId(kbId);
        applyRequest(template, request);
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        if (Boolean.TRUE.equals(template.getEnabled())) {
            disableOtherEnabledTemplates(null, kbId, template.getAnswerStyle());
        }
        promptTemplateMapper.insert(template);
        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateResponse update(Long id, PromptTemplateRequest request) {
        PromptTemplate template = getEditableTemplate(id);
        applyRequest(template, request);
        template.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(template.getEnabled())) {
            disableOtherEnabledTemplates(template.getId(), template.getKbId(), template.getAnswerStyle());
        }
        promptTemplateMapper.updateById(template);
        return toResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PromptTemplate template = getEditableTemplate(id);
        promptTemplateMapper.deleteById(template.getId());
    }

    @Override
    public Optional<PromptTemplate> activeTemplate(Long kbId, AnswerStyle style) {
        permissionService.requireRead(kbId);
        AnswerStyle safeStyle = style == null ? AnswerStyle.STRICT : style;
        PromptTemplate template = promptTemplateMapper.selectOne(new LambdaQueryWrapper<PromptTemplate>()
                .eq(PromptTemplate::getKbId, kbId)
                .eq(PromptTemplate::getAnswerStyle, safeStyle.name())
                .eq(PromptTemplate::getEnabled, true)
                .orderByDesc(PromptTemplate::getUpdatedAt)
                .last("LIMIT 1"));
        return Optional.ofNullable(template);
    }

    private PromptTemplate getEditableTemplate(Long id) {
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Prompt 模板不存在");
        }
        permissionService.requireAdmin(template.getKbId());
        return template;
    }

    private void applyRequest(PromptTemplate template, PromptTemplateRequest request) {
        template.setName(normalizeName(request.name()));
        template.setAnswerStyle(AnswerStyle.from(request.answerStyle()).name());
        template.setSystemPrompt(normalizePrompt(request.systemPrompt()));
        template.setEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    private void disableOtherEnabledTemplates(Long currentId, Long kbId, String answerStyle) {
        LambdaUpdateWrapper<PromptTemplate> wrapper = new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getKbId, kbId)
                .eq(PromptTemplate::getAnswerStyle, answerStyle)
                .set(PromptTemplate::getEnabled, false)
                .set(PromptTemplate::getUpdatedAt, LocalDateTime.now());
        if (currentId != null) {
            wrapper.ne(PromptTemplate::getId, currentId);
        }
        promptTemplateMapper.update(wrapper);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private String normalizePrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        if (normalized.length() < 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Prompt 模板内容过短");
        }
        return normalized;
    }

    private PromptTemplateResponse toResponse(PromptTemplate template) {
        return new PromptTemplateResponse(
                template.getId(),
                template.getKbId(),
                template.getName(),
                template.getAnswerStyle(),
                template.getSystemPrompt(),
                template.getEnabled(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
