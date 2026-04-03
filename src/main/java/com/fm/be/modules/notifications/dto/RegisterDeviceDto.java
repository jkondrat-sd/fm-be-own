package com.fm.be.modules.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for registering or refreshing an FCM device token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceDto {

    @NotBlank(message = "FCM token is required")
    private String fcmToken;

    private String deviceInfo;
}
