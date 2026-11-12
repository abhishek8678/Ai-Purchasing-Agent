package com.procureai.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
@Table(
        name = "demand_forecasts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_forecast_run_product_warehouse_date",
                columnNames = {"forecast_run_id", "product_id", "warehouse_id", "forecast_date"}
        ),
        indexes = @Index(
                name = "idx_forecast_product_warehouse_date_generated",
                columnList = "product_id, warehouse_id, forecast_date, generated_at"
        )
)
public class DemandForecast {

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
    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @NotNull
    @PositiveOrZero
    @Column(name = "expected_units", nullable = false)
    private Long expectedUnits;

    @NotNull
    @Column(name = "forecast_run_id", nullable = false)
    private UUID forecastRunId;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Size(max = 80)
    @Column(name = "model_version", length = 80)
    private String modelVersion;

    protected DemandForecast() {
        // Required by JPA.
    }

    public DemandForecast(
            Product product,
            Warehouse warehouse,
            LocalDate forecastDate,
            Long expectedUnits,
            UUID forecastRunId,
            LocalDateTime generatedAt,
            String modelVersion
    ) {
        this.product = product;
        this.warehouse = warehouse;
        this.forecastDate = forecastDate;
        this.expectedUnits = expectedUnits;
        this.forecastRunId = forecastRunId;
        this.generatedAt = generatedAt;
        this.modelVersion = modelVersion;
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

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public void setForecastDate(LocalDate forecastDate) {
        this.forecastDate = forecastDate;
    }

    public Long getExpectedUnits() {
        return expectedUnits;
    }

    public void setExpectedUnits(Long expectedUnits) {
        this.expectedUnits = expectedUnits;
    }

    public UUID getForecastRunId() {
        return forecastRunId;
    }

    public void setForecastRunId(UUID forecastRunId) {
        this.forecastRunId = forecastRunId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
