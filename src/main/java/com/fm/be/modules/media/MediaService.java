package com.fm.be.modules.media;

import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.common.constants.ErrorCode;
import com.fm.be.modules.media.dto.MediaConfirmDto;
import com.fm.be.modules.media.dto.MediaConfirmResponseDto;
import com.fm.be.modules.media.dto.PresignedUrlRequestDto;
import com.fm.be.modules.media.dto.PresignedUrlResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for media upload operations using S3-compatible storage.
 *
 * <p>Generates presigned upload URLs for clients to upload directly
 * to R2/S3/MinIO. Validates file types and sizes before generating
 * the URL. Presigned URLs expire after the configured duration
 * (default 15 minutes).</p>
 */
@Slf4j
@Service
public class MediaService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;       // 5 MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024;      // 50 MB
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;   // 10 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv");

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"),
            Map.entry("video/quicktime", ".mov"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("text/csv", ".csv"));

    @Value("${app.storage.endpoint}")
    private String storageEndpoint;

    @Value("${app.storage.access-key}")
    private String accessKey;

    @Value("${app.storage.secret-key}")
    private String secretKey;

    @Value("${app.storage.bucket-name}")
    private String bucketName;

    @Value("${app.storage.region:auto}")
    private String region;

    @Value("${app.storage.presigned-url-expiration-minutes:15}")
    private int presignedUrlExpirationMinutes;

    /**
     * Generates a presigned upload URL for direct client-to-storage upload.
     *
     * @param dto the upload request with file metadata
     * @return the presigned URL and file key
     * @throws BusinessRuleException if file type or size is not allowed
     */
    public PresignedUrlResponseDto generatePresignedUrl(
            PresignedUrlRequestDto dto) {
        validateFileType(dto.getContentType());
        validateFileSize(dto.getContentType(), dto.getFileSize());

        String fileKey = buildFileKey(dto);

        try (S3Presigner presigner = buildPresigner()) {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(dto.getContentType())
                    .build();

            PutObjectPresignRequest presignRequest =
                    PutObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(
                                    presignedUrlExpirationMinutes))
                            .putObjectRequest(objectRequest)
                            .build();

            String uploadUrl = presigner
                    .presignPutObject(presignRequest)
                    .url()
                    .toString();

            Instant expiresAt = Instant.now()
                    .plus(Duration.ofMinutes(presignedUrlExpirationMinutes));

            log.info("Presigned URL generated: fileKey={}, contentType={}",
                    fileKey, dto.getContentType());

            return PresignedUrlResponseDto.builder()
                    .uploadUrl(uploadUrl)
                    .fileKey(fileKey)
                    .cdnUrl(buildCdnUrl(fileKey))
                    .expiresAt(expiresAt)
                    .build();
        }
    }

    /**
     * Confirms that a file upload has been completed successfully.
     * Triggers async thumbnail generation for images.
     *
     * @param dto the confirmation data with the file key
     * @return the confirmed media details with CDN URL
     */
    public MediaConfirmResponseDto confirmUpload(MediaConfirmDto dto) {
        String fileKey = dto.getFileKey();

        String thumbnailUrl = null;
        if (isImageKey(fileKey)) {
            thumbnailUrl = buildThumbnailUrl(fileKey);
        }

        log.info("Upload confirmed: fileKey={}", fileKey);

        return MediaConfirmResponseDto.builder()
                .fileKey(fileKey)
                .cdnUrl(buildCdnUrl(fileKey))
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    /**
     * Validates that the content type is in the allowed set.
     */
    void validateFileType(String contentType) {
        boolean allowed = ALLOWED_IMAGE_TYPES.contains(contentType)
                || ALLOWED_VIDEO_TYPES.contains(contentType)
                || ALLOWED_DOCUMENT_TYPES.contains(contentType);

        if (!allowed) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "File type not allowed: " + contentType);
        }
    }

    /**
     * Validates file size against limits per content type category.
     */
    void validateFileSize(String contentType, Long fileSize) {
        if (fileSize == null) return;

        long maxSize;
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            maxSize = MAX_IMAGE_SIZE;
        } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            maxSize = MAX_VIDEO_SIZE;
        } else {
            maxSize = MAX_DOCUMENT_SIZE;
        }

        if (fileSize > maxSize) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    String.format("File size %d bytes exceeds maximum %d bytes",
                            fileSize, maxSize));
        }
    }

    /**
     * Builds a unique file key with category prefix and UUID.
     */
    String buildFileKey(PresignedUrlRequestDto dto) {
        String category = dto.getCategory() != null
                ? dto.getCategory() : "uploads";
        String extension = CONTENT_TYPE_EXTENSIONS.getOrDefault(
                dto.getContentType(), "");
        return String.format("%s/%s%s", category,
                UUID.randomUUID(), extension);
    }

    private String buildCdnUrl(String fileKey) {
        return storageEndpoint + "/" + bucketName + "/" + fileKey;
    }

    private String buildThumbnailUrl(String fileKey) {
        int lastDot = fileKey.lastIndexOf('.');
        if (lastDot > 0) {
            return buildCdnUrl(
                    fileKey.substring(0, lastDot) + "_thumb"
                            + fileKey.substring(lastDot));
        }
        return buildCdnUrl(fileKey + "_thumb");
    }

    private boolean isImageKey(String fileKey) {
        String lower = fileKey.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }

    S3Presigner buildPresigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(storageEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
