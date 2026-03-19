package net.uberfoo.ai.ralphy;

public enum ExecutionAgentProvider {
    CODEX("Codex", true, "codex_cli", true),
    GITHUB_COPILOT("GitHub Copilot", true, "copilot_cli", false),
    CLAUDE_CODE("Claude Code", false, "", false);

    private final String displayName;
    private final boolean executionSupported;
    private final String toolingCheckId;
    private final boolean authenticationRequired;

    ExecutionAgentProvider(String displayName,
                           boolean executionSupported,
                           String toolingCheckId,
                           boolean authenticationRequired) {
        this.displayName = displayName;
        this.executionSupported = executionSupported;
        this.toolingCheckId = toolingCheckId == null ? "" : toolingCheckId;
        this.authenticationRequired = authenticationRequired;
    }

    public String displayName() {
        return displayName;
    }

    public boolean executionSupported() {
        return executionSupported;
    }

    public String toolingCheckId() {
        return toolingCheckId;
    }

    public boolean authenticationRequired() {
        return authenticationRequired;
    }
}
