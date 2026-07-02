package com.rag.knowledge.interceptor;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.JwtService;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import com.rag.knowledge.service.TokenBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTests {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsDisabledUserEvenWithValidToken() {
        AuthInterceptor interceptor = new AuthInterceptor(jwtService, userMapper, tokenBlacklistService);
        when(tokenBlacklistService.blacklisted("token")).thenReturn(false);
        when(jwtService.parseToken("token")).thenReturn(new LoginUser(2L, "alice", "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice", "USER", false));

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("disabled");

        assertThat(UserContext.get()).isNull();
    }

    @Test
    void usesLatestUserRoleFromDatabase() {
        AuthInterceptor interceptor = new AuthInterceptor(jwtService, userMapper, tokenBlacklistService);
        when(tokenBlacklistService.blacklisted("token")).thenReturn(false);
        when(jwtService.parseToken("token")).thenReturn(new LoginUser(2L, "alice", "USER"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "alice", "ADMIN", true));

        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(UserContext.getRequired().role()).isEqualTo("ADMIN");
    }

    @Test
    void rejectsBlacklistedToken() {
        AuthInterceptor interceptor = new AuthInterceptor(jwtService, userMapper, tokenBlacklistService);
        when(tokenBlacklistService.blacklisted("token")).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(UserContext.get()).isNull();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private User user(Long id, String username, String role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }
}
