package com.procureai.agent.model;

import java.util.Map;

public record ModelToolCall(String id, String name, Map<String, Object> arguments) {
}
