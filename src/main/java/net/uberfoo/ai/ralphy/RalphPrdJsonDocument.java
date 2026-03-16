package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RalphPrdJsonDocument(String name,
                                   String branchName,
                                   String description,
                                   List<String> qualityGates,
                                   List<RalphPrdJsonUserStory> userStories,
                                   String sourcePrdPath,
                                   String createdAt,
                                   String updatedAt) {
    public RalphPrdJsonDocument {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(branchName, "branchName must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(qualityGates, "qualityGates must not be null");
        Objects.requireNonNull(userStories, "userStories must not be null");
        sourcePrdPath = sourcePrdPath == null ? "" : sourcePrdPath.trim();
        createdAt = createdAt == null ? "" : createdAt.trim();
        updatedAt = updatedAt == null ? "" : updatedAt.trim();
        qualityGates = List.copyOf(qualityGates);
        userStories = List.copyOf(userStories);
    }
}
