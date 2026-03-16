package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record PrdTaskHistoryEntry(String timestamp,
                                  String type,
                                  PrdTaskStatus status,
                                  String message) {
    public PrdTaskHistoryEntry {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        message = message == null ? "" : message.trim();
    }

    public static PrdTaskHistoryEntry created(String timestamp) {
        return new PrdTaskHistoryEntry(
                timestamp,
                "CREATED",
                PrdTaskStatus.READY,
                "Created from the active PRD."
        );
    }

    public static PrdTaskHistoryEntry storyUpdated(String timestamp, PrdTaskStatus status) {
        return new PrdTaskHistoryEntry(
                timestamp,
                "PRD_SYNC",
                status,
                "Updated story details from the active PRD."
        );
    }
}
