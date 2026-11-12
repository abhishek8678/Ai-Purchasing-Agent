package com.procureai.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.procureai.agent.dto.AgentEventResponse;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.exception.AgentConfigurationException;
import com.procureai.agent.exception.AgentNotFoundException;
import com.procureai.agent.service.AgentExecutionService;
import com.procureai.agent.service.AgentRunQueryService;
import com.procureai.controller.AgentRunController;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentRunController.class)
@Import({com.procureai.controller.AgentApiExceptionHandler.class, AgentRunControllerTest.ClockConfiguration.class})
class AgentRunControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AgentExecutionService executionService;

    @MockitoBean
    private AgentRunQueryService queryService;

    @Test
    void startsRunAndReturnsRunAndOrderedEventsAsDtos() throws Exception {
        UUID runId = UUID.randomUUID();
        when(executionService.start(any(AgentRunRequest.class))).thenReturn(new AgentRunResponse(
                runId, "WM-001", "DEL-01", 800L, AgentRunStatus.RUNNING, null, null,
                "Investigation is running.", null, null, null));
        when(queryService.getRun(runId)).thenReturn(new AgentRunResponse(runId, "WM-001", "DEL-01", 800L,
                AgentRunStatus.COMPLETED, null, 800L, "Reviewed evidence.", null, null, LocalDateTime.now()));
        when(queryService.getEvents(runId)).thenReturn(List.of(new AgentEventResponse(1,
                AgentEventType.RUN_STARTED, null, null, null, "Agent investigation started.", LocalDateTime.now())));
        String validRequest = """
                {"productSku":"WM-001","warehouseCode":"DEL-01","recommendedPurchaseUnits":800,
                 "planningStartDate":"2026-11-12","planningEndDate":"2026-11-19"}
                """;

        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content(validRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(get("/api/agent-runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recommendationSummary").value("Reviewed evidence."));
        mvc.perform(get("/api/agent-runs/{runId}/events", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[0].eventType").value("RUN_STARTED"));
    }

    @Test
    void rejectsInvalidRunRequestAndReturnsClearMissingKeyError() throws Exception {
        String invalidRequest = """
                {"productSku":"WM-001","warehouseCode":"DEL-01","recommendedPurchaseUnits":0,
                 "planningStartDate":"2026-11-20","planningEndDate":"2026-11-19"}
                """;
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        when(executionService.start(any(AgentRunRequest.class))).thenThrow(new AgentConfigurationException(
                "GEMINI_API_KEY is not configured. Set it before starting an agent run."));
        String validRequest = """
                {"productSku":"WM-001","warehouseCode":"DEL-01","recommendedPurchaseUnits":800,
                 "planningStartDate":"2026-11-12","planningEndDate":"2026-11-19"}
                """;
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content(validRequest))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("GEMINI_CONFIGURATION_MISSING"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GEMINI_API_KEY")));
    }

    @Test
    void unknownRunReturnsNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        when(queryService.getRun(runId)).thenThrow(new AgentNotFoundException("Agent run was not found."));

        mvc.perform(get("/api/agent-runs/{runId}", runId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AGENT_RUN_NOT_FOUND"));
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.system(ZoneOffset.UTC);
        }
    }
}
