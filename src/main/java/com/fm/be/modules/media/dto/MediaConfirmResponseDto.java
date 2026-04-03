package com.fm.be.modules.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO after confirming a media upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaConfirmResponseDto {

    private String fileKey;
    private String cdnUrl;
    private String thumbnailUrl;
}
