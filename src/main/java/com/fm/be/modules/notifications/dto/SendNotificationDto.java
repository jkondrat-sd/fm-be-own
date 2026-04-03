package com.fm.be.modules.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for sending a targeted notification to a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationDto {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Title is required")
    private String title;

    private String body;

    private String type;

    private Map<String, Object> metadata;
}
