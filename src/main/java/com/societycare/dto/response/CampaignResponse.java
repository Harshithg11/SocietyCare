package com.societycare.dto.response;

import com.societycare.entity.BookingCampaign;
import com.societycare.enums.CampaignStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CampaignResponse(
        Long id,
        String campaignTitle,
        String description,
        Long serviceOfferingId,
        String serviceName,
        Long societyId,
        String societyName,
        Integer minimumParticipants,
        Integer maximumParticipants,
        Integer currentParticipants,
        BigDecimal regularPrice,
        BigDecimal discountPercentage,
        BigDecimal discountedPrice,
        LocalDate startDate,
        LocalDate endDate,
        CampaignStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CampaignResponse from(BookingCampaign c) {
        return new CampaignResponse(
                c.getId(),
                c.getCampaignTitle(),
                c.getDescription(),
                c.getServiceOffering().getId(),
                c.getServiceOffering().getServiceName(),
                c.getSociety().getId(),
                c.getSociety().getSocietyName(),
                c.getMinimumParticipants(),
                c.getMaximumParticipants(),
                c.getCurrentParticipants(),
                c.getRegularPrice(),
                c.getDiscountPercentage(),
                c.getDiscountedPrice(),
                c.getStartDate(),
                c.getEndDate(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
