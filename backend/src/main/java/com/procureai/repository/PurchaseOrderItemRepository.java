package com.procureai.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;

import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    List<PurchaseOrderItem> findByPurchaseOrder(PurchaseOrder purchaseOrder);

    List<PurchaseOrderItem> findBySupplierProduct(SupplierProduct supplierProduct);

    List<PurchaseOrderItem> findBySupplierProductIn(Collection<SupplierProduct> supplierProducts);

    List<PurchaseOrderItem> findByPurchaseOrderIn(Collection<PurchaseOrder> purchaseOrders);

    List<PurchaseOrderItem> findBySupplierProductAndExpectedDeliveryDate(
            SupplierProduct supplierProduct,
            LocalDate expectedDeliveryDate
    );
}
