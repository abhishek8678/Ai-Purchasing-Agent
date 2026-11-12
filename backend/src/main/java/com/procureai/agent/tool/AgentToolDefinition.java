package com.procureai.agent.tool;

import java.util.Map;
import java.util.Set;

public record AgentToolDefinition(
        String name,
        String description,
        Map<String, Object> parametersJsonSchema,
        Set<String> allowedArguments,
        Set<String> requiredArguments
) {
    public AgentToolDefinition {
        parametersJsonSchema = Map.copyOf(parametersJsonSchema);
        allowedArguments = Set.copyOf(allowedArguments);
        requiredArguments = Set.copyOf(requiredArguments);
    }
}
