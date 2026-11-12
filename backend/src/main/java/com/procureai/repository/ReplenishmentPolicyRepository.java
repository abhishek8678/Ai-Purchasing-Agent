package com.procureai.repository;

import java.util.List;
import java.util.Optional;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.ReplenishmentPolicy;
import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplenishmentPolicyRepository extends JpaRepository<ReplenishmentPolicy, Long> {

    Optional<ReplenishmentPolicy> findByProductAndWarehouse(Product product, Warehouse warehouse);

    List<ReplenishmentPolicy> findByWarehouse(Warehouse warehouse);
}
