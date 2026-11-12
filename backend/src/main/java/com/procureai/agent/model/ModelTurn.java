package com.procureai.agent.model;

import java.util.List;

public record ModelTurn(ModelMessage modelMessage, String text, List<ModelToolCall> toolCalls) {
    public ModelTurn {
        toolCalls = List.copyOf(toolCalls);
    }
}
