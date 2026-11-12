package com.procureai.agent.model;

import java.util.List;

/** One model response. {@code model} is the Gemini model that actually answered (it may be a fallback). */
public record ModelTurn(ModelMessage modelMessage, String text, List<ModelToolCall> toolCalls, String model) {
    public ModelTurn {
        toolCalls = List.copyOf(toolCalls);
    }

    public ModelTurn(ModelMessage modelMessage, String text, List<ModelToolCall> toolCalls) {
        this(modelMessage, text, toolCalls, null);
    }
}
