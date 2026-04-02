package com.fm.be.modules.sites.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the sites table.
 *
 * <p>Sites represent physical locations where employees work.
 * Each site has an optional geofence polygon (PostGIS geography)
 * for attendance boundary verification. V1 starts with circular
 * geofences (lat/lng/radius) with polygon support available.</p>
 *
 * <p>Note: The {@code geofence} column (PostGIS geography) is not
 * mapped as a JPA field because it requires Hibernate Spatial with
 * a real PostGIS database. Geofence operations use native SQL queries.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sites")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Column(name = "geofence_radius")
    private Double geofenceRadius;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    // geofence geography column is managed via native SQL queries
    // because Hibernate Spatial requires PostGIS at runtime

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
