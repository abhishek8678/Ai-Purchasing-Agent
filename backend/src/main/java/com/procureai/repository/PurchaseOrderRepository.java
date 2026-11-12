package com.procureai.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    Optional<PurchaseOrder> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseOrder> findByWarehouseAndStatus(Warehouse warehouse, PurchaseOrderStatus status);

    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrderStatus status);

    List<PurchaseOrder> findByWarehouseAndOrderDateBetween(
            Warehouse warehouse,
            LocalDate startDate,
            LocalDate endDate
    );

    List<PurchaseOrder> findAllByOrderByOrderDateDesc();
}
