package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PrdTaskStatus {
    READY("QUEUED", "READY", "QUEUED"),
    RUNNING,
    FAILED,
    COMPLETED("PASSED", "COMPLETED", "PASSED"),
    BLOCKED;

    private final String storageValue;
    private final String[] aliases;

    PrdTaskStatus() {
        this.storageValue = name();
        this.aliases = new String[]{name()};
    }

    PrdTaskStatus(String storageValue, String... aliases) {
        this.storageValue = storageValue;
        this.aliases = aliases;
    }

    @JsonValue
    public String storageValue() {
        return storageValue;
    }

    public boolean isQueued() {
        return this == READY;
    }

    public boolean isPassed() {
        return this == COMPLETED;
    }

    @JsonCreator
    public static PrdTaskStatus fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return READY;
        }

        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        for (PrdTaskStatus status : values()) {
            for (String alias : status.aliases) {
                if (alias.equalsIgnoreCase(normalizedValue)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported PRD task status: " + value);
    }
}
