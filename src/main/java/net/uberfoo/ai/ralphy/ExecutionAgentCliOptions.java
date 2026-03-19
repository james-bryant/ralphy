package net.uberfoo.ai.ralphy;

import java.util.ArrayList;
import java.util.List;

final class ExecutionAgentCliOptions {
    private ExecutionAgentCliOptions() {
    }

    static List<String> build(ExecutionAgentSelection executionAgentSelection) {
        if (executionAgentSelection == null) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        if (executionAgentSelection.provider() == ExecutionAgentProvider.CODEX) {
            options.add("--json");
        }
        if (hasText(executionAgentSelection.modelId())) {
            options.add("--model");
            options.add(executionAgentSelection.modelId().trim());
        }
        if (hasText(executionAgentSelection.thinkingLevel())) {
            options.add("--reasoning-effort");
            options.add(executionAgentSelection.thinkingLevel().trim());
        }
        return List.copyOf(options);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
