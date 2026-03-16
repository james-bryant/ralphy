package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RalphPrdJsonUserStory(String id,
                                    String title,
                                    String description,
                                    List<String> acceptanceCriteria,
                                    int priority,
                                    boolean passes,
                                    List<String> dependsOn,
                                    String completionNotes,
                                    String outcome,
                                    String ralphyStatus,
                                    List<PrdTaskHistoryEntry> history,
                                    List<PrdStoryAttemptRecord> attempts,
                                    String createdAt,
                                    String updatedAt) {
    public RalphPrdJsonUserStory {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(acceptanceCriteria, "acceptanceCriteria must not be null");
        Objects.requireNonNull(dependsOn, "dependsOn must not be null");
        completionNotes = completionNotes == null ? "" : completionNotes.trim();
        outcome = outcome == null ? "" : outcome.trim();
        ralphyStatus = ralphyStatus == null ? "" : ralphyStatus.trim();
        history = history == null ? List.of() : List.copyOf(history);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        createdAt = createdAt == null ? "" : createdAt.trim();
        updatedAt = updatedAt == null ? "" : updatedAt.trim();
        acceptanceCriteria = List.copyOf(acceptanceCriteria);
        dependsOn = List.copyOf(dependsOn);
    }

    public RalphPrdJsonUserStory(String id,
                                 String title,
                                 String description,
                                 List<String> acceptanceCriteria,
                                 int priority,
                                 boolean passes,
                                 List<String> dependsOn,
                                 String completionNotes,
                                 String outcome,
                                 String ralphyStatus,
                                 List<PrdTaskHistoryEntry> history,
                                 String createdAt,
                                 String updatedAt) {
        this(id,
                title,
                description,
                acceptanceCriteria,
                priority,
                passes,
                dependsOn,
                completionNotes,
                outcome,
                ralphyStatus,
                history,
                List.of(),
                createdAt,
                updatedAt);
    }
}
