package com.fm.be.modules.notifications;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.notifications.dto.NotificationResponseDto;
import com.fm.be.modules.notifications.dto.RegisterDeviceDto;
import com.fm.be.modules.notifications.dto.SendNotificationDto;
import com.fm.be.modules.notifications.entity.DeviceToken;
import com.fm.be.modules.notifications.entity.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}.
 * Covers send, list, mark read, and device registration.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("sendNotification")
    class SendNotificationTests {

        @Test
        @DisplayName("should persist notification and send via FCM")
        void sendNotification_withDeviceTokens_sendsViaFcm() {
            SendNotificationDto dto = SendNotificationDto.builder()
                    .userId(userId)
                    .title("Shift Reminder")
                    .body("Your shift starts in 30 minutes")
                    .type("SHIFT_REMINDER")
                    .build();

            Notification saved = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .title(dto.getTitle())
                    .body(dto.getBody())
                    .type("SHIFT_REMINDER")
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(saved);
            when(deviceTokenRepository.findByUserId(userId))
                    .thenReturn(List.of(
                            DeviceToken.builder()
                                    .fcmToken("token-1")
                                    .userId(userId)
                                    .build()));

            NotificationResponseDto result =
                    notificationService.sendNotification(dto);

            assertThat(result.getTitle()).isEqualTo("Shift Reminder");
            assertThat(result.getType()).isEqualTo("SHIFT_REMINDER");
            verify(fcmService).sendToMultipleDevices(
                    eq(List.of("token-1")),
                    eq("Shift Reminder"),
                    eq("Your shift starts in 30 minutes"),
                    anyMap());
        }

        @Test
        @DisplayName("should persist notification without FCM when no devices")
        void sendNotification_noDevices_persistsOnly() {
            SendNotificationDto dto = SendNotificationDto.builder()
                    .userId(userId)
                    .title("Test")
                    .body("Test body")
                    .build();

            Notification saved = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .title("Test")
                    .body("Test body")
                    .type("GENERAL")
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(saved);
            when(deviceTokenRepository.findByUserId(userId))
                    .thenReturn(Collections.emptyList());

            NotificationResponseDto result =
                    notificationService.sendNotification(dto);

            assertThat(result).isNotNull();
            verify(fcmService, never()).sendToMultipleDevices(
                    anyList(), anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("should use GENERAL type when type is null")
        void sendNotification_nullType_defaultsToGeneral() {
            SendNotificationDto dto = SendNotificationDto.builder()
                    .userId(userId)
                    .title("Hello")
                    .build();

            Notification saved = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .title("Hello")
                    .type("GENERAL")
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(saved);
            when(deviceTokenRepository.findByUserId(userId))
                    .thenReturn(Collections.emptyList());

            NotificationResponseDto result =
                    notificationService.sendNotification(dto);

            assertThat(result.getType()).isEqualTo("GENERAL");
        }
    }

    @Nested
    @DisplayName("listNotifications")
    class ListNotificationsTests {

        @Test
        @DisplayName("should return paginated notifications with unread count")
        void listNotifications_returnsWithMeta() {
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            Notification n = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .title("Alert")
                    .type("GENERAL")
                    .createdAt(Instant.now())
                    .build();

            Page<Notification> page = new PageImpl<>(
                    List.of(n), PageRequest.of(0, 10), 1);
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                    eq(userId), any(PageRequest.class)))
                    .thenReturn(page);
            when(notificationRepository.countByUserIdAndIsReadFalse(userId))
                    .thenReturn(3L);

            NotificationService.PaginatedNotifications result =
                    notificationService.listNotifications(userId, pageReq);

            assertThat(result.notifications()).hasSize(1);
            assertThat(result.unreadCount()).isEqualTo(3);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        @Test
        @DisplayName("should mark notification as read")
        void markAsRead_existingNotification_marksRead() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .id(notifId)
                    .userId(userId)
                    .title("Test")
                    .type("GENERAL")
                    .isRead(false)
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.findById(notifId))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(i -> i.getArgument(0));

            NotificationResponseDto result =
                    notificationService.markAsRead(notifId);

            assertThat(result.isRead()).isTrue();
        }

        @Test
        @DisplayName("should throw when notification not found")
        void markAsRead_notFound_throws() {
            UUID notifId = UUID.randomUUID();
            when(notificationRepository.findById(notifId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    notificationService.markAsRead(notifId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("registerDevice")
    class RegisterDeviceTests {

        @Test
        @DisplayName("should create new device token")
        void registerDevice_newToken_creates() {
            RegisterDeviceDto dto = RegisterDeviceDto.builder()
                    .fcmToken("new-token-123")
                    .deviceInfo("Pixel 7, Android 14")
                    .build();

            when(deviceTokenRepository.findByUserIdAndFcmToken(
                    userId, "new-token-123"))
                    .thenReturn(Optional.empty());

            notificationService.registerDevice(userId, dto);

            verify(deviceTokenRepository).save(any(DeviceToken.class));
        }

        @Test
        @DisplayName("should update existing device token info")
        void registerDevice_existingToken_updates() {
            DeviceToken existing = DeviceToken.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .fcmToken("existing-token")
                    .deviceInfo("Old device")
                    .build();

            RegisterDeviceDto dto = RegisterDeviceDto.builder()
                    .fcmToken("existing-token")
                    .deviceInfo("Updated device info")
                    .build();

            when(deviceTokenRepository.findByUserIdAndFcmToken(
                    userId, "existing-token"))
                    .thenReturn(Optional.of(existing));

            notificationService.registerDevice(userId, dto);

            verify(deviceTokenRepository).save(existing);
            assertThat(existing.getDeviceInfo())
                    .isEqualTo("Updated device info");
        }
    }

    @Nested
    @DisplayName("sendToUser")
    class SendToUserTests {

        @Test
        @DisplayName("should send notification via convenience method")
        void sendToUser_delegatesToSendNotification() {
            Notification saved = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .title("Geofence Alert")
                    .body("Employee left site boundary")
                    .type("GEOFENCE_ALERT")
                    .createdAt(Instant.now())
                    .build();

            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(saved);
            when(deviceTokenRepository.findByUserId(userId))
                    .thenReturn(Collections.emptyList());

            notificationService.sendToUser(userId,
                    "Geofence Alert", "Employee left site boundary",
                    "GEOFENCE_ALERT");

            verify(notificationRepository).save(any(Notification.class));
        }
    }
}
