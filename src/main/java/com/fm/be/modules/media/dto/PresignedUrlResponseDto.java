package com.fm.be.modules.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO containing the presigned upload URL and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponseDto {

    private String uploadUrl;
    private String fileKey;
    private String cdnUrl;
    private Instant expiresAt;
}
