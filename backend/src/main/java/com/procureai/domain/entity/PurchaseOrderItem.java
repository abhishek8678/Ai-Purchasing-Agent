package com.procureai.domain.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One supplier offer and its ordered, confirmed, and received quantities on a PO.
 * A transactional service must verify that the offer's supplier and warehouse match
 * the PO header; the product being purchased is the product referenced by the offer.
 * The unit price is kept as an order-time snapshot so later offer changes do not
 * rewrite the historical order.
 */
@Entity
@Table(
        name = "purchase_order_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_order_item_order_offer",
                columnNames = {"purchase_order_id", "supplier_product_id"}
        ),
        indexes = @Index(
                name = "idx_purchase_order_items_offer_delivery",
                columnList = "supplier_product_id, expected_delivery_date"
        )
)
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_product_id", nullable = false)
    private SupplierProduct supplierProduct;

    @NotNull
    @Positive
    @Column(name = "ordered_units", nullable = false)
    private Long orderedUnits;

    @PositiveOrZero
    @Column(name = "confirmed_units")
    private Long confirmedUnits;

    @NotNull
    @PositiveOrZero
    @Column(name = "received_units", nullable = false)
    private Long receivedUnits;

    @NotNull
    @PositiveOrZero
    @Column(name = "unit_price_at_order", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceAtOrder;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PurchaseOrderItem() {
        // Required by JPA.
    }

    public PurchaseOrderItem(
            PurchaseOrder purchaseOrder,
            SupplierProduct supplierProduct,
            Long orderedUnits,
            Long confirmedUnits,
            Long receivedUnits,
            BigDecimal unitPriceAtOrder,
            LocalDate expectedDeliveryDate
    ) {
        this.purchaseOrder = purchaseOrder;
        this.supplierProduct = supplierProduct;
        this.orderedUnits = orderedUnits;
        this.confirmedUnits = confirmedUnits;
        this.receivedUnits = receivedUnits;
        this.unitPriceAtOrder = unitPriceAtOrder;
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    @AssertTrue(message = "confirmed units must be between zero and ordered units")
    public boolean isConfirmedUnitsWithinOrderedUnits() {
        return confirmedUnits == null || (confirmedUnits >= 0 && orderedUnits != null && confirmedUnits <= orderedUnits);
    }

    @AssertTrue(message = "received units must be between zero and ordered units")
    public boolean isReceivedUnitsWithinOrderedUnits() {
        return receivedUnits == null || (receivedUnits >= 0 && orderedUnits != null && receivedUnits <= orderedUnits);
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
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public SupplierProduct getSupplierProduct() { return supplierProduct; }
    public void setSupplierProduct(SupplierProduct supplierProduct) { this.supplierProduct = supplierProduct; }
    public Long getOrderedUnits() { return orderedUnits; }
    public void setOrderedUnits(Long orderedUnits) { this.orderedUnits = orderedUnits; }
    public Long getConfirmedUnits() { return confirmedUnits; }
    public void setConfirmedUnits(Long confirmedUnits) { this.confirmedUnits = confirmedUnits; }
    public Long getReceivedUnits() { return receivedUnits; }
    public void setReceivedUnits(Long receivedUnits) { this.receivedUnits = receivedUnits; }
    public BigDecimal getUnitPriceAtOrder() { return unitPriceAtOrder; }
    public void setUnitPriceAtOrder(BigDecimal unitPriceAtOrder) { this.unitPriceAtOrder = unitPriceAtOrder; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
