package com.procureai.agent.model;

import java.util.List;

import com.procureai.agent.tool.AgentToolDefinition;

public interface GeminiModelGateway {
    ModelTurn generate(String systemInstruction, List<ModelMessage> conversation,
                       List<AgentToolDefinition> toolDefinitions);
}
