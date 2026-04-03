package com.fm.be.modules.media;

import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.modules.media.dto.MediaConfirmDto;
import com.fm.be.modules.media.dto.MediaConfirmResponseDto;
import com.fm.be.modules.media.dto.PresignedUrlRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MediaService}.
 * Covers file type/size validation, file key generation,
 * and confirm upload flow.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @InjectMocks
    private MediaService mediaService;

    @Nested
    @DisplayName("validateFileType")
    class ValidateFileTypeTests {

        @Test
        @DisplayName("should accept JPEG image")
        void validateFileType_jpeg_passes() {
            mediaService.validateFileType("image/jpeg");
        }

        @Test
        @DisplayName("should accept PNG image")
        void validateFileType_png_passes() {
            mediaService.validateFileType("image/png");
        }

        @Test
        @DisplayName("should accept MP4 video")
        void validateFileType_mp4_passes() {
            mediaService.validateFileType("video/mp4");
        }

        @Test
        @DisplayName("should accept PDF document")
        void validateFileType_pdf_passes() {
            mediaService.validateFileType("application/pdf");
        }

        @Test
        @DisplayName("should reject unsupported content type")
        void validateFileType_unsupported_throws() {
            assertThatThrownBy(() ->
                    mediaService.validateFileType("application/zip"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("File type not allowed");
        }

        @Test
        @DisplayName("should reject executable content type")
        void validateFileType_executable_throws() {
            assertThatThrownBy(() ->
                    mediaService.validateFileType(
                            "application/x-executable"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("validateFileSize")
    class ValidateFileSizeTests {

        @Test
        @DisplayName("should accept image under 5MB")
        void validateFileSize_imageUnderLimit_passes() {
            mediaService.validateFileSize("image/jpeg",
                    4 * 1024 * 1024L);
        }

        @Test
        @DisplayName("should reject image over 5MB")
        void validateFileSize_imageOverLimit_throws() {
            assertThatThrownBy(() ->
                    mediaService.validateFileSize("image/jpeg",
                            6 * 1024 * 1024L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        @DisplayName("should accept video under 50MB")
        void validateFileSize_videoUnderLimit_passes() {
            mediaService.validateFileSize("video/mp4",
                    45 * 1024 * 1024L);
        }

        @Test
        @DisplayName("should reject video over 50MB")
        void validateFileSize_videoOverLimit_throws() {
            assertThatThrownBy(() ->
                    mediaService.validateFileSize("video/mp4",
                            55 * 1024 * 1024L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("should accept document under 10MB")
        void validateFileSize_docUnderLimit_passes() {
            mediaService.validateFileSize("application/pdf",
                    8 * 1024 * 1024L);
        }

        @Test
        @DisplayName("should reject document over 10MB")
        void validateFileSize_docOverLimit_throws() {
            assertThatThrownBy(() ->
                    mediaService.validateFileSize("application/pdf",
                            11 * 1024 * 1024L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("should pass when fileSize is null")
        void validateFileSize_null_passes() {
            mediaService.validateFileSize("image/jpeg", null);
        }
    }

    @Nested
    @DisplayName("buildFileKey")
    class BuildFileKeyTests {

        @Test
        @DisplayName("should build key with category prefix")
        void buildFileKey_withCategory_includesPrefix() {
            PresignedUrlRequestDto dto = PresignedUrlRequestDto.builder()
                    .fileName("photo.jpg")
                    .contentType("image/jpeg")
                    .category("attendance")
                    .build();

            String key = mediaService.buildFileKey(dto);

            assertThat(key).startsWith("attendance/");
            assertThat(key).endsWith(".jpg");
        }

        @Test
        @DisplayName("should use default category when not specified")
        void buildFileKey_noCategory_usesUploads() {
            PresignedUrlRequestDto dto = PresignedUrlRequestDto.builder()
                    .fileName("doc.pdf")
                    .contentType("application/pdf")
                    .build();

            String key = mediaService.buildFileKey(dto);

            assertThat(key).startsWith("uploads/");
            assertThat(key).endsWith(".pdf");
        }

        @Test
        @DisplayName("should generate unique keys for same file")
        void buildFileKey_sameFile_uniqueKeys() {
            PresignedUrlRequestDto dto = PresignedUrlRequestDto.builder()
                    .fileName("test.png")
                    .contentType("image/png")
                    .category("photos")
                    .build();

            String key1 = mediaService.buildFileKey(dto);
            String key2 = mediaService.buildFileKey(dto);

            assertThat(key1).isNotEqualTo(key2);
        }
    }

    @Nested
    @DisplayName("confirmUpload")
    class ConfirmUploadTests {

        @Test
        @DisplayName("should return CDN URL and thumbnail for images")
        void confirmUpload_imageFile_returnsThumbnailUrl() {
            ReflectionTestUtils.setField(mediaService,
                    "storageEndpoint", "http://localhost:9000");
            ReflectionTestUtils.setField(mediaService,
                    "bucketName", "fm-media");

            MediaConfirmDto dto = MediaConfirmDto.builder()
                    .fileKey("attendance/abc-123.jpg")
                    .build();

            MediaConfirmResponseDto result =
                    mediaService.confirmUpload(dto);

            assertThat(result.getCdnUrl()).contains("abc-123.jpg");
            assertThat(result.getThumbnailUrl())
                    .contains("abc-123_thumb.jpg");
        }

        @Test
        @DisplayName("should return null thumbnail for non-image files")
        void confirmUpload_pdfFile_noThumbnail() {
            ReflectionTestUtils.setField(mediaService,
                    "storageEndpoint", "http://localhost:9000");
            ReflectionTestUtils.setField(mediaService,
                    "bucketName", "fm-media");

            MediaConfirmDto dto = MediaConfirmDto.builder()
                    .fileKey("documents/abc-123.pdf")
                    .build();

            MediaConfirmResponseDto result =
                    mediaService.confirmUpload(dto);

            assertThat(result.getCdnUrl()).contains("abc-123.pdf");
            assertThat(result.getThumbnailUrl()).isNull();
        }
    }
}
