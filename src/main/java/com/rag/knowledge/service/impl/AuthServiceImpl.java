package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.domain.entity.UserLoginLog;
import com.rag.knowledge.domain.enums.UserRole;
import com.rag.knowledge.dto.auth.ChangePasswordRequest;
import com.rag.knowledge.dto.auth.CurrentUserResponse;
import com.rag.knowledge.dto.auth.LoginRequest;
import com.rag.knowledge.dto.auth.LoginResponse;
import com.rag.knowledge.dto.auth.RegisterRequest;
import com.rag.knowledge.dto.auth.UserLoginLogResponse;
import com.rag.knowledge.dto.auth.UserProfileResponse;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.UserLoginLogMapper;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.JwtService;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.AuthService;
import com.rag.knowledge.service.TokenBlacklistService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthServiceImpl(
            UserMapper userMapper,
            UserLoginLogMapper loginLogMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService
    ) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER.name());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            saveLoginLog(user == null ? null : user.getId(), ipAddress, userAgent, false, "BAD_CREDENTIALS");
            throw new BusinessException(ErrorCode.BAD_REQUEST, "username or password is incorrect");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            saveLoginLog(user.getId(), ipAddress, userAgent, false, "ACCOUNT_DISABLED");
            throw new BusinessException(ErrorCode.FORBIDDEN, "account is disabled");
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        String token = jwtService.createToken(loginUser);
        saveLoginLog(user.getId(), ipAddress, userAgent, true, "LOGIN_SUCCESS");
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public CurrentUserResponse currentUser() {
        LoginUser loginUser = UserContext.getRequired();
        return new CurrentUserResponse(loginUser.userId(), loginUser.username(), loginUser.role());
    }

    @Override
    public UserProfileResponse profile() {
        LoginUser loginUser = UserContext.getRequired();
        User user = userMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                recentLoginLogs(user.getId())
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        LoginUser loginUser = UserContext.getRequired();
        User user = userMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "old password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "new password must be different");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    private void saveLoginLog(Long userId, String ipAddress, String userAgent, boolean success, String message) {
        UserLoginLog log = new UserLoginLog();
        log.setUserId(userId);
        log.setIpAddress(limit(ipAddress, 64));
        log.setUserAgent(limit(userAgent, 512));
        log.setSuccess(success);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    private List<UserLoginLogResponse> recentLoginLogs(Long userId) {
        return loginLogMapper.selectList(new LambdaQueryWrapper<UserLoginLog>()
                        .eq(UserLoginLog::getUserId, userId)
                        .orderByDesc(UserLoginLog::getCreatedAt)
                        .last("LIMIT 5"))
                .stream()
                .map(log -> new UserLoginLogResponse(
                        log.getId(),
                        log.getIpAddress(),
                        log.getUserAgent(),
                        log.getSuccess(),
                        log.getMessage(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
