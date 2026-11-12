package com.procureai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchasingBudgetRepository extends JpaRepository<PurchasingBudget, Long> {

    List<PurchasingBudget> findByWarehouse(Warehouse warehouse);

    Optional<PurchasingBudget> findByWarehouseAndPeriodStartAndPeriodEnd(
            Warehouse warehouse,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
