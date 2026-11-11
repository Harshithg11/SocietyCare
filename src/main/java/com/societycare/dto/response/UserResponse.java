package com.societycare.dto.response;

import com.societycare.entity.User;
import com.societycare.enums.Role;

import java.time.LocalDateTime;

/** Deliberately has no password field. */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        Role role,
        Long societyId,
        String societyName,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getSociety() != null ? user.getSociety().getId() : null,
                user.getSociety() != null ? user.getSociety().getSocietyName() : null,
                user.getCreatedAt());
    }
}
