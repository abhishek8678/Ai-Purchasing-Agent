package com.procureai.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.audit.AgentAuditService;
import com.procureai.agent.config.AgentProperties;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.model.GeminiModelGateway;
import com.procureai.agent.model.ModelMessage;
import com.procureai.agent.model.ModelPart;
import com.procureai.agent.model.ModelToolCall;
import com.procureai.agent.model.ModelTurn;
import com.procureai.agent.service.AgentRunQueryService;
import com.procureai.agent.tool.AgentTool;
import com.procureai.agent.tool.AgentToolContext;
import com.procureai.agent.tool.AgentToolDefinition;
import com.procureai.agent.tool.AgentToolOutput;
import com.procureai.agent.tool.AgentToolRegistry;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.service.EvidenceLookupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AgentExecutionServiceTest {

    private final UUID runId = UUID.randomUUID();
    private final AgentRunRequest request = new AgentRunRequest("WM-001", "DEL-01", 800L,
            LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-19"), null);

    @Test
    void executesOnlyModelSelectedToolThenAcceptsEvidenceBasedFinalRecommendation() {
        EvidenceLookupService lookup = Mockito.mock(EvidenceLookupService.class);
        Product product = Mockito.mock(Product.class);
        Warehouse warehouse = Mockito.mock(Warehouse.class);
        when(lookup.requireProduct("WM-001")).thenReturn(product);
        when(lookup.requireWarehouse("DEL-01")).thenReturn(warehouse);

        AgentAuditService audit = Mockito.mock(AgentAuditService.class);
        AgentRun run = Mockito.mock(AgentRun.class);
        when(run.getId()).thenReturn(runId);
        when(audit.start(product, warehouse, request)).thenReturn(run);

        AgentTool tool = Mockito.mock(AgentTool.class);
        when(tool.definition()).thenReturn(new AgentToolDefinition("get_inventory", "Inventory evidence",
                Map.of("type", "object", "properties", Map.of()), Set.of(), Set.of()));
        AgentToolRegistry registry = new AgentToolRegistry(List.of(tool));
        when(tool.execute(Map.of(), new AgentToolContext(runId, request, 1)))
                .thenReturn(AgentToolOutput.success(Map.of("onHandUnits", 120L), Set.of("inventory:WM-001:DEL-01"), Map.of()));

        GeminiModelGateway gateway = Mockito.mock(GeminiModelGateway.class);
        ModelToolCall call = new ModelToolCall("call-1", "get_inventory", Map.of());
        when(gateway.generate(anyString(), anyList(), anyList())).thenReturn(
                new ModelTurn(new ModelMessage("model", List.of(ModelPart.call(call))), null, List.of(call)),
                new ModelTurn(new ModelMessage("model", List.of(ModelPart.text("final"))),
                        "{\"decision\":\"ACCEPT\",\"originalRecommendedQuantity\":800,\"proposedQuantity\":800,"
                                + "\"selectedSupplierId\":null,\"selectedSupplierProductId\":null,"
                                + "\"recommendationSummary\":\"The proposed quantity is supported by available evidence.\","
                                + "\"evidenceReferences\":[\"inventory:WM-001:DEL-01\"],"
                                + "\"keySupportingFacts\":[\"On hand is 120 units.\"],\"risks\":[],"
                                + "\"unresolvedQuestions\":[],\"constraintEvaluationReferences\":[],\"confidence\":0.8}", List.of()));

        AgentRunResponse expected = new AgentRunResponse(runId, "WM-001", "DEL-01", 800L,
                AgentRunStatus.COMPLETED, null, 800L, "The proposed quantity is supported by available evidence.",
                null, null, null);
        AgentRunQueryService query = Mockito.mock(AgentRunQueryService.class);
        when(query.getRun(runId)).thenReturn(expected);
        AgentProperties properties = new AgentProperties();
        properties.setGeminiApiKey("configured-for-mock-test");
        AgentExecutionService service = new AgentExecutionService(lookup, properties, gateway, registry,
                audit, new AgentRecommendationValidator(), query, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC));

        AgentRunResponse response = service.start(request);

        assertThat(response.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(response.proposedQuantity()).isEqualTo(800L);
        ArgumentCaptor<List<ModelMessage>> conversationCaptor = ArgumentCaptor.forClass(List.class);
        verify(gateway, Mockito.times(2)).generate(anyString(), conversationCaptor.capture(), anyList());
        List<ModelMessage> secondConversation = conversationCaptor.getAllValues().get(1);
        ModelMessage returnedToolResults = secondConversation.get(secondConversation.size() - 1);
        assertThat(returnedToolResults.role()).isEqualTo("user");
        assertThat(returnedToolResults.parts()).singleElement().satisfies(part -> {
            assertThat(part.toolResult().id()).isEqualTo("call-1");
            assertThat(part.toolResult().name()).isEqualTo("get_inventory");
            assertThat(part.toolResult().result()).containsKey("result");
        });
        verify(tool).execute(Map.of(), new AgentToolContext(runId, request, 1));
        verify(audit).append(Mockito.eq(runId), Mockito.anyInt(),
                Mockito.eq(com.procureai.domain.enums.AgentEventType.EVIDENCE_OBSERVED),
                Mockito.eq("get_inventory"), any(), any(), anyString());
        verify(audit).finish(Mockito.eq(runId), Mockito.anyInt(), Mockito.eq(AgentRunStatus.COMPLETED),
                Mockito.any(), Mockito.eq(800L), anyString(), any(), any(), any());
    }

    @Test
    void missingApiKeyFailsClearlyBeforeCreatingRun() {
        EvidenceLookupService lookup = Mockito.mock(EvidenceLookupService.class);
        AgentAuditService audit = Mockito.mock(AgentAuditService.class);
        AgentProperties properties = new AgentProperties();
        AgentExecutionService service = new AgentExecutionService(lookup, properties,
                Mockito.mock(GeminiModelGateway.class), new AgentToolRegistry(List.of()), audit,
                new AgentRecommendationValidator(), Mockito.mock(AgentRunQueryService.class), new ObjectMapper(),
                Clock.systemUTC());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.start(request))
                .isInstanceOf(com.procureai.agent.exception.AgentConfigurationException.class)
                .hasMessageContaining("GEMINI_API_KEY is not configured");
        verify(audit, never()).start(any(), any(), any());
        verify(lookup, never()).requireProduct(anyString());
    }
}
