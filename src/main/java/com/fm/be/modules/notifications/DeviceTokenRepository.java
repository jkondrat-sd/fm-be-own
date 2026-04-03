package com.fm.be.modules.notifications;

import com.fm.be.modules.notifications.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the device_tokens table.
 */
@Repository
public interface DeviceTokenRepository
        extends JpaRepository<DeviceToken, UUID> {

    /**
     * Finds all device tokens for a user (one per device).
     *
     * @param userId the user UUID
     * @return list of device tokens
     */
    List<DeviceToken> findByUserId(UUID userId);

    /**
     * Finds a specific token entry by user and FCM token.
     *
     * @param userId the user UUID
     * @param fcmToken the FCM token string
     * @return the device token if found
     */
    Optional<DeviceToken> findByUserIdAndFcmToken(UUID userId, String fcmToken);

    /**
     * Deletes all tokens for a user (e.g., on logout).
     *
     * @param userId the user UUID
     */
    void deleteByUserId(UUID userId);
}
