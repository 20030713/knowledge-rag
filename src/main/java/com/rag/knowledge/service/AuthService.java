package com.rag.knowledge.service;

import com.rag.knowledge.dto.auth.CurrentUserResponse;
import com.rag.knowledge.dto.auth.LoginRequest;
import com.rag.knowledge.dto.auth.LoginResponse;
import com.rag.knowledge.dto.auth.RegisterRequest;

public interface AuthService {

    CurrentUserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    CurrentUserResponse currentUser();
}
