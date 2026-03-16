package net.uberfoo.ai.ralphy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PrdTaskState(int schemaVersion,
                           String sourcePrdPath,
                           List<String> qualityGates,
                           List<PrdTaskRecord> tasks,
                           String createdAt,
                           String updatedAt) {
    public static final int SCHEMA_VERSION = 1;

    public PrdTaskState {
        Objects.requireNonNull(sourcePrdPath, "sourcePrdPath must not be null");
        Objects.requireNonNull(qualityGates, "qualityGates must not be null");
        Objects.requireNonNull(tasks, "tasks must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        qualityGates = List.copyOf(qualityGates);
        tasks = List.copyOf(tasks);
    }

    public static PrdTaskState created(String sourcePrdPath,
                                       List<String> qualityGates,
                                       List<PrdTaskRecord> tasks,
                                       String timestamp) {
        return new PrdTaskState(
                SCHEMA_VERSION,
                sourcePrdPath,
                qualityGates,
                tasks,
                timestamp,
                timestamp
        );
    }

    public Optional<PrdTaskRecord> taskById(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }

        return tasks.stream()
                .filter(task -> task.taskId().equals(taskId))
                .findFirst();
    }
}
