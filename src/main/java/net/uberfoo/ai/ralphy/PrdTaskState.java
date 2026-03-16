package net.uberfoo.ai.ralphy;

import java.util.ArrayList;
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

    public PrdTaskState replaceTask(PrdTaskRecord replacementTask, String timestamp) {
        Objects.requireNonNull(replacementTask, "replacementTask must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        List<PrdTaskRecord> updatedTasks = new ArrayList<>(tasks.size());
        boolean replaced = false;
        for (PrdTaskRecord task : tasks) {
            if (task.taskId().equals(replacementTask.taskId())) {
                updatedTasks.add(replacementTask);
                replaced = true;
            } else {
                updatedTasks.add(task);
            }
        }

        if (!replaced) {
            throw new IllegalArgumentException("No task exists for " + replacementTask.taskId() + ".");
        }

        return new PrdTaskState(
                schemaVersion,
                sourcePrdPath,
                qualityGates,
                updatedTasks,
                createdAt,
                timestamp
        );
    }
}
