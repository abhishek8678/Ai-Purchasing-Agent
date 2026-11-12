package com.procureai.domain.entity;

import java.time.Clock;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_product_warehouse",
                columnNames = {"product_id", "warehouse_id"}
        ),
        indexes = @Index(
                name = "idx_inventory_warehouse_product",
                columnList = "warehouse_id, product_id"
        )
)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @NotNull
    @PositiveOrZero
    @Column(name = "on_hand_units", nullable = false)
    private Long onHandUnits;

    @NotNull
    @PositiveOrZero
    @Column(name = "reserved_units", nullable = false)
    private Long reservedUnits;

    @NotNull
    @Column(name = "last_counted_at", nullable = false)
    private LocalDateTime lastCountedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Inventory() {
        // Required by JPA.
    }

    public Inventory(
            Product product,
            Warehouse warehouse,
            Long onHandUnits,
            Long reservedUnits,
            LocalDateTime lastCountedAt
    ) {
        this.product = product;
        this.warehouse = warehouse;
        this.onHandUnits = onHandUnits;
        this.reservedUnits = reservedUnits;
        this.lastCountedAt = lastCountedAt;
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

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Long getOnHandUnits() {
        return onHandUnits;
    }

    public void setOnHandUnits(Long onHandUnits) {
        this.onHandUnits = onHandUnits;
    }

    public Long getReservedUnits() {
        return reservedUnits;
    }

    public void setReservedUnits(Long reservedUnits) {
        this.reservedUnits = reservedUnits;
    }

    public LocalDateTime getLastCountedAt() {
        return lastCountedAt;
    }

    public void setLastCountedAt(LocalDateTime lastCountedAt) {
        this.lastCountedAt = lastCountedAt;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
