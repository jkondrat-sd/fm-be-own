package com.fm.be.modules.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for a notification record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private UUID id;
    private UUID userId;
    private String title;
    private String body;
    private String type;
    private boolean read;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
