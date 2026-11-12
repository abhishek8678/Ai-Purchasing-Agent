package com.procureai.repository;

import java.util.List;
import java.util.UUID;

import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByProductAndWarehouse(Product product, Warehouse warehouse);

    List<AgentRun> findByStatus(AgentRunStatus status);

    List<AgentRun> findAllByOrderByStartedAtDesc();
}
