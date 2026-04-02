package com.fm.be.modules.users.dto;

import com.fm.be.common.constants.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing user.
 * All fields are optional — only non-null fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDto {

    /** Update phone number */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phone;

    /** Update email */
    @Email(message = "Invalid email format")
    private String email;

    /** Update password (will be hashed) */
    private String password;

    /** Update role */
    private Role role;

    /** Update active status */
    private Boolean isActive;

    /** Update preferred language */
    private String preferredLang;
}
