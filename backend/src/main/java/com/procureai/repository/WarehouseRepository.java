package com.procureai.repository;

import java.util.List;
import java.util.Optional;

import com.procureai.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    List<Warehouse> findByActiveTrue();
}
