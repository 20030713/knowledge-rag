package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.domain.enums.UserRole;
import com.rag.knowledge.dto.auth.CurrentUserResponse;
import com.rag.knowledge.dto.auth.LoginRequest;
import com.rag.knowledge.dto.auth.LoginResponse;
import com.rag.knowledge.dto.auth.RegisterRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.JwtService;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.AuthService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse register(RegisterRequest request) {
        if (findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER.name());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        String token = jwtService.createToken(loginUser);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public CurrentUserResponse currentUser() {
        LoginUser loginUser = UserContext.getRequired();
        return new CurrentUserResponse(loginUser.userId(), loginUser.username(), loginUser.role());
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }
}
