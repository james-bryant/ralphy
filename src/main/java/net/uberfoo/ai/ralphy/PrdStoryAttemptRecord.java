package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record PrdStoryAttemptRecord(String runId,
                                    String presetId,
                                    String presetName,
                                    String presetVersion,
                                    PrdTaskStatus outcome,
                                    String queuedAt,
                                    String startedAt,
                                    String endedAt,
                                    String detail) {
    public PrdStoryAttemptRecord {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(presetId, "presetId must not be null");
        Objects.requireNonNull(presetName, "presetName must not be null");
        Objects.requireNonNull(presetVersion, "presetVersion must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        queuedAt = queuedAt == null ? "" : queuedAt.trim();
        startedAt = startedAt == null ? "" : startedAt.trim();
        endedAt = endedAt == null ? "" : endedAt.trim();
        detail = detail == null ? "" : detail.trim();
    }

    public static PrdStoryAttemptRecord queued(String runId, BuiltInPreset preset, String timestamp, String detail) {
        Objects.requireNonNull(preset, "preset must not be null");
        return new PrdStoryAttemptRecord(
                runId,
                preset.presetId(),
                preset.displayName(),
                preset.version(),
                PrdTaskStatus.READY,
                timestamp,
                "",
                "",
                detail
        );
    }

    public PrdStoryAttemptRecord started(String timestamp, String detail) {
        return new PrdStoryAttemptRecord(
                runId,
                presetId,
                presetName,
                presetVersion,
                PrdTaskStatus.RUNNING,
                queuedAt,
                timestamp,
                "",
                detail
        );
    }

    public PrdStoryAttemptRecord finished(PrdTaskStatus replacementOutcome, String timestamp, String detail) {
        Objects.requireNonNull(replacementOutcome, "replacementOutcome must not be null");
        return new PrdStoryAttemptRecord(
                runId,
                presetId,
                presetName,
                presetVersion,
                replacementOutcome,
                queuedAt,
                startedAt,
                timestamp,
                detail
        );
    }
}
