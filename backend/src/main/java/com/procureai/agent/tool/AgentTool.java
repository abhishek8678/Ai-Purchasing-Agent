package com.procureai.agent.tool;

import java.util.Map;

public interface AgentTool {
    AgentToolDefinition definition();
    AgentToolOutput execute(Map<String, Object> arguments, AgentToolContext context);
}
