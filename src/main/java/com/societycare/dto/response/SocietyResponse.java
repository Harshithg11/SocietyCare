package com.societycare.dto.response;

import com.societycare.entity.Society;

import java.time.LocalDateTime;

public record SocietyResponse(
        Long id,
        String societyName,
        String address,
        String city,
        String state,
        String pincode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SocietyResponse from(Society s) {
        return new SocietyResponse(s.getId(), s.getSocietyName(), s.getAddress(), s.getCity(), s.getState(),
                s.getPincode(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
