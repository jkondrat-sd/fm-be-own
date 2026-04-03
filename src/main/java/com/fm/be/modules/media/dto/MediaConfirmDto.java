package com.fm.be.modules.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for confirming a successful media upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaConfirmDto {

    @NotBlank(message = "File key is required")
    private String fileKey;
}
