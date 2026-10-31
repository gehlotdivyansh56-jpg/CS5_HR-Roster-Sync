package com.movesync.rostersync.domain.entity;

import com.movesync.rostersync.domain.enums.EmployeeStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee", uniqueConstraints = {
    @UniqueConstraint(name = "uq_tenant_source_emp", columnNames = {"tenant_id", "source_employee_id"})
})
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "source_employee_id", nullable = false, length = 100)
    private String sourceEmployeeId;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion = 1L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "manager_source_id", length = 100)
    private String managerSourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    private Office office;

    @Column(length = 100)
    private String department;

    @Column(length = 50)
    private String shift;

    // Transport-admin owned fields
    @Column(name = "pickup_point")
    private String pickupPoint;

    @Column(name = "drop_point")
    private String dropPoint;

    @Column(name = "special_assistance")
    private String specialAssistance;

    @Column(name = "transport_field_version", nullable = false)
    private long transportFieldVersion = 0L;

    // Deterministic null-safe SHA-256 hash of HR-owned fields
    @Column(name = "record_hash", nullable = false, length = 64)
    private String recordHash;

    // Optimistic concurrency control lock
    @Version
    @Column(name = "opt_lock_version", nullable = false)
    private long optLockVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    public Employee() {}

    public Employee(Tenant tenant, String sourceEmployeeId, long sourceVersion, String name, String email, String recordHash) {
        this.tenant = tenant;
        this.sourceEmployeeId = sourceEmployeeId;
        this.sourceVersion = sourceVersion;
        this.name = name;
        this.email = email;
        this.recordHash = recordHash;
        this.status = EmployeeStatus.ACTIVE;
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

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getSourceEmployeeId() { return sourceEmployeeId; }
    public void setSourceEmployeeId(String sourceEmployeeId) { this.sourceEmployeeId = sourceEmployeeId; }

    public long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(long sourceVersion) { this.sourceVersion = sourceVersion; }

    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getManagerSourceId() { return managerSourceId; }
    public void setManagerSourceId(String managerSourceId) { this.managerSourceId = managerSourceId; }

    public Office getOffice() { return office; }
    public void setOffice(Office office) { this.office = office; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public String getPickupPoint() { return pickupPoint; }
    public void setPickupPoint(String pickupPoint) { this.pickupPoint = pickupPoint; }

    public String getDropPoint() { return dropPoint; }
    public void setDropPoint(String dropPoint) { this.dropPoint = dropPoint; }

    public String getSpecialAssistance() { return specialAssistance; }
    public void setSpecialAssistance(String specialAssistance) { this.specialAssistance = specialAssistance; }

    public long getTransportFieldVersion() { return transportFieldVersion; }
    public void setTransportFieldVersion(long transportFieldVersion) { this.transportFieldVersion = transportFieldVersion; }

    public String getRecordHash() { return recordHash; }
    public void setRecordHash(String recordHash) { this.recordHash = recordHash; }

    public long getOptLockVersion() { return optLockVersion; }
    public void setOptLockVersion(long optLockVersion) { this.optLockVersion = optLockVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Instant deactivatedAt) { this.deactivatedAt = deactivatedAt; }
}

