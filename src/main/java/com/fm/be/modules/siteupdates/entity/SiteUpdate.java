package com.fm.be.modules.siteupdates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the site_updates table.
 *
 * <p>Field employees submit real-time site status reports with
 * text + photos (max 5) + video (max 1, 30s). Media URLs are
 * stored as a PostgreSQL {@code text[]} array column.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "site_updates")
public class SiteUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(columnDefinition = "TEXT")
    private String text;

    /** URLs of uploaded media files (photos + video), stored as text[] */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_urls", columnDefinition = "text[]")
    private List<String> mediaUrls;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.recordedAt == null) {
            this.recordedAt = this.createdAt;
        }
    }
}
