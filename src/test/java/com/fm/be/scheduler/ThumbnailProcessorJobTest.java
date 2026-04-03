package com.fm.be.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThumbnailProcessorJobTest {

    @InjectMocks
    private ThumbnailProcessorJob thumbnailProcessorJob;

    @Nested
    @DisplayName("buildThumbnailKey")
    class BuildThumbnailKeyTests {

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

    @Nested
    @DisplayName("generateThumbnail")
    class GenerateThumbnailTests {

        @Test
        @DisplayName("should download image, create thumbnail, and upload to S3")
        @SuppressWarnings("unchecked")
        void generateThumbnail_success_uploadsThumb() throws Exception {
            ThumbnailProcessorJob spyJob = spy(thumbnailProcessorJob);

            S3Client mockS3 = mock(S3Client.class);
            doReturn(mockS3).when(spyJob).buildS3Client();

            BufferedImage img = new BufferedImage(
                    400, 400, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            ResponseInputStream<GetObjectResponse> ris =
                    new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(imageBytes));
            when(mockS3.getObject(any(GetObjectRequest.class)))
                    .thenReturn(ris);
            when(mockS3.putObject(any(PutObjectRequest.class),
                    any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            spyJob.generateThumbnail("photos/test-image.jpg");

            ArgumentCaptor<PutObjectRequest> putCaptor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(mockS3).putObject(putCaptor.capture(),
                    any(RequestBody.class));

            PutObjectRequest putReq = putCaptor.getValue();
            assertThat(putReq.key())
                    .isEqualTo("photos/test-image_thumb.jpg");
            assertThat(putReq.contentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("should resolve PNG content type")
        @SuppressWarnings("unchecked")
        void generateThumbnail_png_resolvesPngContentType() throws Exception {
            ThumbnailProcessorJob spyJob = spy(thumbnailProcessorJob);

            S3Client mockS3 = mock(S3Client.class);
            doReturn(mockS3).when(spyJob).buildS3Client();

            BufferedImage img = new BufferedImage(
                    400, 400, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);

            ResponseInputStream<GetObjectResponse> ris =
                    new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(baos.toByteArray()));
            when(mockS3.getObject(any(GetObjectRequest.class)))
                    .thenReturn(ris);
            when(mockS3.putObject(any(PutObjectRequest.class),
                    any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            spyJob.generateThumbnail("photos/test.png");

            ArgumentCaptor<PutObjectRequest> putCaptor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(mockS3).putObject(putCaptor.capture(),
                    any(RequestBody.class));

            assertThat(putCaptor.getValue().contentType())
                    .isEqualTo("image/png");
        }

        @Test
        @DisplayName("should handle S3 error gracefully without throwing")
        void generateThumbnail_s3Error_logsAndSwallows() {
            ThumbnailProcessorJob spyJob = spy(thumbnailProcessorJob);

            S3Client mockS3 = mock(S3Client.class);
            doReturn(mockS3).when(spyJob).buildS3Client();

            when(mockS3.getObject(any(GetObjectRequest.class)))
                    .thenThrow(new RuntimeException("S3 connection failed"));

            assertThatCode(() ->
                    spyJob.generateThumbnail("photos/fail.jpg"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should resolve webp content type")
        @SuppressWarnings("unchecked")
        void generateThumbnail_webp_resolvesFallbackJpeg() throws Exception {
            ThumbnailProcessorJob spyJob = spy(thumbnailProcessorJob);

            S3Client mockS3 = mock(S3Client.class);
            doReturn(mockS3).when(spyJob).buildS3Client();

            BufferedImage img = new BufferedImage(
                    400, 400, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);

            ResponseInputStream<GetObjectResponse> ris =
                    new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(baos.toByteArray()));
            when(mockS3.getObject(any(GetObjectRequest.class)))
                    .thenReturn(ris);
            when(mockS3.putObject(any(PutObjectRequest.class),
                    any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            spyJob.generateThumbnail("photos/test.webp");

            ArgumentCaptor<PutObjectRequest> putCaptor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(mockS3).putObject(putCaptor.capture(),
                    any(RequestBody.class));

            assertThat(putCaptor.getValue().key())
                    .isEqualTo("photos/test_thumb.webp");
            assertThat(putCaptor.getValue().contentType())
                    .isEqualTo("image/webp");
        }

        @Test
        @DisplayName("should resolve gif content type")
        @SuppressWarnings("unchecked")
        void generateThumbnail_gif_resolvesGifContentType() throws Exception {
            ThumbnailProcessorJob spyJob = spy(thumbnailProcessorJob);

            S3Client mockS3 = mock(S3Client.class);
            doReturn(mockS3).when(spyJob).buildS3Client();

            BufferedImage img = new BufferedImage(
                    400, 400, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "gif", baos);

            ResponseInputStream<GetObjectResponse> ris =
                    new ResponseInputStream<>(
                            GetObjectResponse.builder().build(),
                            new ByteArrayInputStream(baos.toByteArray()));
            when(mockS3.getObject(any(GetObjectRequest.class)))
                    .thenReturn(ris);
            when(mockS3.putObject(any(PutObjectRequest.class),
                    any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            spyJob.generateThumbnail("photos/test.gif");

            ArgumentCaptor<PutObjectRequest> putCaptor =
                    ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(mockS3).putObject(putCaptor.capture(),
                    any(RequestBody.class));

            assertThat(putCaptor.getValue().contentType())
                    .isEqualTo("image/gif");
        }
    }
}
