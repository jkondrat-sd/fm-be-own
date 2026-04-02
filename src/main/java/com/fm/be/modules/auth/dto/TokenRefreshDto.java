package com.fm.be.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for refreshing an access token using a valid refresh token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRefreshDto {

    /** The refresh token issued during login */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
