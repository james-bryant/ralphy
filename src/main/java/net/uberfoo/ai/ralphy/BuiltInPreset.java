package net.uberfoo.ai.ralphy;

import java.util.List;
import java.util.Objects;

public record BuiltInPreset(String presetId,
                            String displayName,
                            String version,
                            PresetUseCase useCase,
                            String overview,
                            List<String> requiredSkills,
                            List<String> operatingAssumptions,
                            String promptPreview) {
    public BuiltInPreset {
        Objects.requireNonNull(presetId, "presetId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(useCase, "useCase must not be null");
        Objects.requireNonNull(overview, "overview must not be null");
        requiredSkills = List.copyOf(Objects.requireNonNull(requiredSkills, "requiredSkills must not be null"));
        operatingAssumptions = List.copyOf(
                Objects.requireNonNull(operatingAssumptions, "operatingAssumptions must not be null")
        );
        Objects.requireNonNull(promptPreview, "promptPreview must not be null");
    }
}
