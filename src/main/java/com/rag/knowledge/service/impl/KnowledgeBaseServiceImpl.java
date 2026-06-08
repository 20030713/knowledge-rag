package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.KnowledgeBase;
import com.rag.knowledge.domain.enums.KnowledgeBaseVisibility;
import com.rag.knowledge.dto.kb.KnowledgeBaseCreateRequest;
import com.rag.knowledge.dto.kb.KnowledgeBaseResponse;
import com.rag.knowledge.dto.kb.KnowledgeBaseUpdateRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.KnowledgeBaseMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.KnowledgeBaseService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse create(KnowledgeBaseCreateRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        ensureNameAvailable(loginUser.userId(), request.name(), null);

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(loginUser.userId());
        knowledgeBase.setName(request.name());
        knowledgeBase.setDescription(request.description());
        knowledgeBase.setVisibility(KnowledgeBaseVisibility.PRIVATE.name());
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);
        return toResponse(knowledgeBase);
    }

    @Override
    public List<KnowledgeBaseResponse> listMine() {
        LoginUser loginUser = UserContext.getRequired();
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, loginUser.userId())
                        .orderByDesc(KnowledgeBase::getUpdatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public KnowledgeBaseResponse getMine(Long id) {
        return toResponse(getOwnedKnowledgeBase(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse update(Long id, KnowledgeBaseUpdateRequest request) {
        KnowledgeBase knowledgeBase = getOwnedKnowledgeBase(id);
        ensureNameAvailable(knowledgeBase.getUserId(), request.name(), id);

        knowledgeBase.setName(request.name());
        knowledgeBase.setDescription(request.description());
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        return toResponse(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBase knowledgeBase = getOwnedKnowledgeBase(id);
        knowledgeBaseMapper.deleteById(knowledgeBase.getId());
    }

    private KnowledgeBase getOwnedKnowledgeBase(Long id) {
        LoginUser loginUser = UserContext.getRequired();
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getUserId, loginUser.userId())
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return knowledgeBase;
    }

    private void ensureNameAvailable(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getName, name);
        if (excludeId != null) {
            wrapper.ne(KnowledgeBase::getId, excludeId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识库名称已存在");
        }
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getVisibility(),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt()
        );
    }
}
