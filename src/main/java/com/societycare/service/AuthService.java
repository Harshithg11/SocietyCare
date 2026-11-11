package com.societycare.service;

import com.societycare.dto.request.LoginRequest;
import com.societycare.dto.request.RegisterRequest;
import com.societycare.dto.response.AuthResponse;
import com.societycare.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
