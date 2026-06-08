package com.rag.knowledge.interceptor;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.security.JwtService;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            LoginUser loginUser = jwtService.parseToken(token);
            UserContext.set(loginUser);
            return true;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
