package com.rag.knowledge.controller;

import com.rag.knowledge.common.ApiResponse;
import com.rag.knowledge.dto.admin.AdminLoginLogResponse;
import com.rag.knowledge.dto.admin.AdminOperationLogResponse;
import com.rag.knowledge.dto.admin.AdminResetPasswordRequest;
import com.rag.knowledge.dto.admin.AdminUserOverviewResponse;
import com.rag.knowledge.dto.admin.AdminUserResponse;
import com.rag.knowledge.dto.admin.AdminUserUpdateRequest;
import com.rag.knowledge.service.AdminService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users/overview")
    public ApiResponse<AdminUserOverviewResponse> overview() {
        return ApiResponse.success(adminService.overview());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return ApiResponse.success(adminService.listUsers(keyword, limit));
    }

    @PutMapping("/users/{userId}")
    public ApiResponse<AdminUserResponse> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUserUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(adminService.updateUser(
                userId,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    @PutMapping("/users/{userId}/password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        adminService.resetPassword(
                userId,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
        return ApiResponse.success();
    }

    @GetMapping("/login-logs")
    public ApiResponse<List<AdminLoginLogResponse>> loginLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false, defaultValue = "80") Integer limit
    ) {
        return ApiResponse.success(adminService.listLoginLogs(userId, success, limit));
    }

    @GetMapping("/operation-logs")
    public ApiResponse<List<AdminOperationLogResponse>> operationLogs(
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "80") Integer limit
    ) {
        return ApiResponse.success(adminService.listOperationLogs(targetUserId, action, limit));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
