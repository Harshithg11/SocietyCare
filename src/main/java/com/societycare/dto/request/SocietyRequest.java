package com.societycare.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SocietyRequest(
        @Schema(example = "Green Valley Residency")
        @NotBlank(message = "Society name is required")
        @Size(max = 150, message = "Society name must be at most 150 characters")
        String societyName,

        @Schema(example = "12, Outer Ring Road, Marathahalli")
        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @Schema(example = "Bengaluru")
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Schema(example = "Karnataka")
        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must be at most 100 characters")
        String state,

        @Schema(example = "560037", description = "6-digit pincode")
        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9]\\d{5}$", message = "Pincode must be a valid 6-digit number")
        String pincode
) {
}
