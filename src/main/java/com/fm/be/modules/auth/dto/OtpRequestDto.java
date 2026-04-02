package com.fm.be.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating OTP login (mobile app flow).
 * Sends a 6-digit OTP to the provided phone number via SMS gateway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRequestDto {

    /** Phone number in E.164 format (e.g., +919876543210) */
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phone;

    /** Optional device identifier for token binding */
    private String deviceInfo;
}
