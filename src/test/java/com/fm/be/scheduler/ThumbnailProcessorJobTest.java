package com.fm.be.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ThumbnailProcessorJob}.
 * Tests thumbnail key generation logic.
 */
@ExtendWith(MockitoExtension.class)
class ThumbnailProcessorJobTest {

    @InjectMocks
    private ThumbnailProcessorJob thumbnailProcessorJob;

    @Test
    @DisplayName("should insert _thumb before file extension")
    void buildThumbnailKey_withExtension_insertsSuffix() {
        String result = thumbnailProcessorJob
                .buildThumbnailKey("photos/abc-123.jpg");

        assertThat(result).isEqualTo("photos/abc-123_thumb.jpg");
    }

    @Test
    @DisplayName("should handle PNG extension")
    void buildThumbnailKey_png_insertsSuffix() {
        String result = thumbnailProcessorJob
                .buildThumbnailKey("uploads/image.png");

        assertThat(result).isEqualTo("uploads/image_thumb.png");
    }

    @Test
    @DisplayName("should append _thumb when no extension")
    void buildThumbnailKey_noExtension_appendsSuffix() {
        String result = thumbnailProcessorJob
                .buildThumbnailKey("uploads/abc-123");

        assertThat(result).isEqualTo("uploads/abc-123_thumb");
    }

    @Test
    @DisplayName("should handle nested path with extension")
    void buildThumbnailKey_nestedPath_handlesCorrectly() {
        String result = thumbnailProcessorJob
                .buildThumbnailKey("org/site/employee/photo.webp");

        assertThat(result)
                .isEqualTo("org/site/employee/photo_thumb.webp");
    }
}
