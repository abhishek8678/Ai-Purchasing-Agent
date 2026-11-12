package com.procureai.agent.model;

/**
 * One part of a model conversation message.
 *
 * <p>{@code thoughtSignature} is an opaque, encrypted token that Gemini 3 models attach to parts (notably function
 * calls). The API requires it to be echoed back unchanged in later turns. It is not readable reasoning; it is kept
 * only in the in-memory conversation and is never persisted to the audit trail.</p>
 */
public record ModelPart(String text, ModelToolCall toolCall, ModelToolResult toolResult, byte[] thoughtSignature) {
    public ModelPart(String text, ModelToolCall toolCall, ModelToolResult toolResult) {
        this(text, toolCall, toolResult, null);
    }

    public static ModelPart text(String text) { return new ModelPart(text, null, null); }
    public static ModelPart call(ModelToolCall call) { return new ModelPart(null, call, null); }
    public static ModelPart result(ModelToolResult result) { return new ModelPart(null, null, result); }

    public ModelPart withThoughtSignature(byte[] signature) {
        return new ModelPart(text, toolCall, toolResult, signature);
    }
}
