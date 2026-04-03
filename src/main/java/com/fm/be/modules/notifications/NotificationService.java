package com.fm.be.modules.notifications;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.notifications.dto.NotificationResponseDto;
import com.fm.be.modules.notifications.dto.RegisterDeviceDto;
import com.fm.be.modules.notifications.dto.SendNotificationDto;
import com.fm.be.modules.notifications.entity.DeviceToken;
import com.fm.be.modules.notifications.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing notifications and device tokens.
 *
 * <p>Handles sending targeted push notifications, persisting in-app
 * notification records, listing user notifications, marking as read,
 * and FCM device token registration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmService fcmService;

    /**
     * Sends a targeted notification to a user.
     * Persists the notification record and dispatches via FCM
     * to all registered devices.
     *
     * @param dto the notification data
     * @return the created notification response
     */
    @Transactional
    public NotificationResponseDto sendNotification(SendNotificationDto dto) {
        Notification notification = Notification.builder()
                .userId(dto.getUserId())
                .title(dto.getTitle())
                .body(dto.getBody())
                .type(dto.getType() != null ? dto.getType() : "GENERAL")
                .metadata(dto.getMetadata())
                .build();
        notification = notificationRepository.save(notification);

        List<DeviceToken> tokens = deviceTokenRepository
                .findByUserId(dto.getUserId());
        if (!tokens.isEmpty()) {
            List<String> fcmTokens = tokens.stream()
                    .map(DeviceToken::getFcmToken)
                    .collect(Collectors.toList());

            Map<String, String> data = new HashMap<>();
            data.put("notificationId", notification.getId().toString());
            data.put("type", notification.getType());

            fcmService.sendToMultipleDevices(
                    fcmTokens, dto.getTitle(), dto.getBody(), data);
        }

        log.info("Notification sent: userId={}, title={}",
                dto.getUserId(), dto.getTitle());
        return toResponseDto(notification);
    }

    /**
     * Lists notifications for a user with pagination.
     *
     * @param userId the user UUID
     * @param pageRequest pagination parameters
     * @return paginated notification results
     */
    @Transactional(readOnly = true)
    public PaginatedNotifications listNotifications(UUID userId,
                                                     PageRequestDto pageRequest) {
        PageRequest pageable = PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit());

        Page<Notification> page = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<NotificationResponseDto> notifications = page.getContent()
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());

        long unreadCount = notificationRepository
                .countByUserIdAndIsReadFalse(userId);

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedNotifications(notifications, meta, unreadCount);
    }

    /**
     * Marks a notification as read.
     *
     * @param notificationId the notification UUID
     * @return the updated notification
     */
    @Transactional
    public NotificationResponseDto markAsRead(UUID notificationId) {
        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification", "id", notificationId));

        notification.setRead(true);
        notification = notificationRepository.save(notification);

        log.debug("Notification marked as read: id={}", notificationId);
        return toResponseDto(notification);
    }

    /**
     * Registers or refreshes an FCM device token for a user.
     * If the token already exists for this user, updates the device info.
     *
     * @param userId the user UUID
     * @param dto the device registration data
     */
    @Transactional
    public void registerDevice(UUID userId, RegisterDeviceDto dto) {
        Optional<DeviceToken> existing = deviceTokenRepository
                .findByUserIdAndFcmToken(userId, dto.getFcmToken());

        if (existing.isPresent()) {
            DeviceToken token = existing.get();
            token.setDeviceInfo(dto.getDeviceInfo());
            deviceTokenRepository.save(token);
            log.debug("Device token updated: userId={}", userId);
        } else {
            DeviceToken token = DeviceToken.builder()
                    .userId(userId)
                    .fcmToken(dto.getFcmToken())
                    .deviceInfo(dto.getDeviceInfo())
                    .build();
            deviceTokenRepository.save(token);
            log.info("Device token registered: userId={}", userId);
        }
    }

    /**
     * Sends a notification to a user by user ID (internal use).
     * Convenience method for other services (shift reminders, geofence alerts).
     *
     * @param userId the target user UUID
     * @param title  notification title
     * @param body   notification body
     * @param type   notification type
     */
    @Transactional
    public void sendToUser(UUID userId, String title, String body, String type) {
        SendNotificationDto dto = SendNotificationDto.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .build();
        sendNotification(dto);
    }

    /**
     * Maps a Notification entity to its response DTO.
     */
    private NotificationResponseDto toResponseDto(Notification n) {
        return NotificationResponseDto.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .body(n.getBody())
                .type(n.getType())
                .read(n.isRead())
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }

    /**
     * Record holding paginated notification results with unread count.
     */
    public record PaginatedNotifications(
            List<NotificationResponseDto> notifications,
            PaginationMeta meta,
            long unreadCount) {
    }
}
