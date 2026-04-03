package com.fm.be.modules.notifications;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.notifications.dto.NotificationResponseDto;
import com.fm.be.modules.notifications.dto.RegisterDeviceDto;
import com.fm.be.modules.notifications.dto.SendNotificationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for notification endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/notifications/send — Send targeted notification (admin)</li>
 *   <li>GET /v1/notifications — List user's notifications</li>
 *   <li>PUT /v1/notifications/{id}/read — Mark notification as read</li>
 *   <li>POST /v1/notifications/register-device — Register FCM token</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Sends a targeted notification to a specific user (admin only).
     *
     * @param dto the notification data
     * @return the created notification
     */
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public NotificationResponseDto sendNotification(
            @Valid @RequestBody SendNotificationDto dto) {
        return notificationService.sendNotification(dto);
    }

    /**
     * Lists the authenticated user's notifications with pagination.
     *
     * @param principal the authenticated user
     * @param pageRequest pagination parameters
     * @return paginated notifications with unread count
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Object> listNotifications(
            @CurrentUser UserPrincipal principal,
            @Valid PageRequestDto pageRequest) {
        NotificationService.PaginatedNotifications result =
                notificationService.listNotifications(
                        principal.getUserId(), pageRequest);

        Map<String, Object> responseData = Map.of(
                "notifications", result.notifications(),
                "unreadCount", result.unreadCount());

        return ApiResponse.paginated(responseData, result.meta());
    }

    /**
     * Marks a notification as read.
     *
     * @param id the notification UUID
     * @return the updated notification
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public NotificationResponseDto markAsRead(@PathVariable UUID id) {
        return notificationService.markAsRead(id);
    }

    /**
     * Registers or refreshes an FCM device token for push notifications.
     *
     * @param principal the authenticated user
     * @param dto the device registration data
     */
    @PostMapping("/register-device")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void registerDevice(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody RegisterDeviceDto dto) {
        notificationService.registerDevice(
                principal.getUserId(), dto);
    }
}
