package com.procureai.repository;

import java.util.List;

import com.procureai.domain.entity.AgentEvent;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.enums.AgentEventType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEventRepository extends JpaRepository<AgentEvent, Long> {

    List<AgentEvent> findByAgentRun(AgentRun agentRun);

    List<AgentEvent> findByAgentRunOrderBySequenceNumberAsc(AgentRun agentRun);

    List<AgentEvent> findByEventType(AgentEventType eventType);
}
