package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.config.VectorSearchProperties;
import com.rag.knowledge.domain.entity.UserPreference;
import com.rag.knowledge.dto.user.UserPreferenceResponse;
import com.rag.knowledge.dto.user.UserPreferenceUpdateRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.rag.AnswerStyle;
import com.rag.knowledge.repository.UserPreferenceMapper;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.UserPreferenceService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;
    private final VectorSearchProperties vectorSearchProperties;

    public UserPreferenceServiceImpl(
            UserPreferenceMapper userPreferenceMapper,
            VectorSearchProperties vectorSearchProperties
    ) {
        this.userPreferenceMapper = userPreferenceMapper;
        this.vectorSearchProperties = vectorSearchProperties;
    }

    @Override
    public UserPreferenceResponse getCurrent() {
        return toResponse(getOrCreate(UserContext.getRequired().userId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceResponse updateCurrent(UserPreferenceUpdateRequest request) {
        Long userId = UserContext.getRequired().userId();
        UserPreference existing = getOrCreate(userId);
        UserPreference normalized = normalize(userId, request);
        normalized.setId(existing.getId());
        normalized.setCreatedAt(existing.getCreatedAt());
        normalized.setUpdatedAt(LocalDateTime.now());

        userPreferenceMapper.update(normalized, new LambdaUpdateWrapper<UserPreference>()
                .eq(UserPreference::getId, existing.getId())
                .eq(UserPreference::getUserId, userId));
        return toResponse(normalized);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreference getOrCreate(Long userId) {
        UserPreference preference = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId)
                .last("LIMIT 1"));
        if (preference != null) {
            return fillDefaults(preference);
        }

        UserPreference created = defaultPreference(userId);
        userPreferenceMapper.insert(created);
        return created;
    }

    private UserPreference normalize(Long userId, UserPreferenceUpdateRequest request) {
        if (request == null) {
            return defaultPreference(userId);
        }
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setDefaultAnswerStyle(AnswerStyle.from(request.defaultAnswerStyle()).name());
        preference.setDefaultTopK(clamp(request.defaultTopK(), 1, 20, vectorSearchProperties.safeTopK()));
        preference.setVectorWeight(clampWeight(request.vectorWeight(), vectorSearchProperties.getVectorWeight()));
        preference.setKeywordWeight(clampWeight(request.keywordWeight(), vectorSearchProperties.getKeywordWeight()));
        if (preference.getVectorWeight() + preference.getKeywordWeight() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索权重不能同时为 0");
        }
        preference.setEnableModel(request.enableModel() == null || request.enableModel());
        preference.setEnableCache(request.enableCache() == null || request.enableCache());
        return preference;
    }

    private UserPreference fillDefaults(UserPreference preference) {
        if (preference.getDefaultAnswerStyle() == null) {
            preference.setDefaultAnswerStyle(AnswerStyle.STRICT.name());
        }
        if (preference.getDefaultTopK() == null) {
            preference.setDefaultTopK(vectorSearchProperties.safeTopK());
        }
        if (preference.getVectorWeight() == null) {
            preference.setVectorWeight(vectorSearchProperties.getVectorWeight());
        }
        if (preference.getKeywordWeight() == null) {
            preference.setKeywordWeight(vectorSearchProperties.getKeywordWeight());
        }
        if (preference.getEnableModel() == null) {
            preference.setEnableModel(true);
        }
        if (preference.getEnableCache() == null) {
            preference.setEnableCache(true);
        }
        return preference;
    }

    private UserPreference defaultPreference(Long userId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setDefaultAnswerStyle(AnswerStyle.STRICT.name());
        preference.setDefaultTopK(vectorSearchProperties.safeTopK());
        preference.setVectorWeight(vectorSearchProperties.getVectorWeight());
        preference.setKeywordWeight(vectorSearchProperties.getKeywordWeight());
        preference.setEnableModel(true);
        preference.setEnableCache(true);
        LocalDateTime now = LocalDateTime.now();
        preference.setCreatedAt(now);
        preference.setUpdatedAt(now);
        return preference;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(value, max));
    }

    private double clampWeight(Double value, double fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(0, Math.min(value, 1));
    }

    private UserPreferenceResponse toResponse(UserPreference preference) {
        UserPreference filled = fillDefaults(preference);
        return new UserPreferenceResponse(
                filled.getDefaultAnswerStyle(),
                filled.getDefaultTopK(),
                filled.getVectorWeight(),
                filled.getKeywordWeight(),
                filled.getEnableModel(),
                filled.getEnableCache()
        );
    }
}
