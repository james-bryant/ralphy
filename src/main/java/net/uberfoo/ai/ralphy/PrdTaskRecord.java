package net.uberfoo.ai.ralphy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PrdTaskRecord(String taskId,
                            String title,
                            String outcome,
                            PrdTaskStatus status,
                            List<PrdTaskHistoryEntry> history,
                            String createdAt,
                            String updatedAt) {
    public PrdTaskRecord {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        history = List.copyOf(history);
    }

    public static PrdTaskRecord created(String taskId, String title, String outcome, String timestamp) {
        return new PrdTaskRecord(
                taskId,
                title,
                outcome,
                PrdTaskStatus.READY,
                List.of(PrdTaskHistoryEntry.created(timestamp)),
                timestamp,
                timestamp
        );
    }

    public PrdTaskRecord withStoryDefinition(String replacementTitle, String replacementOutcome, String timestamp) {
        Objects.requireNonNull(replacementTitle, "replacementTitle must not be null");
        Objects.requireNonNull(replacementOutcome, "replacementOutcome must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (title.equals(replacementTitle) && outcome.equals(replacementOutcome)) {
            return this;
        }

        List<PrdTaskHistoryEntry> updatedHistory = new ArrayList<>(history);
        updatedHistory.add(PrdTaskHistoryEntry.storyUpdated(timestamp, status));
        return new PrdTaskRecord(
                taskId,
                replacementTitle,
                replacementOutcome,
                status,
                updatedHistory,
                createdAt,
                timestamp
        );
    }

    public PrdTaskRecord withStatus(PrdTaskStatus replacementStatus, String timestamp, String message) {
        Objects.requireNonNull(replacementStatus, "replacementStatus must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        List<PrdTaskHistoryEntry> updatedHistory = new ArrayList<>(history);
        updatedHistory.add(new PrdTaskHistoryEntry(timestamp, "STATUS_CHANGE", replacementStatus, message));
        return new PrdTaskRecord(
                taskId,
                title,
                outcome,
                replacementStatus,
                updatedHistory,
                createdAt,
                timestamp
        );
    }
}
