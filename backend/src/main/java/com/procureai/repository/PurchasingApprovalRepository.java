package com.procureai.repository;

import java.util.Optional;
import java.util.UUID;

import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.PurchasingApproval;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchasingApprovalRepository extends JpaRepository<PurchasingApproval, UUID> {

    Optional<PurchasingApproval> findByAgentRun(AgentRun agentRun);

    boolean existsByAgentRun(AgentRun agentRun);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select approval from PurchasingApproval approval where approval.id = :id")
    Optional<PurchasingApproval> findByIdForUpdate(@Param("id") UUID id);
}
