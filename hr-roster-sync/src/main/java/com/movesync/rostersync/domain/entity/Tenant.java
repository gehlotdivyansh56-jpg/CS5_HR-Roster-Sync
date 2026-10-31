package com.movesync.rostersync.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "safety_brake_threshold", nullable = false)
    private double safetyBrakeThreshold = 0.30;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "field_ownership_config")
    private String fieldOwnershipConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Tenant() {}

    public Tenant(String code, String name, double safetyBrakeThreshold) {
        this.code = code;
        this.name = name;
        this.safetyBrakeThreshold = safetyBrakeThreshold;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getSafetyBrakeThreshold() { return safetyBrakeThreshold; }
    public void setSafetyBrakeThreshold(double safetyBrakeThreshold) { this.safetyBrakeThreshold = safetyBrakeThreshold; }

    public String getFieldOwnershipConfig() { return fieldOwnershipConfig; }
    public void setFieldOwnershipConfig(String fieldOwnershipConfig) { this.fieldOwnershipConfig = fieldOwnershipConfig; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
