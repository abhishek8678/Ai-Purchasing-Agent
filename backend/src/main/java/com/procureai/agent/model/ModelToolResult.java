package com.procureai.agent.model;

import java.util.Map;

public record ModelToolResult(String id, String name, Map<String, Object> result) {
}
