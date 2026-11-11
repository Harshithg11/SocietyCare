package com.societycare.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Create a booking campaign. discountedPrice is calculated by the server.")
public record CampaignRequest(
        @Schema(example = "AC Service Group Offer - December")
        @NotBlank(message = "Campaign title is required")
        @Size(max = 150, message = "Campaign title must be at most 150 characters")
        String campaignTitle,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Schema(example = "1")
        @NotNull(message = "Service offering id is required")
        @Positive(message = "Service offering id must be positive")
        Long serviceOfferingId,

        @Schema(example = "1")
        @NotNull(message = "Society id is required")
        @Positive(message = "Society id must be positive")
        Long societyId,

        @Schema(example = "10")
        @NotNull(message = "Minimum participants is required")
        @Min(value = 1, message = "Minimum participants must be at least 1")
        Integer minimumParticipants,

        @Schema(example = "25", description = "Optional. Must be >= minimumParticipants")
        @Min(value = 1, message = "Maximum participants must be at least 1")
        Integer maximumParticipants,

        @Schema(example = "1000.00")
        @NotNull(message = "Regular price is required")
        @Positive(message = "Regular price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Regular price must have at most 2 decimal places")
        BigDecimal regularPrice,

        @Schema(example = "20.00", description = "Percentage between 0.01 and 99.99")
        @NotNull(message = "Discount percentage is required")
        @DecimalMin(value = "0.01", message = "Discount percentage must be at least 0.01")
        @DecimalMax(value = "99.99", message = "Discount percentage must be less than 100")
        @Digits(integer = 2, fraction = 2, message = "Discount percentage must have at most 2 decimal places")
        BigDecimal discountPercentage,

        @Schema(example = "2026-12-01")
        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date cannot be in the past")
        LocalDate startDate,

        @Schema(example = "2026-12-15")
        @NotNull(message = "End date is required")
        @FutureOrPresent(message = "End date cannot be in the past")
        LocalDate endDate
) {
}
