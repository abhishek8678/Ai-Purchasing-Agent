package com.procureai.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentToolRegistryTest {

    @Test
    void dispatchesValidArgumentsToMatchingHandler() {
        AgentTool tool = Mockito.mock(AgentTool.class);
        AgentToolDefinition definition = new AgentToolDefinition("evidence", "Read evidence",
                Map.of("type", "object"), Set.of("sku"), Set.of("sku"));
        AgentToolOutput expected = AgentToolOutput.success(Map.of("count", 4), Set.of("inventory:WM-001"), Map.of());
        Mockito.when(tool.definition()).thenReturn(definition);
        Mockito.when(tool.execute(Map.of("sku", "WM-001"), new AgentToolContext(
                UUID.randomUUID(), null, 1))).thenReturn(expected);
        AgentToolRegistry registry = new AgentToolRegistry(List.of(tool));
        AgentToolContext context = new AgentToolContext(UUID.randomUUID(), null, 1);

        // Use the same context instance in the mock expectation.
        Mockito.when(tool.execute(Map.of("sku", "WM-001"), context)).thenReturn(expected);
        AgentToolOutput actual = registry.execute("evidence", Map.of("sku", "WM-001"), context);

        assertThat(registry.toolNames()).containsExactly("evidence");
        assertThat(actual).isEqualTo(expected);
        verify(tool).execute(Map.of("sku", "WM-001"), context);
    }

    @Test
    void rejectsMissingAndUnexpectedArgumentsBeforeHandlerRuns() {
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.definition()).thenReturn(new AgentToolDefinition("evidence", "Read evidence",
                Map.of("type", "object"), Set.of("sku"), Set.of("sku")));
        AgentToolRegistry registry = new AgentToolRegistry(List.of(tool));

        AgentToolOutput missing = registry.execute("evidence", Map.of(), null);
        AgentToolOutput unexpected = registry.execute("evidence", Map.of("sku", "WM-001", "extra", true), null);

        assertThat(missing.success()).isFalse();
        assertThat(missing.errorCode()).isEqualTo("INVALID_TOOL_ARGUMENTS");
        assertThat(unexpected.success()).isFalse();
        assertThat(unexpected.errorCode()).isEqualTo("INVALID_TOOL_ARGUMENTS");
        Mockito.verify(tool, Mockito.never()).execute(org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownToolReturnsConciseFailure() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of());

        AgentToolOutput result = registry.execute("not_registered", Map.of(), null);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UNKNOWN_TOOL");
        assertThat(result.asModelResult()).containsKey("error");
    }
}
