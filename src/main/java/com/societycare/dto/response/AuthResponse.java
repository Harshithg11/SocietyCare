package com.societycare.dto.response;

import com.societycare.enums.Role;

import java.time.Instant;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        Long userId,
        String fullName,
        String email,
        Role role
) {
}
