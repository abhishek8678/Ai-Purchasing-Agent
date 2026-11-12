package com.procureai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.procureai.agent.config.AgentProperties;
import com.procureai.agent.model.GeminiSdkModelGateway;
import com.procureai.agent.model.ModelMessage;
import com.procureai.agent.model.ModelPart;
import com.procureai.agent.model.ModelTurn;
import com.procureai.agent.tool.AgentToolDefinition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in live check; excluded from the standard test suite unless explicitly enabled. */
class GeminiLiveIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "PROCUREAI_GEMINI_INTEGRATION_TESTS", matches = "true")
    void sendsARealRequestOnlyWhenExplicitlyEnabledAndConfigured() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "GEMINI_API_KEY must be configured when the live integration test is enabled.");

        AgentProperties properties = new AgentProperties();
        properties.setGeminiApiKey(apiKey);
        String model = System.getenv("GEMINI_MODEL");
        if (model != null && !model.isBlank()) properties.setModel(model);
        GeminiSdkModelGateway gateway = new GeminiSdkModelGateway(properties);
        AgentToolDefinition harmlessTool = new AgentToolDefinition("ping", "No-op function used by this opt-in test.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false), Set.of(), Set.of());

        ModelTurn turn = gateway.generate("Return a one-word acknowledgment.",
                List.of(new ModelMessage("user", List.of(ModelPart.text("Reply with PONG; do not call a function.")))),
                List.of(harmlessTool));

        assertThat(turn).isNotNull();
        assertThat(turn.modelMessage()).isNotNull();
        assertThat(turn.text().isBlank() && turn.toolCalls().isEmpty()).isFalse();
    }
}
