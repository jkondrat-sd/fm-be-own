package com.fm.be.modules.attendance.entity;

import com.fm.be.common.constants.PunchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for the attendance_logs table.
 *
 * <p>Records each punch in/out event with geofence compliance,
 * face match score, GPS coordinates, and device metadata.
 * The {@code deviceInfo} field is stored as JSONB in PostgreSQL.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance_logs")
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type", nullable = false, length = 10)
    private PunchType punchType;

    @Column(name = "punch_time", nullable = false)
    private Instant punchTime;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column
    private Double accuracy;

    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "is_inside_geofence", nullable = false)
    private boolean isInsideGeofence;

    /** JSONB column storing device metadata (model, os, app version, etc.) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_info", columnDefinition = "jsonb")
    private Map<String, Object> deviceInfo;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.punchTime == null) {
            this.punchTime = this.createdAt;
        }
    }
}
