package com.societycare.dto.request;

import com.societycare.enums.ServiceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Create or update a service offering. societyId/providerId are only used by ADMIN on create; "
        + "a SERVICE_PROVIDER always creates offerings for their own society under their own account.")
public record ServiceOfferingRequest(
        @Schema(example = "AC Servicing")
        @NotBlank(message = "Service name is required")
        @Size(max = 150, message = "Service name must be at most 150 characters")
        String serviceName,

        @Schema(example = "Complete split/window AC service with gas pressure check")
        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Schema(example = "AC_SERVICING")
        @NotNull(message = "Category is required")
        ServiceCategory category,

        @Schema(example = "1000.00")
        @NotNull(message = "Regular price is required")
        @Positive(message = "Regular price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Regular price must have at most 2 decimal places")
        BigDecimal regularPrice,

        @Schema(description = "ADMIN only on create")
        Long societyId,

        @Schema(description = "ADMIN only on create: the SERVICE_PROVIDER who owns the offering")
        Long providerId,

        @Schema(example = "true", description = "Defaults to true when omitted")
        Boolean active
) {
}
