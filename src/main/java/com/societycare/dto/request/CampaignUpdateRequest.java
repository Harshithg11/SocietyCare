package com.societycare.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Update a PENDING campaign. Service offering and society cannot be changed; "
        + "the participant count is never client-controlled.")
public record CampaignUpdateRequest(
        @NotBlank(message = "Campaign title is required")
        @Size(max = 150, message = "Campaign title must be at most 150 characters")
        String campaignTitle,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "Minimum participants is required")
        @Min(value = 1, message = "Minimum participants must be at least 1")
        Integer minimumParticipants,

        @Min(value = 1, message = "Maximum participants must be at least 1")
        Integer maximumParticipants,

        @NotNull(message = "Regular price is required")
        @Positive(message = "Regular price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Regular price must have at most 2 decimal places")
        BigDecimal regularPrice,

        @NotNull(message = "Discount percentage is required")
        @DecimalMin(value = "0.01", message = "Discount percentage must be at least 0.01")
        @DecimalMax(value = "99.99", message = "Discount percentage must be less than 100")
        @Digits(integer = 2, fraction = 2, message = "Discount percentage must have at most 2 decimal places")
        BigDecimal discountPercentage,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate
) {
}
