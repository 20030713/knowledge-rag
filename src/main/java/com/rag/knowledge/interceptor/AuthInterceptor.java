package com.rag.knowledge.interceptor;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.JwtService;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthInterceptor(JwtService jwtService, UserMapper userMapper, TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            if (tokenBlacklistService.blacklisted(token)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "login status expired");
            }
            LoginUser loginUser = jwtService.parseToken(token);
            User user = userMapper.selectById(loginUser.userId());
            if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "account is disabled or deleted");
            }
            UserContext.set(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
            return true;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "login status expired");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
