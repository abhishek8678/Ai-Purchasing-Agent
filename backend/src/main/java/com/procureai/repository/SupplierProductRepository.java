package com.procureai.repository;

import java.util.List;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.SupplierOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {

    List<SupplierProduct> findByProductAndWarehouse(Product product, Warehouse warehouse);

    List<SupplierProduct> findBySupplierAndWarehouse(Supplier supplier, Warehouse warehouse);

    List<SupplierProduct> findByProductAndWarehouseAndStatus(
            Product product,
            Warehouse warehouse,
            SupplierOfferStatus status
    );
}
