package com.fm.be.modules.siteupdates.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating a new site update report.
 * At least text or media must be provided.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSiteUpdateDto {

    @NotNull(message = "Site ID is required")
    private UUID siteId;

    /** Report text content */
    private String text;

    /** Media URLs (max 5 photos + 1 video = 6 total) */
    @Size(max = 6, message = "Maximum 6 media files allowed")
    private List<String> mediaUrls;

    /** When the update was recorded on the device */
    private Instant recordedAt;
}
