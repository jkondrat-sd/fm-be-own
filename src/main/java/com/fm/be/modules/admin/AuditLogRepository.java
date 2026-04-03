package com.fm.be.modules.admin;

import com.fm.be.modules.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for the audit_logs table.
 * Supports paginated retrieval and filtering by user, action, and date range.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Finds audit logs ordered by creation date (newest first).
     *
     * @param pageable pagination parameters
     * @return paginated audit logs
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Finds audit logs for a specific user.
     *
     * @param userId the acting user's UUID
     * @param pageable pagination parameters
     * @return paginated audit logs for the user
     */
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId,
                                                     Pageable pageable);

    /**
     * Finds audit logs by action type.
     *
     * @param action the action string (e.g., "CREATE_USER", "UPDATE_CONFIG")
     * @param pageable pagination parameters
     * @return paginated audit logs for the action
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action,
                                                     Pageable pageable);

    /**
     * Finds audit logs within a date range.
     *
     * @param start range start
     * @param end range end
     * @param pageable pagination parameters
     * @return paginated audit logs in the range
     */
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            Instant start, Instant end, Pageable pageable);
}
