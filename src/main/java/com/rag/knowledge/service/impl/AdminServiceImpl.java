package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.AdminOperationLog;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.domain.entity.UserLoginLog;
import com.rag.knowledge.domain.enums.UserRole;
import com.rag.knowledge.dto.admin.AdminLoginLogResponse;
import com.rag.knowledge.dto.admin.AdminOperationLogResponse;
import com.rag.knowledge.dto.admin.AdminResetPasswordRequest;
import com.rag.knowledge.dto.admin.AdminUserOverviewResponse;
import com.rag.knowledge.dto.admin.AdminUserResponse;
import com.rag.knowledge.dto.admin.AdminUserUpdateRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.AdminOperationLogMapper;
import com.rag.knowledge.repository.UserLoginLogMapper;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.AdminService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final UserLoginLogMapper loginLogMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminServiceImpl(
            UserMapper userMapper,
            UserLoginLogMapper loginLogMapper,
            AdminOperationLogMapper operationLogMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AdminUserOverviewResponse overview() {
        requireAdmin();
        Long total = userMapper.selectCount(new LambdaQueryWrapper<>());
        Long enabled = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ne(User::getEnabled, false));
        Long disabled = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEnabled, false));
        Long admins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.ADMIN.name()));
        return new AdminUserOverviewResponse(total, enabled, disabled, admins);
    }

    @Override
    public List<AdminUserResponse> listUsers(String keyword, Integer limit) {
        requireAdmin();
        int safeLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 200));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt)
                .last("LIMIT " + safeLimit);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword.trim());
        }
        List<User> users = userMapper.selectList(wrapper);
        Map<Long, UserLoginLog> latestLogs = latestLogs(users.stream().map(User::getId).toList());
        return users.stream()
                .map(user -> toUserResponse(user, latestLogs.get(user.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserResponse updateUser(Long userId, AdminUserUpdateRequest request, String ipAddress, String userAgent) {
        LoginUser admin = requireAdmin();
        User user = requireUser(userId);
        String oldRole = user.getRole();
        boolean oldEnabled = !Boolean.FALSE.equals(user.getEnabled());
        String nextRole = parseRole(request.role(), user.getRole());
        Boolean nextEnabled = request.enabled() == null ? user.getEnabled() : request.enabled();
        boolean nextEnabledValue = !Boolean.FALSE.equals(nextEnabled);

        if (Objects.equals(admin.userId(), user.getId()) && Boolean.FALSE.equals(nextEnabled)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cannot disable current admin");
        }
        if (UserRole.ADMIN.name().equals(user.getRole())
                && (!UserRole.ADMIN.name().equals(nextRole) || Boolean.FALSE.equals(nextEnabled))
                && countEnabledAdminsExcluding(user.getId()) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "at least one enabled admin is required");
        }

        user.setRole(nextRole);
        user.setEnabled(nextEnabled);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        if (!Objects.equals(oldRole, nextRole)) {
            saveOperation(admin.userId(), user.getId(), "CHANGE_ROLE", "SUCCESS",
                    "role " + oldRole + " -> " + nextRole, ipAddress, userAgent);
        }
        if (oldEnabled != nextEnabledValue) {
            saveOperation(admin.userId(), user.getId(), nextEnabledValue ? "ENABLE_USER" : "DISABLE_USER", "SUCCESS",
                    "enabled " + oldEnabled + " -> " + nextEnabledValue, ipAddress, userAgent);
        }
        return toUserResponse(userMapper.selectById(userId), latestLog(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, AdminResetPasswordRequest request, String ipAddress, String userAgent) {
        LoginUser admin = requireAdmin();
        User user = requireUser(userId);
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        saveOperation(admin.userId(), user.getId(), "RESET_PASSWORD", "SUCCESS",
                "password reset by admin", ipAddress, userAgent);
    }

    @Override
    public List<AdminLoginLogResponse> listLoginLogs(Long userId, Boolean success, Integer limit) {
        requireAdmin();
        int safeLimit = Math.max(1, Math.min(limit == null ? 80 : limit, 200));
        LambdaQueryWrapper<UserLoginLog> wrapper = new LambdaQueryWrapper<UserLoginLog>()
                .orderByDesc(UserLoginLog::getCreatedAt)
                .last("LIMIT " + safeLimit);
        if (userId != null) {
            wrapper.eq(UserLoginLog::getUserId, userId);
        }
        if (success != null) {
            wrapper.eq(UserLoginLog::getSuccess, success);
        }
        List<UserLoginLog> logs = loginLogMapper.selectList(wrapper);
        Map<Long, String> usernames = usernames(logs.stream()
                .map(UserLoginLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return logs.stream()
                .map(log -> new AdminLoginLogResponse(
                        log.getId(),
                        log.getUserId(),
                        log.getUserId() == null ? null : usernames.get(log.getUserId()),
                        log.getIpAddress(),
                        log.getUserAgent(),
                        log.getSuccess(),
                        log.getMessage(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<AdminOperationLogResponse> listOperationLogs(Long targetUserId, String action, Integer limit) {
        requireAdmin();
        int safeLimit = Math.max(1, Math.min(limit == null ? 80 : limit, 200));
        LambdaQueryWrapper<AdminOperationLog> wrapper = new LambdaQueryWrapper<AdminOperationLog>()
                .orderByDesc(AdminOperationLog::getCreatedAt)
                .last("LIMIT " + safeLimit);
        if (targetUserId != null) {
            wrapper.eq(AdminOperationLog::getTargetUserId, targetUserId);
        }
        if (action != null && !action.isBlank() && !"ALL".equalsIgnoreCase(action)) {
            wrapper.eq(AdminOperationLog::getAction, action.trim().toUpperCase());
        }
        List<AdminOperationLog> logs = operationLogMapper.selectList(wrapper);
        List<Long> userIds = logs.stream()
                .flatMap(log -> java.util.stream.Stream.of(log.getAdminUserId(), log.getTargetUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> usernames = usernames(userIds);
        return logs.stream()
                .map(log -> new AdminOperationLogResponse(
                        log.getId(),
                        log.getAdminUserId(),
                        usernames.get(log.getAdminUserId()),
                        log.getTargetUserId(),
                        log.getTargetUserId() == null ? null : usernames.get(log.getTargetUserId()),
                        log.getAction(),
                        log.getResult(),
                        log.getDetail(),
                        log.getIpAddress(),
                        log.getUserAgent(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    private LoginUser requireAdmin() {
        LoginUser loginUser = UserContext.getRequired();
        if (!UserRole.ADMIN.name().equals(loginUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "admin permission required");
        }
        return loginUser;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return user;
    }

    private String parseRole(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "role must be USER or ADMIN");
        }
    }

    private long countEnabledAdminsExcluding(Long userId) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.ADMIN.name())
                .ne(User::getEnabled, false)
                .ne(User::getId, userId));
    }

    private AdminUserResponse toUserResponse(User user, UserLoginLog latestLog) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                !Boolean.FALSE.equals(user.getEnabled()),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                latestLog == null ? null : latestLog.getCreatedAt(),
                latestLog == null ? null : latestLog.getSuccess()
        );
    }

    private UserLoginLog latestLog(Long userId) {
        return loginLogMapper.selectOne(new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .orderByDesc(UserLoginLog::getCreatedAt)
                .last("LIMIT 1"));
    }

    private Map<Long, UserLoginLog> latestLogs(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userIds.stream()
                .map(this::latestLog)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserLoginLog::getUserId, log -> log, (left, right) -> left));
    }

    private Map<Long, String> usernames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds))
                .stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private void saveOperation(
            Long adminUserId,
            Long targetUserId,
            String action,
            String result,
            String detail,
            String ipAddress,
            String userAgent
    ) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminUserId(adminUserId);
        log.setTargetUserId(targetUserId);
        log.setAction(limit(action, 64));
        log.setResult(limit(result, 32));
        log.setDetail(limit(detail, 1000));
        log.setIpAddress(limit(ipAddress, 64));
        log.setUserAgent(limit(userAgent, 512));
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
