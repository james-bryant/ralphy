package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetCatalogServiceTest {
    private final PresetCatalogService presetCatalogService = new PresetCatalogService();

    @Test
    void builtInCatalogProvidesVersionedPresetsForEachWorkflowStage() {
        Set<PresetUseCase> coveredUseCases = EnumSet.noneOf(PresetUseCase.class);

        for (BuiltInPreset preset : presetCatalogService.presets()) {
            coveredUseCases.add(preset.useCase());
            assertTrue(preset.version().startsWith("v"));
            assertFalse(preset.presetId().isBlank());
            assertFalse(preset.overview().isBlank());
            assertFalse(preset.promptPreview().isBlank());
            assertFalse(preset.operatingAssumptions().isEmpty());
        }

        assertEquals(EnumSet.allOf(PresetUseCase.class), coveredUseCases);
    }

    @Test
    void builtInCatalogCanRecordRequiredSkillsAndOperatingAssumptions() {
        BuiltInPreset prdPreset = presetCatalogService.defaultPreset(PresetUseCase.PRD_CREATION);
        BuiltInPreset implementationPreset = presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION);

        assertEquals(Set.of("ralph-tui-prd"), Set.copyOf(prdPreset.requiredSkills()));
        assertTrue(prdPreset.operatingAssumptions().stream()
                .anyMatch(assumption -> assumption.contains(".ralph-tui")));
        assertTrue(implementationPreset.requiredSkills().contains("springboot-tdd"));
        assertTrue(implementationPreset.operatingAssumptions().stream()
                .anyMatch(assumption -> assumption.contains(".\\mvnw.cmd clean verify jacoco:report")));
    }
}
