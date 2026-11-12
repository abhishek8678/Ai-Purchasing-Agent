package com.procureai.repository;

import java.util.List;
import java.util.Optional;

import com.procureai.domain.entity.Inventory;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductAndWarehouse(Product product, Warehouse warehouse);

    List<Inventory> findByWarehouse(Warehouse warehouse);

    List<Inventory> findByProduct(Product product);
}
