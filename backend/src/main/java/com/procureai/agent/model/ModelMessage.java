package com.procureai.agent.model;

import java.util.List;

public record ModelMessage(String role, List<ModelPart> parts) {
    public ModelMessage {
        parts = List.copyOf(parts);
    }
}
