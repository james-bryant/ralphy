package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record ExecutionAgentSelection(ExecutionAgentProvider provider, String modelId, String thinkingLevel) {
    public ExecutionAgentSelection {
        provider = Objects.requireNonNull(provider, "provider must not be null");
        modelId = modelId == null ? "" : modelId.trim();
        thinkingLevel = thinkingLevel == null ? "" : thinkingLevel.trim();
    }

    public ExecutionAgentSelection(ExecutionAgentProvider provider, String modelId) {
        this(provider, modelId, "");
    }

    public static ExecutionAgentSelection codexDefault() {
        return new ExecutionAgentSelection(ExecutionAgentProvider.CODEX, "", "");
    }
}
