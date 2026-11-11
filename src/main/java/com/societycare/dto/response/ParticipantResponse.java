package com.societycare.dto.response;

import com.societycare.entity.Booking;

import java.time.LocalDateTime;

/** A resident who has an active booking in a campaign (visible to ADMIN and the owning provider). */
public record ParticipantResponse(
        Long bookingId,
        Long citizenId,
        String fullName,
        String email,
        String phoneNumber,
        LocalDateTime bookedAt
) {
    public static ParticipantResponse from(Booking b) {
        return new ParticipantResponse(b.getId(), b.getCitizen().getId(), b.getCitizen().getFullName(),
                b.getCitizen().getEmail(), b.getCitizen().getPhoneNumber(), b.getBookedAt());
    }
}
