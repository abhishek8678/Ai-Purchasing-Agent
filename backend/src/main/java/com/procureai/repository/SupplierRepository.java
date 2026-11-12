package com.procureai.repository;

import java.util.List;
import java.util.Optional;

import com.procureai.domain.entity.Supplier;
import com.procureai.domain.enums.SupplierStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCode(String code);

    List<Supplier> findByStatus(SupplierStatus status);
}
