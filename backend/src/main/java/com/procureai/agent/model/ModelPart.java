package com.procureai.agent.model;

public record ModelPart(String text, ModelToolCall toolCall, ModelToolResult toolResult) {
    public static ModelPart text(String text) { return new ModelPart(text, null, null); }
    public static ModelPart call(ModelToolCall call) { return new ModelPart(null, call, null); }
    public static ModelPart result(ModelToolResult result) { return new ModelPart(null, null, result); }
}
