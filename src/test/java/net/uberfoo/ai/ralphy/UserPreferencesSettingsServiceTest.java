package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserPreferencesSettingsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsExecutionProfileAndStageSelectionsInPreferences() {
        Preferences preferences = Preferences.userRoot().node("/net/uberfoo/ai/ralphy/tests/preferences/"
                + Integer.toUnsignedString(tempDir.toString().hashCode()));
        UserPreferencesSettingsService settingsService = new UserPreferencesSettingsService(preferences);
        settingsService.clearForTest();

        settingsService.saveExecutionProfile(new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                "C:\\Users\\james\\workspaces",
                "/mnt/c/Users/james/workspaces"
        ));
        settingsService.saveExecutionStageSelection(
                new ExecutionAgentSelection(ExecutionAgentProvider.GITHUB_COPILOT, "gpt-5.4", "high")
        );
        settingsService.savePlanningStageSelection(
                new ExecutionAgentSelection(ExecutionAgentProvider.CODEX, "gpt-5.4-mini", "")
        );

        UserPreferencesSettingsService restartedService = new UserPreferencesSettingsService(preferences);

        assertEquals(ExecutionProfile.ProfileType.WSL, restartedService.executionProfile().type());
        assertEquals("Ubuntu-24.04", restartedService.executionProfile().wslDistribution());
        assertEquals("C:\\Users\\james\\workspaces", restartedService.executionProfile().windowsPathPrefix());
        assertEquals("/mnt/c/Users/james/workspaces", restartedService.executionProfile().wslPathPrefix());
        assertEquals(new ExecutionAgentSelection(ExecutionAgentProvider.GITHUB_COPILOT, "gpt-5.4", "high"),
                restartedService.executionStageSelection());
        assertEquals(new ExecutionAgentSelection(ExecutionAgentProvider.CODEX, "gpt-5.4-mini", ""),
                restartedService.planningStageSelection());
    }
}
