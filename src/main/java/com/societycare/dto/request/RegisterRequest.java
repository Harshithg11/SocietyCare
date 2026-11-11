package com.societycare.dto.request;

import com.societycare.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Public registration. Role may be CITIZEN (default) or SERVICE_PROVIDER. ADMIN is rejected.")
public record RegisterRequest(
        @Schema(example = "Priya Sharma")
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
        String fullName,

        @Schema(example = "priya@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Schema(example = "Priya@1234", description = "8-72 characters with at least one letter and one digit")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String password,

        @Schema(example = "9876543210", description = "10-digit Indian mobile number")
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit mobile number")
        String phoneNumber,

        @Schema(example = "1")
        @NotNull(message = "Society id is required")
        @Positive(message = "Society id must be positive")
        Long societyId,

        @Schema(example = "CITIZEN", description = "CITIZEN (default) or SERVICE_PROVIDER")
        Role role
) {
}
