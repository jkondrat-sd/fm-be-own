package com.fm.be.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for audit log entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponseDto {

    private UUID id;
    private UUID userId;
    private String action;
    private String targetEntity;
    private UUID targetId;
    private Map<String, Object> metadata;
    private String ipAddress;
    private Instant createdAt;
}
