package com.fm.be.modules.notifications;

import com.fm.be.modules.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for the notifications table.
 */
@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    /**
     * Finds notifications for a user (paginated, newest first).
     *
     * @param userId the user UUID
     * @param pageable pagination params
     * @return paginated notifications
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(
            UUID userId, Pageable pageable);

    /**
     * Counts unread notifications for a user.
     *
     * @param userId the user UUID
     * @return count of unread notifications
     */
    long countByUserIdAndIsReadFalse(UUID userId);
}
