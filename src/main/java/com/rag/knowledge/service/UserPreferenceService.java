package com.rag.knowledge.service;

import com.rag.knowledge.domain.entity.UserPreference;
import com.rag.knowledge.dto.user.UserPreferenceResponse;
import com.rag.knowledge.dto.user.UserPreferenceUpdateRequest;

public interface UserPreferenceService {

    UserPreferenceResponse getCurrent();

    UserPreferenceResponse updateCurrent(UserPreferenceUpdateRequest request);

    UserPreference getOrCreate(Long userId);
}
