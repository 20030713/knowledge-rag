package com.rag.knowledge.service;

import com.rag.knowledge.dto.auth.ChangePasswordRequest;
import com.rag.knowledge.dto.auth.CurrentUserResponse;
import com.rag.knowledge.dto.auth.LoginRequest;
import com.rag.knowledge.dto.auth.LoginResponse;
import com.rag.knowledge.dto.auth.RegisterRequest;
import com.rag.knowledge.dto.auth.UserProfileResponse;

public interface AuthService {

    CurrentUserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    CurrentUserResponse currentUser();

    UserProfileResponse profile();

    void changePassword(ChangePasswordRequest request);

    void logout(String token);
}
