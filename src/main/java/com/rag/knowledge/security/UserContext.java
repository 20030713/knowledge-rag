package com.rag.knowledge.security;

import com.rag.knowledge.common.ErrorCode;
import com.rag.knowledge.exception.BusinessException;

public final class UserContext {

    private static final ThreadLocal<LoginUser> LOGIN_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        LOGIN_USER.set(loginUser);
    }

    public static LoginUser getRequired() {
        LoginUser loginUser = LOGIN_USER.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static void clear() {
        LOGIN_USER.remove();
    }
}
