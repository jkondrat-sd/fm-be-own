package com.fm.be.scheduler;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;

/**
 * Async background job for generating image thumbnails.
 *
 * <p>When an image upload is confirmed, this job downloads the original
 * image from S3/R2, generates a thumbnail (200x200), and uploads the
 * thumbnail back to storage with a {@code _thumb} suffix.</p>
 */
@Slf4j
@Component
public class ThumbnailProcessorJob {

    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 200;

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

    /**
     * Generates a thumbnail for the given image file key.
     *
     * @param fileKey the S3/R2 key of the original image
     */
    @Async("mediaExecutor")
    public void generateThumbnail(String fileKey) {
        log.info("Starting thumbnail generation: fileKey={}", fileKey);
        long startTime = System.currentTimeMillis();

        try (S3Client s3Client = buildS3Client()) {
            InputStream original = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(fileKey)
                            .build());

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thumbnails.of(original)
                    .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(0.8)
                    .toOutputStream(output);

            String thumbnailKey = buildThumbnailKey(fileKey);
            byte[] thumbnailBytes = output.toByteArray();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(thumbnailKey)
                            .contentType(resolveContentType(fileKey))
                            .build(),
                    RequestBody.fromInputStream(
                            new ByteArrayInputStream(thumbnailBytes),
                            thumbnailBytes.length));

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Thumbnail generated: fileKey={}, thumbKey={}, "
                            + "size={}B, duration={}ms",
                    fileKey, thumbnailKey, thumbnailBytes.length, elapsed);

        } catch (Exception e) {
            log.error("Thumbnail generation failed: fileKey={}", fileKey, e);
        }
    }

    /**
     * Builds the thumbnail key by inserting {@code _thumb} before the
     * file extension.
     */
    String buildThumbnailKey(String fileKey) {
        int lastDot = fileKey.lastIndexOf('.');
        if (lastDot > 0) {
            return fileKey.substring(0, lastDot) + "_thumb"
                    + fileKey.substring(lastDot);
        }
        return fileKey + "_thumb";
    }

    private String resolveContentType(String fileKey) {
        String lower = fileKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    S3Client buildS3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(storageEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }
}
