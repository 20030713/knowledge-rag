package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.user.UserPreferenceResponse;
import com.rag.knowledge.dto.user.UserPreferenceUpdateRequest;
import com.rag.knowledge.service.UserPreferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    public UserPreferenceController(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping("/rag")
    public ApiResponse<UserPreferenceResponse> getRagPreference() {
        return ApiResponse.success(userPreferenceService.getCurrent());
    }

    @PutMapping("/rag")
    public ApiResponse<UserPreferenceResponse> updateRagPreference(@RequestBody UserPreferenceUpdateRequest request) {
        return ApiResponse.success(userPreferenceService.updateCurrent(request));
    }
}
