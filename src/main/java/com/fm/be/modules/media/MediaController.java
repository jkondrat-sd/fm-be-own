package com.fm.be.modules.media;

import com.fm.be.modules.media.dto.MediaConfirmDto;
import com.fm.be.modules.media.dto.MediaConfirmResponseDto;
import com.fm.be.modules.media.dto.PresignedUrlRequestDto;
import com.fm.be.modules.media.dto.PresignedUrlResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for media upload endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/media/presigned-url — Get presigned upload URL</li>
 *   <li>POST /v1/media/confirm — Confirm upload completion</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Generates a presigned URL for direct upload to S3/R2 storage.
     * Validates file type and size before generating the URL.
     *
     * @param dto the upload request with file metadata
     * @return the presigned URL, file key, and CDN URL
     */
    @PostMapping("/presigned-url")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PresignedUrlResponseDto getPresignedUrl(
            @Valid @RequestBody PresignedUrlRequestDto dto) {
        return mediaService.generatePresignedUrl(dto);
    }

    /**
     * Confirms that a file has been uploaded successfully.
     * Triggers async thumbnail generation for image files.
     *
     * @param dto the confirmation data with file key
     * @return the confirmed media details
     */
    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    public MediaConfirmResponseDto confirmUpload(
            @Valid @RequestBody MediaConfirmDto dto) {
        return mediaService.confirmUpload(dto);
    }
}
