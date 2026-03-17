package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record ExecutionAgentSelection(ExecutionAgentProvider provider, String modelId) {
    public ExecutionAgentSelection {
        provider = Objects.requireNonNull(provider, "provider must not be null");
        modelId = modelId == null ? "" : modelId.trim();
    }

    public static ExecutionAgentSelection codexDefault() {
        return new ExecutionAgentSelection(ExecutionAgentProvider.CODEX, "");
    }
}
