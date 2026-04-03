package com.fm.be.modules.siteupdates.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a site update report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteUpdateResponseDto {

    private UUID id;
    private UUID employeeId;
    private String employeeName;
    private UUID siteId;
    private String text;
    private List<String> mediaUrls;
    private Instant recordedAt;
    private Instant createdAt;
}
