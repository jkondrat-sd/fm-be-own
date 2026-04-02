package com.fm.be.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for email/password login (Web Portal and Client Portal).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {

    /** User's email address */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /** User's password */
    @NotBlank(message = "Password is required")
    private String password;

    /** Optional device identifier for token binding */
    private String deviceInfo;
}
