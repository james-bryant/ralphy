package net.uberfoo.ai.ralphy;

public enum PresetUseCase {
    PRD_CREATION("PRD Creation"),
    STORY_IMPLEMENTATION("Story Implementation"),
    RETRY_FIX("Retry/Fix"),
    RUN_SUMMARY("Run Summary");

    private final String label;

    PresetUseCase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
