package com.fm.be.modules.notifications;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for sending push notifications via Firebase Cloud Messaging.
 *
 * <p>Wraps the Firebase Admin SDK. When FCM is disabled (e.g., in dev/test),
 * notification sends are logged but not dispatched.</p>
 */
@Slf4j
@Service
public class FcmService {

    @Value("${app.fcm.enabled:false}")
    private boolean fcmEnabled;

    /**
     * Sends a push notification to a specific device via FCM token.
     *
     * @param fcmToken the target device's FCM token
     * @param title    notification title
     * @param body     notification body text
     * @param data     optional key-value data payload
     * @return the FCM message ID, or null if FCM is disabled
     */
    public String sendNotification(String fcmToken, String title,
                                    String body, Map<String, String> data) {
        if (!fcmEnabled) {
            log.info("FCM disabled — notification logged: token={}, title={}",
                    fcmToken, title);
            return null;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String messageId = FirebaseMessaging.getInstance()
                    .send(messageBuilder.build());

            log.info("FCM notification sent: messageId={}, token={}",
                    messageId, fcmToken);
            return messageId;

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification: token={}, error={}",
                    fcmToken, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Sends a push notification to multiple devices.
     *
     * @param fcmTokens list of target FCM tokens
     * @param title     notification title
     * @param body      notification body text
     * @param data      optional data payload
     */
    public void sendToMultipleDevices(java.util.List<String> fcmTokens,
                                       String title, String body,
                                       Map<String, String> data) {
        for (String token : fcmTokens) {
            sendNotification(token, title, body, data);
        }
    }
}
