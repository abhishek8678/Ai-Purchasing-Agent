package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import com.procureai.domain.enums.SupplierOfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Current purchasing terms for a supplier, product, and destination warehouse. */
@Entity
@Table(
        name = "supplier_products",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supplier_product_warehouse",
                columnNames = {"supplier_id", "product_id", "warehouse_id"}
        ),
        indexes = {
                @Index(name = "idx_supplier_product_warehouse_status", columnList = "product_id, warehouse_id, status"),
                @Index(name = "idx_supplier_product_supplier_status", columnList = "supplier_id, status")
        }
)
public class SupplierProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Size(max = 100)
    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @NotNull
    @Positive
    @Column(name = "minimum_order_quantity", nullable = false)
    private Long minimumOrderQuantity;

    @NotNull
    @Positive
    @Column(name = "pack_size", nullable = false)
    private Long packSize;

    @NotNull
    @PositiveOrZero
    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @NotNull
    @PositiveOrZero
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupplierOfferStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierProduct() {
        // Required by JPA.
    }

    public SupplierProduct(
            Supplier supplier,
            Product product,
            Warehouse warehouse,
            String supplierSku,
            Long minimumOrderQuantity,
            Long packSize,
            Integer leadTimeDays,
            BigDecimal unitPrice,
            String currencyCode,
            SupplierOfferStatus status
    ) {
        this.supplier = supplier;
        this.product = product;
        this.warehouse = warehouse;
        this.supplierSku = supplierSku;
        this.minimumOrderQuantity = minimumOrderQuantity;
        this.packSize = packSize;
        this.leadTimeDays = leadTimeDays;
        this.unitPrice = unitPrice;
        this.currencyCode = currencyCode;
        this.status = status;
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
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse warehouse) { this.warehouse = warehouse; }
    public String getSupplierSku() { return supplierSku; }
    public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
    public Long getMinimumOrderQuantity() { return minimumOrderQuantity; }
    public void setMinimumOrderQuantity(Long minimumOrderQuantity) { this.minimumOrderQuantity = minimumOrderQuantity; }
    public Long getPackSize() { return packSize; }
    public void setPackSize(Long packSize) { this.packSize = packSize; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public SupplierOfferStatus getStatus() { return status; }
    public void setStatus(SupplierOfferStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
