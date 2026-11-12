package com.procureai.controller;

import java.util.List;
import java.util.UUID;

import com.procureai.agent.dto.AgentEventResponse;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.service.AgentExecutionService;
import com.procureai.agent.service.AgentRunQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {

    private final AgentExecutionService executionService;
    private final AgentRunQueryService queryService;

    public AgentRunController(AgentExecutionService executionService, AgentRunQueryService queryService) {
        this.executionService = executionService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRunResponse start(@Valid @RequestBody AgentRunRequest request) {
        return executionService.start(request);
    }

    @GetMapping("/{runId}")
    public AgentRunResponse getRun(@PathVariable UUID runId) {
        return queryService.getRun(runId);
    }

    @GetMapping("/{runId}/events")
    public List<AgentEventResponse> getEvents(@PathVariable UUID runId) {
        return queryService.getEvents(runId);
    }
}
