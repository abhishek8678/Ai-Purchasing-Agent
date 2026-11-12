package com.procureai.agent.tool;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.procureai.service.exception.InvestigationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    @Autowired
    public AgentToolRegistry(PurchasingToolHandlers handlers) {
        this(handlers.tools());
    }

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            if (indexed.putIfAbsent(tool.definition().name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate agent tool: " + tool.definition().name());
            }
        }
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    public List<AgentToolDefinition> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }

    public List<String> toolNames() {
        return List.copyOf(tools.keySet());
    }

    public AgentToolOutput execute(String name, Map<String, Object> arguments, AgentToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return AgentToolOutput.failure("UNKNOWN_TOOL", "The requested tool is not available.");
        }
        try {
            ToolArgumentValidator.validate(tool.definition(), arguments);
            return tool.execute(arguments, context);
        } catch (IllegalArgumentException exception) {
            return AgentToolOutput.failure("INVALID_TOOL_ARGUMENTS", exception.getMessage());
        } catch (InvestigationException exception) {
            return AgentToolOutput.failure(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return AgentToolOutput.failure("TOOL_EXECUTION_FAILED",
                    "The tool could not complete because of an internal evidence error.");
        }
    }
}
