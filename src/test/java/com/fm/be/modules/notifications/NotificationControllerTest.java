package com.fm.be.modules.notifications;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.notifications.dto.NotificationResponseDto;
import com.fm.be.modules.notifications.dto.RegisterDeviceDto;
import com.fm.be.modules.notifications.dto.SendNotificationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private UserPrincipal principal() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();
    }

    @Nested
    @DisplayName("sendNotification")
    class SendNotificationTests {

        @Test
        @DisplayName("delegates to NotificationService.sendNotification")
        void delegatesToService() {
            SendNotificationDto dto = mock(SendNotificationDto.class);
            NotificationResponseDto expected = mock(NotificationResponseDto.class);
            when(notificationService.sendNotification(dto)).thenReturn(expected);

            NotificationResponseDto result = notificationController.sendNotification(dto);

            verify(notificationService).sendNotification(dto);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("listNotifications")
    class ListNotificationsTests {

        @Test
        @DisplayName("delegates with user id and builds paginated response with notifications and unreadCount")
        @SuppressWarnings("unchecked")
        void delegatesToService() {
            UserPrincipal principal = principal();
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            List<NotificationResponseDto> notifications = List.of(mock(NotificationResponseDto.class));
            long unreadCount = 3L;
            NotificationService.PaginatedNotifications paginated =
                    new NotificationService.PaginatedNotifications(notifications, meta, unreadCount);

            when(notificationService.listNotifications(principal.getUserId(), pageReq))
                    .thenReturn(paginated);

            ApiResponse<Object> response = notificationController.listNotifications(principal, pageReq);

            verify(notificationService).listNotifications(principal.getUserId(), pageReq);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMeta()).isEqualTo(meta);
            Map<String, Object> data = (Map<String, Object>) response.getData();
            assertThat(data).containsEntry("notifications", notifications);
            assertThat(data).containsEntry("unreadCount", unreadCount);
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        @Test
        @DisplayName("delegates to NotificationService.markAsRead")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            NotificationResponseDto expected = mock(NotificationResponseDto.class);
            when(notificationService.markAsRead(id)).thenReturn(expected);

            NotificationResponseDto result = notificationController.markAsRead(id);

            verify(notificationService).markAsRead(id);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("registerDevice")
    class RegisterDeviceTests {

        @Test
        @DisplayName("delegates to NotificationService.registerDevice with principal user id")
        void delegatesToService() {
            UserPrincipal principal = principal();
            RegisterDeviceDto dto = mock(RegisterDeviceDto.class);

            notificationController.registerDevice(principal, dto);

            verify(notificationService).registerDevice(principal.getUserId(), dto);
        }
    }
}
