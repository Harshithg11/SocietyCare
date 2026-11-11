package com.societycare.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.societycare.entity.Booking;
import com.societycare.entity.BookingCampaign;
import com.societycare.enums.BookingStatus;
import com.societycare.enums.CampaignStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Used for join, cancel and booking reads. 'message' is only present for join/cancel. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        Long bookingId,
        Long campaignId,
        String campaignTitle,
        String serviceName,
        String societyName,
        Long citizenId,
        String citizenName,
        BookingStatus bookingStatus,
        BigDecimal discountedPrice,
        Integer currentParticipants,
        Integer minimumParticipants,
        Integer maximumParticipants,
        CampaignStatus campaignStatus,
        LocalDateTime bookedAt,
        LocalDateTime cancelledAt,
        String message
) {
    public static BookingResponse from(Booking booking) {
        return from(booking, null);
    }

    public static BookingResponse from(Booking booking, String message) {
        BookingCampaign c = booking.getBookingCampaign();
        return new BookingResponse(
                booking.getId(),
                c.getId(),
                c.getCampaignTitle(),
                c.getServiceOffering().getServiceName(),
                c.getSociety().getSocietyName(),
                booking.getCitizen().getId(),
                booking.getCitizen().getFullName(),
                booking.getBookingStatus(),
                c.getDiscountedPrice(),
                c.getCurrentParticipants(),
                c.getMinimumParticipants(),
                c.getMaximumParticipants(),
                c.getStatus(),
                booking.getBookedAt(),
                booking.getCancelledAt(),
                message);
    }
}
