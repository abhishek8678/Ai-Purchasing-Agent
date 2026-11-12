package com.procureai.repository;

import java.util.Optional;

import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageCapacityRepository extends JpaRepository<StorageCapacity, Long> {

    Optional<StorageCapacity> findByWarehouse(Warehouse warehouse);
}
