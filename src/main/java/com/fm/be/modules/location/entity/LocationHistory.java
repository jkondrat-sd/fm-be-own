package com.fm.be.modules.location.entity;

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

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the location_history table.
 *
 * <p>Stores GPS coordinates from mobile devices. Data is first written
 * to Redis for fast access, then batch-flushed to PostgreSQL every 5 minutes
 * by the scheduled flush job. Subject to 30-day retention cleanup.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "location_history")
public class LocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "lat", nullable = false)
    private Double latitude;

    @Column(name = "lng", nullable = false)
    private Double longitude;

    @Column
    private Double accuracy;

    @Column
    private Double battery;

    @Column
    private Double signal;

    @Column(name = "airplane_mode", nullable = false)
    @Builder.Default
    private boolean airplaneMode = false;

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
