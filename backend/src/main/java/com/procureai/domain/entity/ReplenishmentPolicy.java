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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(
        name = "replenishment_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_replenishment_policy_product_warehouse",
                columnNames = {"product_id", "warehouse_id"}
        ),
        indexes = @Index(
                name = "idx_replenishment_policy_warehouse_product",
                columnList = "warehouse_id, product_id"
        )
)
public class ReplenishmentPolicy {

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
    @Column(name = "safety_stock_units", nullable = false)
    private Long safetyStockUnits;

    @NotNull
    @Positive
    @Column(name = "review_period_days", nullable = false)
    private Integer reviewPeriodDays;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ReplenishmentPolicy() {
        // Required by JPA.
    }

    public ReplenishmentPolicy(
            Product product,
            Warehouse warehouse,
            Long safetyStockUnits,
            Integer reviewPeriodDays
    ) {
        this.product = product;
        this.warehouse = warehouse;
        this.safetyStockUnits = safetyStockUnits;
        this.reviewPeriodDays = reviewPeriodDays;
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

    public Long getSafetyStockUnits() {
        return safetyStockUnits;
    }

    public void setSafetyStockUnits(Long safetyStockUnits) {
        this.safetyStockUnits = safetyStockUnits;
    }

    public Integer getReviewPeriodDays() {
        return reviewPeriodDays;
    }

    public void setReviewPeriodDays(Integer reviewPeriodDays) {
        this.reviewPeriodDays = reviewPeriodDays;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
