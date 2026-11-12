package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Latest warehouse capacity snapshot supplied by the warehouse management system. */
@Entity
@Table(name = "storage_capacities")
public class StorageCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, unique = true)
    private Warehouse warehouse;

    @NotNull
    @Positive
    @Column(name = "total_volume_m3", nullable = false, precision = 14, scale = 6)
    private BigDecimal totalVolumeM3;

    @NotNull
    @PositiveOrZero
    @Column(name = "allocated_volume_m3", nullable = false, precision = 14, scale = 6)
    private BigDecimal allocatedVolumeM3;

    @NotNull
    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StorageCapacity() {
        // Required by JPA.
    }

    public StorageCapacity(
            Warehouse warehouse,
            BigDecimal totalVolumeM3,
            BigDecimal allocatedVolumeM3,
            LocalDateTime measuredAt
    ) {
        this.warehouse = warehouse;
        this.totalVolumeM3 = totalVolumeM3;
        this.allocatedVolumeM3 = allocatedVolumeM3;
        this.measuredAt = measuredAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public BigDecimal getTotalVolumeM3() { return totalVolumeM3; }
    public void setTotalVolumeM3(BigDecimal totalVolumeM3) { this.totalVolumeM3 = totalVolumeM3; }
    public BigDecimal getAllocatedVolumeM3() { return allocatedVolumeM3; }
    public void setAllocatedVolumeM3(BigDecimal allocatedVolumeM3) { this.allocatedVolumeM3 = allocatedVolumeM3; }
    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
