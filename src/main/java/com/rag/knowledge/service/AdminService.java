package com.rag.knowledge.service;

import com.rag.knowledge.dto.admin.AdminLoginLogResponse;
import com.rag.knowledge.dto.admin.AdminOperationLogResponse;
import com.rag.knowledge.dto.admin.AdminResetPasswordRequest;
import com.rag.knowledge.dto.admin.AdminUserOverviewResponse;
import com.rag.knowledge.dto.admin.AdminUserResponse;
import com.rag.knowledge.dto.admin.AdminUserUpdateRequest;
import java.util.List;

public interface AdminService {

    AdminUserOverviewResponse overview();

    List<AdminUserResponse> listUsers(String keyword, Integer limit);

    AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request, String ipAddress, String userAgent);

    void resetPassword(Long userId, AdminResetPasswordRequest request, String ipAddress, String userAgent);

    List<AdminLoginLogResponse> listLoginLogs(Long userId, Boolean success, Integer limit);

    List<AdminOperationLogResponse> listOperationLogs(Long targetUserId, String action, Integer limit);
}
