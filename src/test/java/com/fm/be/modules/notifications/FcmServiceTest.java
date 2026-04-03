package com.fm.be.modules.notifications;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FcmService}.
 * Tests behavior when FCM is disabled (the common dev/test scenario).
 */
@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @InjectMocks
    private FcmService fcmService;

    @Test
    @DisplayName("should return null when FCM is disabled")
    void sendNotification_fcmDisabled_returnsNull() {
        ReflectionTestUtils.setField(fcmService, "fcmEnabled", false);

        String result = fcmService.sendNotification(
                "token-123", "Title", "Body", Map.of("key", "value"));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should handle null data map when FCM is disabled")
    void sendNotification_nullData_handlesGracefully() {
        ReflectionTestUtils.setField(fcmService, "fcmEnabled", false);

        String result = fcmService.sendNotification(
                "token-123", "Title", "Body", null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should send to multiple devices when FCM is disabled")
    void sendToMultipleDevices_fcmDisabled_logsOnly() {
        ReflectionTestUtils.setField(fcmService, "fcmEnabled", false);

        fcmService.sendToMultipleDevices(
                List.of("token-1", "token-2"),
                "Title", "Body", Map.of());

        // No exception = success (FCM disabled, just logs)
    }
}
