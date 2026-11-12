package com.procureai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    List<DemandForecast> findByProductAndWarehouse(Product product, Warehouse warehouse);

    List<DemandForecast> findByProductAndWarehouseAndForecastDate(
            Product product,
            Warehouse warehouse,
            LocalDate forecastDate
    );

    List<DemandForecast> findByForecastRunId(UUID forecastRunId);

    List<DemandForecast> findByProductAndWarehouseOrderByForecastDateAsc(Product product, Warehouse warehouse);
}
