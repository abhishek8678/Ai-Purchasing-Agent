package com.procureai.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record AgentToolOutput(
        boolean success,
        Map<String, Object> result,
        String errorCode,
        String errorMessage,
        Set<String> evidenceReferences,
        Map<Long, Long> supplierOffers
) {
    public AgentToolOutput {
        result = result == null ? Map.of() : Map.copyOf(result);
        evidenceReferences = evidenceReferences == null ? Set.of() : Set.copyOf(evidenceReferences);
        supplierOffers = supplierOffers == null ? Map.of() : Map.copyOf(supplierOffers);
    }

    public static AgentToolOutput success(Map<String, Object> result, Set<String> evidenceReferences,
                                          Map<Long, Long> supplierOffers) {
        return new AgentToolOutput(true, result, null, null, evidenceReferences, supplierOffers);
    }

    public static AgentToolOutput failure(String code, String message) {
        return new AgentToolOutput(false, Map.of(), code, message, Set.of(), Map.of());
    }

    public Map<String, Object> asModelResult() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", success);
        if (success) {
            response.put("result", result);
        } else {
            response.put("error", Map.of("code", errorCode, "message", errorMessage));
        }
        return response;
    }
}
