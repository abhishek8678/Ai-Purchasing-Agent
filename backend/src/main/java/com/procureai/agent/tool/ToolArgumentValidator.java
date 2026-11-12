package com.procureai.agent.tool;

import java.math.BigDecimal;
import java.util.Map;

public final class ToolArgumentValidator {

    private ToolArgumentValidator() {
    }

    public static void validate(AgentToolDefinition definition, Map<String, Object> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments must be a JSON object.");
        }
        for (String key : arguments.keySet()) {
            if (!definition.allowedArguments().contains(key)) {
                throw new IllegalArgumentException("Unknown argument: " + key);
            }
        }
        for (String required : definition.requiredArguments()) {
            if (!arguments.containsKey(required) || arguments.get(required) == null) {
                throw new IllegalArgumentException("Missing required argument: " + required);
            }
        }
    }

    public static long requirePositiveLong(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a positive integer.");
        }
        try {
            long parsed = new BigDecimal(number.toString()).longValueExact();
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be a positive integer.");
            }
            return parsed;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer.");
        }
    }
}
