package com.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.domain.entity.AdminOperationLog;
import com.rag.knowledge.domain.entity.User;
import com.rag.knowledge.dto.admin.AdminResetPasswordRequest;
import com.rag.knowledge.dto.admin.AdminUserUpdateRequest;
import com.rag.knowledge.exception.BusinessException;
import com.rag.knowledge.repository.AdminOperationLogMapper;
import com.rag.knowledge.repository.UserLoginLogMapper;
import com.rag.knowledge.repository.UserMapper;
import com.rag.knowledge.security.LoginUser;
import com.rag.knowledge.security.UserContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTests {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserLoginLogMapper loginLogMapper;

    @Mock
    private AdminOperationLogMapper operationLogMapper;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsNonAdminAccess() {
        AdminServiceImpl service = service();
        UserContext.set(new LoginUser(1L, "user", "USER"));

        assertThatThrownBy(() -> service.listUsers("", 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void refusesToDemoteLastEnabledAdmin() {
        AdminServiceImpl service = service();
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        User admin = user(1L, "admin", "ADMIN", true);
        when(userMapper.selectById(1L)).thenReturn(admin);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.updateUser(
                1L,
                new AdminUserUpdateRequest("USER", true),
                "127.0.0.1",
                "JUnit"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one enabled admin");

        verify(userMapper, never()).updateById(any(User.class));
        verify(operationLogMapper, never()).insert(any(AdminOperationLog.class));
    }

    @Test
    void writesAuditLogsForRoleAndEnabledChanges() {
        AdminServiceImpl service = service();
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        User target = user(2L, "alice", "USER", true);
        when(userMapper.selectById(2L)).thenReturn(target);

        service.updateUser(
                2L,
                new AdminUserUpdateRequest("ADMIN", false),
                "10.0.0.8",
                "JUnit"
        );

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(operationLogMapper, org.mockito.Mockito.times(2)).insert(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(AdminOperationLog::getAction)
                .containsExactly("CHANGE_ROLE", "DISABLE_USER");
        assertThat(captor.getAllValues())
                .allSatisfy(log -> {
                    assertThat(log.getAdminUserId()).isEqualTo(1L);
                    assertThat(log.getTargetUserId()).isEqualTo(2L);
                    assertThat(log.getResult()).isEqualTo("SUCCESS");
                    assertThat(log.getIpAddress()).isEqualTo("10.0.0.8");
                    assertThat(log.getUserAgent()).isEqualTo("JUnit");
                });
    }

    @Test
    void writesAuditLogForPasswordResetWithoutStoringPassword() {
        AdminServiceImpl service = service();
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        User target = user(2L, "alice", "USER", true);
        when(userMapper.selectById(2L)).thenReturn(target);

        service.resetPassword(
                2L,
                new AdminResetPasswordRequest("new-password"),
                "10.0.0.9",
                "JUnit"
        );

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        AdminOperationLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("RESET_PASSWORD");
        assertThat(log.getDetail()).doesNotContain("new-password");
        assertThat(target.getPassword()).isNotEqualTo("old-password");
        assertThat(new BCryptPasswordEncoder().matches("new-password", target.getPassword())).isTrue();
    }

    private AdminServiceImpl service() {
        return new AdminServiceImpl(
                userMapper,
                loginLogMapper,
                operationLogMapper,
                new BCryptPasswordEncoder()
        );
    }

    private User user(Long id, String username, String role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(new BCryptPasswordEncoder().encode("old-password"));
        user.setRole(role);
        user.setEnabled(enabled);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
