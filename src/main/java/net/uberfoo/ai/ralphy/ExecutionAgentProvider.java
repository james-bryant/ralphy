package net.uberfoo.ai.ralphy;

public enum ExecutionAgentProvider {
    CODEX("Codex", true),
    GITHUB_COPILOT("GitHub Copilot", false),
    CLAUDE_CODE("Claude Code", false);

    private final String displayName;
    private final boolean executionSupported;

    ExecutionAgentProvider(String displayName, boolean executionSupported) {
        this.displayName = displayName;
        this.executionSupported = executionSupported;
    }

    public String displayName() {
        return displayName;
    }

    public boolean executionSupported() {
        return executionSupported;
    }
}
