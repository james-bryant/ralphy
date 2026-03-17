package net.uberfoo.ai.ralphy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PrdTaskRecord(String taskId,
                            String title,
                            String outcome,
                            PrdTaskStatus status,
                            List<PrdTaskHistoryEntry> history,
                            List<PrdStoryAttemptRecord> attempts,
                            String createdAt,
                            String updatedAt) {
    public PrdTaskRecord {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(history, "history must not be null");
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        history = List.copyOf(history);
    }

    public PrdTaskRecord(String taskId,
                         String title,
                         String outcome,
                         PrdTaskStatus status,
                         List<PrdTaskHistoryEntry> history,
                         String createdAt,
                         String updatedAt) {
        this(taskId, title, outcome, status, history, List.of(), createdAt, updatedAt);
    }

    public static PrdTaskRecord created(String taskId, String title, String outcome, String timestamp) {
        return new PrdTaskRecord(
                taskId,
                title,
                outcome,
                PrdTaskStatus.READY,
                List.of(PrdTaskHistoryEntry.created(timestamp)),
                List.of(),
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
                attempts,
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
                attempts,
                createdAt,
                timestamp
        );
    }

    public PrdTaskRecord queueAttempt(String runId, BuiltInPreset preset, String timestamp, String message) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(preset, "preset must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        List<PrdStoryAttemptRecord> updatedAttempts = new ArrayList<>(attempts);
        updatedAttempts.add(PrdStoryAttemptRecord.queued(runId, preset, timestamp, message));
        return replaceAttemptState(PrdTaskStatus.READY, updatedAttempts, timestamp, message);
    }

    public PrdTaskRecord startAttempt(String runId, String timestamp, String message) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        List<PrdStoryAttemptRecord> updatedAttempts = updateAttempt(runId, attempt -> attempt.started(timestamp, message));
        return replaceAttemptState(PrdTaskStatus.RUNNING, updatedAttempts, timestamp, message);
    }

    public PrdTaskRecord finishAttempt(String runId,
                                       PrdTaskStatus replacementOutcome,
                                       String timestamp,
                                       String message,
                                       String commitHash,
                                       String commitMessage) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(replacementOutcome, "replacementOutcome must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        List<PrdStoryAttemptRecord> updatedAttempts = updateAttempt(
                runId,
                attempt -> attempt.finished(replacementOutcome, timestamp, message, commitHash, commitMessage)
        );
        return replaceAttemptState(replacementOutcome, updatedAttempts, timestamp, message);
    }

    private PrdTaskRecord replaceAttemptState(PrdTaskStatus replacementStatus,
                                              List<PrdStoryAttemptRecord> replacementAttempts,
                                              String timestamp,
                                              String message) {
        List<PrdTaskHistoryEntry> updatedHistory = new ArrayList<>(history);
        updatedHistory.add(new PrdTaskHistoryEntry(timestamp, "STATUS_CHANGE", replacementStatus, message));
        return new PrdTaskRecord(
                taskId,
                title,
                outcome,
                replacementStatus,
                updatedHistory,
                replacementAttempts,
                createdAt,
                timestamp
        );
    }

    private List<PrdStoryAttemptRecord> updateAttempt(String runId,
                                                      java.util.function.UnaryOperator<PrdStoryAttemptRecord> updater) {
        List<PrdStoryAttemptRecord> updatedAttempts = new ArrayList<>(attempts.size());
        boolean updated = false;
        for (PrdStoryAttemptRecord attempt : attempts) {
            if (attempt.runId().equals(runId)) {
                updatedAttempts.add(updater.apply(attempt));
                updated = true;
            } else {
                updatedAttempts.add(attempt);
            }
        }

        if (!updated) {
            throw new IllegalArgumentException("No story attempt exists for run " + runId + ".");
        }

        return List.copyOf(updatedAttempts);
    }
}
