package net.uberfoo.ai.ralphy;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellUiTest {
    private JavaFxUiHarness harness;
    private String previousHostOverride;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        JavaFxUiHarness.startToolkit();
    }

    @BeforeEach
    void setWindowsHostOverride() {
        previousHostOverride = System.getProperty("ralphy.host.os-name");
        System.setProperty("ralphy.host.os-name", "Windows");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.closeShell();
        }
        if (previousHostOverride == null) {
            System.clearProperty("ralphy.host.os-name");
        } else {
            System.setProperty("ralphy.host.os-name", previousHostOverride);
        }
    }

    @AfterAll
    static void stopJavaFxToolkit() throws Exception {
        JavaFxUiHarness.stopToolkit();
    }

    @Test
    void appShellLaunchHarnessShowsPrimaryStageAndSupportsNavigationInteractions() throws Exception {
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        AppShellDescriptor shellDescriptor = harness.getRequiredBean(AppShellDescriptor.class);

        assertEquals("Ralphy", shellDescriptor.windowTitle());
        assertEquals(shellDescriptor.windowTitle(), harness.stageTitle());
        assertTrue(harness.isShowing());
        assertEquals(shellDescriptor.appName(), harness.text("#brandLabel"));
        assertEquals(shellDescriptor.shellTagline(), harness.text("#taglineLabel"));
        assertEquals(shellDescriptor.navigationPlaceholder(), harness.text("#navigationPlaceholderLabel"));
        assertEquals("Projects", harness.text("#workspaceTitleLabel"));
        assertEquals("Repository onboarding, recent projects, and diagnostics will appear here.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("Projects workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.sceneHasStylesheet(AppTheme.stylesheetUrl()));
        assertTrue(harness.hasRootStyleClass(AppTheme.rootStyleClass()));
        assertEquals(Color.web("#020617"), harness.backgroundColor("#shellRoot"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#navigationPane"));
        assertEquals(Color.web("#111827"), harness.backgroundColor("#workspacePane"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#statusPane"));
        assertEquals(Color.web("#e5eefc"), harness.textFill("#brandLabel"));
        assertEquals(Color.web("#94a3b8"), harness.textFill("#taglineLabel"));
        assertTrue(harness.isVisible("#projectContextCard"));
        assertFalse(harness.isVisible("#agentConfigurationProfileCard"));
        assertFalse(harness.isVisible("#planningAgentSettingsCard"));
        assertFalse(harness.isVisible("#executionAgentSettingsCard"));
        assertFalse(harness.isVisible("#prdValidationCard"));

        harness.clickOn("#agentConfigurationNavButton");

        assertEquals("Agent Configuration", harness.text("#workspaceTitleLabel"));
        assertEquals("User-scoped execution profile and preflight diagnostics appear here.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("Agent configuration workspace ready.", harness.text("#statusLabel"));
        assertFalse(harness.isVisible("#projectContextCard"));
        assertTrue(harness.isVisible("#agentConfigurationProfileCard"));
        assertFalse(harness.isVisible("#planningAgentSettingsCard"));
        assertFalse(harness.isVisible("#executionAgentSettingsCard"));

        harness.clickOn("#prdEditorNavButton");

        assertEquals("PRD Editor", harness.text("#workspaceTitleLabel"));
        assertEquals("Create, refine, and validate the active PRD in this workspace.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("PRD Editor workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#prdEditorNavButton", "shell-nav-button-active"));
        assertTrue(harness.isVisible("#planningAgentSettingsCard"));
        assertTrue(harness.isVisible("#prdValidationCard"));

        harness.clickOn("#executionNavButton");

        assertEquals("Execution", harness.text("#workspaceTitleLabel"));
        assertEquals("Run controls, current story progress, and live execution output will appear here.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("Execution workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#executionNavButton", "shell-nav-button-active"));
        assertTrue(harness.isVisible("#executionAgentSettingsCard"));
    }

    @Test
    void appShellShowsBuiltInPresetCatalogWithReadOnlyPreview() throws Exception {
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        harness.clickOn("#executionNavButton");

        assertEquals("Ralph/Codex Story Implementation", harness.text("#presetPreviewNameLabel"));
        assertTrue(harness.text("#presetPreviewVersionLabel").contains("v1"));
        assertTrue(harness.text("#presetRequiredSkillsValueLabel").contains("springboot-tdd"));
        assertTrue(harness.text("#presetPromptPreviewArea")
                .contains("Implement exactly one approved story"));
        assertFalse(harness.isEditable("#presetPromptPreviewArea"));
        assertTrue(harness.textContent("#presetCatalogCard")
                .contains("Preset preview is read-only in v1"));

        harness.clickOn("#storyImplementationPresetRadioButton");

        assertEquals("Ralph/Codex Story Implementation", harness.text("#presetPreviewNameLabel"));
        assertTrue(harness.text("#presetRequiredSkillsValueLabel").contains("springboot-tdd"));
        assertTrue(harness.text("#presetAssumptionsValueLabel")
                .contains(".\\mvnw.cmd clean verify jacoco:report"));
        assertTrue(harness.text("#presetPromptPreviewArea").contains("Implement exactly one approved story"));
    }

    @Test
    void appShellAutoSelectsWorkflowPresetForTheActivePage() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("workflow-preset-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoryProgressArtifacts(repository, playAutoAdvanceMarkdown(), playAutoAdvancePrdJson());

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory, "--ralphy.preflight.native.auto-run=false");

        harness.clickOn("#executionNavButton");

        assertTrue(harness.isVisible("#presetCatalogCard"));
        assertFalse(harness.isVisible("#prdCreationPresetRadioButton"));
        assertEquals("Ralph/Codex Story Implementation", harness.text("#presetPreviewNameLabel"));
        assertFalse(harness.isDisabled("#startSingleStoryButton"));
        assertEquals("Play", harness.text("#startSingleStoryButton"));

        harness.clickOn("#retryFixPresetRadioButton");
        assertEquals("Ralph/Codex Retry and Fix", harness.text("#presetPreviewNameLabel"));

        harness.clickOn("#prdEditorNavButton");
        assertFalse(harness.isVisible("#presetCatalogCard"));

        harness.clickOn("#executionNavButton");
        assertEquals("Ralph/Codex Retry and Fix", harness.text("#presetPreviewNameLabel"));
        assertFalse(harness.isVisible("#prdCreationPresetRadioButton"));
    }

    @Test
    void appShellOnlyScrollsToThePrdDocumentWhenApplyingPlannerDraftAndReenablesPlay() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("planner-scroll-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        String plannerSessionTimestamp = "2026-03-17T12:00:00Z";
        projectMetadataInitializer.writePrdPlanningSession(
                activeProject,
                new PrdPlanningSession(
                        "Plan a structured Codex output viewer.",
                        List.of(
                                new PrdPlanningSession.Message(
                                        "user",
                                        "Plan a structured Codex output viewer.",
                                        plannerSessionTimestamp
                                ),
                                new PrdPlanningSession.Message(
                                        "assistant",
                                        "Here is a draft to review and apply.",
                                        plannerSessionTimestamp
                                )
                        ),
                        """
                                # PRD: Planner Draft

                                ## Overview
                                Validate PRDs before execution starts.

                                ## Goals
                                - Block malformed PRDs before execution.

                                ## Quality Gates
                                - .\\mvnw.cmd clean verify jacoco:report

                                ## User Stories
                                ### US-020: Validate PRD structure before execution
                                **Description:** As a user, I want malformed task definitions blocked before execution.
                                **Dependencies:** None.
                                **Acceptance Criteria:**
                                - [ ] Structural PRD validation blocks malformed stories.
                                - [ ] .\\mvnw.cmd clean verify jacoco:report

                                ## Scope Boundaries
                                ### In Scope
                                - Structural PRD validation

                                ### Out of Scope
                                - Codex launch orchestration
                                """,
                        plannerSessionTimestamp,
                        plannerSessionTimestamp
                )
        );
        seedStoredProject(storageDirectory, repository);
        Path fakeCodexCommand = createFakeCodexCommandScript(50L);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(
                storageDirectory,
                "--ralphy.preflight.native.auto-run=false",
                "--ralphy.codex.command=" + fakeCodexCommand.toAbsolutePath().normalize()
        );

        harness.clickOn("#executionNavButton");
        harness.clickOn("#retryFixPresetRadioButton");
        assertTrue(harness.isDisabled("#startSingleStoryButton"));

        harness.clickOn("#prdEditorNavButton");
        harness.setScrollValue("#workspaceScrollPane", 1.0);
        double beforeSendScroll = harness.scrollValue("#workspaceScrollPane");
        harness.enterText("#prdPlannerInputArea", "Plan a structured Codex output viewer.");
        harness.clickOn("#prdPlannerSendButton");
        assertEquals("", harness.text("#prdPlannerInputArea"));
        assertTrue(harness.isDisabled("#prdPlannerSendButton"));
        assertTrue(harness.isDisabled("#prdPlannerInputArea"));
        harness.waitUntil(() -> harness.scrollValue("#workspaceScrollPane") < 0.3);
        double duringSendScroll = harness.scrollValue("#workspaceScrollPane");
        assertTrue(
                duringSendScroll < 0.3,
                "Expected planner send to focus the Live PRD Planner card. scroll=" + duringSendScroll
        );
        assertTrue(harness.isVisible("#prdPlannerProgressRow"));
        assertEquals("Waiting for Codex to continue the planner...", harness.text("#prdPlannerProgressLabel"));
        waitForPlannerReply(harness);
        assertTrue(harness.scrollValue("#workspaceScrollPane") < 0.3);

        harness.setScrollValue("#workspaceScrollPane", 0.0);
        assertFalse(harness.isDisabled("#prdPlannerApplyDraftButton"));
        harness.clickOn("#prdPlannerApplyDraftButton");
        harness.waitUntil(() -> harness.text("#prdPlannerMessageLabel").contains("Applied the latest planner draft"));
        assertTrue(harness.text("#prdDocumentPreviewArea").contains("# PRD: Planner Draft"));
        assertTrue(harness.scrollValue("#workspaceScrollPane") > 0.8);

        harness.clickOn("#executionNavButton");
        assertEquals("PRD ready for execution", harness.text("#prdValidationSummaryLabel"));
        assertEquals("Ralph/Codex Story Implementation", harness.text("#presetPreviewNameLabel"));
        assertEquals("Play", harness.text("#startSingleStoryButton"));
        assertFalse(harness.isDisabled("#startSingleStoryButton"));
    }

    @Test
    void appShellPlannerSendFocusesWorkspaceOnPlannerWhenStartingFromEmptySession() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("planner-send-no-jump-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        seedStoredProject(storageDirectory, repository);
        Path fakeCodexCommand = createFakeCodexCommandScript(50L);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(
                storageDirectory,
                "--ralphy.codex.command=" + fakeCodexCommand.toAbsolutePath().normalize()
        );

        harness.clickOn("#prdEditorNavButton");
        harness.setScrollValue("#workspaceScrollPane", 0.15);
        double beforeSendScroll = harness.scrollValue("#workspaceScrollPane");

        harness.enterText("#prdPlannerInputArea", "Plan a dashboard for story-level retries.");
        harness.clickOn("#prdPlannerSendButton");
        assertEquals("", harness.text("#prdPlannerInputArea"));
        assertTrue(harness.isDisabled("#prdPlannerSendButton"));
        assertTrue(harness.isDisabled("#prdPlannerInputArea"));
        harness.waitUntil(() -> harness.scrollValue("#workspaceScrollPane") < 0.3);
        double duringSendScroll = harness.scrollValue("#workspaceScrollPane");
        assertTrue(
                duringSendScroll < 0.3,
                "Expected planner send to bring the Live PRD Planner card into view. before="
                        + beforeSendScroll
                        + ", during="
                        + duringSendScroll
        );
        assertTrue(harness.isVisible("#prdPlannerProgressRow"));
        assertEquals("Waiting for Codex to continue the planner...", harness.text("#prdPlannerProgressLabel"));
        waitForPlannerReply(harness);

        double afterSendScroll = harness.scrollValue("#workspaceScrollPane");
        assertTrue(
                afterSendScroll < 0.3,
                "Expected planner send to keep the Live PRD Planner card in view. before="
                        + beforeSendScroll
                        + ", after="
                        + afterSendScroll
        );
    }

    @Test
    void appShellLoadsPlannerModelChoicesForPrdPlanning() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("planner-models-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory, "--ralphy.preflight.native.auto-run=false");

        harness.clickOn("#prdEditorNavButton");
        harness.waitUntil(() -> harness.text("#planningSettingsStatusLabel").contains("Loaded"));
        assertTrue(harness.exists("#planningSettingsModelComboBox"));
    }

    @Test
    void appShellShowsSingleStorySessionAsBlockedWithoutAnActiveProject() throws Exception {
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        assertEquals("No active project", harness.text("#singleStorySessionSummaryLabel"));
        assertTrue(harness.text("#singleStorySessionDetailLabel")
                .contains("Open a repository"));
        assertTrue(harness.isDisabled("#startSingleStoryButton"));
        assertEquals("No active project", harness.text("#storyProgressSummaryLabel"));
        assertEquals("Current story: none", harness.text("#storyProgressCurrentStoryLabel"));
        assertEquals("0", harness.text("#storyProgressPendingCountLabel"));
        assertEquals("0", harness.text("#storyProgressPausedCountLabel"));
        assertEquals("No active project", harness.text("#runOutputSummaryLabel"));
        assertEquals("No active project", harness.text("#runHistorySummaryLabel"));
    }

    @Test
    void appShellPlayAutoAdvancesAcrossReadyStoriesAndShowsSkippedBlockedReasons() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("play-auto-advance-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoryProgressArtifacts(repository, playAutoAdvanceMarkdown(), playAutoAdvancePrdJson());

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        seedStoredProject(storageDirectory, repository);

        Path fakeGitCommand = createFakeGitCommandScript();
        Path fakeCodexCommand = createFakeCodexCommandScript(1200L);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(
                storageDirectory,
                "--ralphy.preflight.native.auto-run=false",
                "--ralphy.git.command=" + fakeGitCommand.toAbsolutePath().normalize(),
                "--ralphy.codex.command=" + fakeCodexCommand.toAbsolutePath().normalize()
        );

        harness.clickOn("#storyImplementationPresetRadioButton");
        harness.waitUntil(() -> harness.text("#singleStorySessionSummaryLabel").contains("Ready to play US-031"));
        assertEquals("Play", harness.text("#startSingleStoryButton"));
        assertTrue(harness.text("#singleStorySessionDetailLabel").contains("US-030 (status is BLOCKED)"));

        harness.clickOn("#startSingleStoryButton");
        harness.waitUntil(() -> harness.isVisible("#singleStorySessionProgressRow"));
        assertTrue(harness.text("#singleStorySessionProgressLabel").contains("Codex is running"));
        harness.waitUntil(() -> "2".equals(harness.text("#storyProgressPassedCountLabel"))
                && !harness.isVisible("#singleStorySessionProgressRow"), 30L);

        assertEquals("1", harness.text("#storyProgressBlockedCountLabel"));
        assertEquals("0", harness.text("#storyProgressRunningCountLabel"));
        assertFalse(harness.isVisible("#singleStorySessionProgressRow"));
        assertTrue(harness.text("#singleStorySessionMessageLabel").contains("US-030 (status is BLOCKED)"));
        assertEquals("Play", harness.text("#startSingleStoryButton"));

        PrdTaskState taskState = harness.getRequiredBean(ActiveProjectService.class).prdTaskState().orElseThrow();
        assertEquals(PrdTaskStatus.BLOCKED, taskState.taskById("US-030").orElseThrow().status());
        assertEquals(PrdTaskStatus.COMPLETED, taskState.taskById("US-031").orElseThrow().status());
        assertEquals(PrdTaskStatus.COMPLETED, taskState.taskById("US-032").orElseThrow().status());
    }

    @Test
    void appShellPlayRetriesOnceAutomaticallyBeforeContinuing() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("play-retry-once-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoryProgressArtifacts(repository, playRetryOnceMarkdown(), playRetryOncePrdJson());

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        seedStoredProject(storageDirectory, repository);

        Path invocationCounterPath = tempDir.resolve("flaky-codex-count.txt");
        Path fakeGitCommand = createFakeGitCommandScript();
        Path fakeCodexCommand = createFlakyCodexCommandScript(invocationCounterPath);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(
                storageDirectory,
                "--ralphy.preflight.native.auto-run=false",
                "--ralphy.git.command=" + fakeGitCommand.toAbsolutePath().normalize(),
                "--ralphy.codex.command=" + fakeCodexCommand.toAbsolutePath().normalize()
        );

        harness.clickOn("#executionNavButton");
        harness.waitUntil(() -> harness.text("#executionSettingsStatusLabel").contains("Loaded"));

        harness.clickOn("#storyImplementationPresetRadioButton");
        harness.waitUntil(() -> harness.text("#singleStorySessionSummaryLabel").contains("Ready to play US-032"));

        harness.clickOn("#startSingleStoryButton");
        harness.waitUntil(() -> {
            PrdTaskState currentTaskState =
                    harness.getRequiredBean(ActiveProjectService.class).prdTaskState().orElseThrow();
            return currentTaskState.taskById("US-032")
                    .map(task -> task.attempts().size() == 2)
                    .orElse(false);
        });
        harness.waitUntil(() -> harness.text("#singleStorySessionMessageLabel").contains("Play complete."), 20L);

        PrdTaskState taskState = harness.getRequiredBean(ActiveProjectService.class).prdTaskState().orElseThrow();
        PrdTaskRecord retriedTask = taskState.taskById("US-032").orElseThrow();
        assertEquals(PrdTaskStatus.COMPLETED, retriedTask.status());
        assertEquals(2, retriedTask.attempts().size());
        assertEquals(PrdTaskStatus.FAILED, retriedTask.attempts().get(0).outcome());
        assertEquals(PrdTaskStatus.COMPLETED, retriedTask.attempts().get(1).outcome());
        assertEquals(PrdTaskStatus.COMPLETED, taskState.taskById("US-033").orElseThrow().status());
        assertEquals("2", harness.text("#storyProgressPassedCountLabel"));
        assertEquals("3", Files.readString(invocationCounterPath).trim());
    }

    @Test
    void appShellPausesOnlyAfterTheCurrentStoryCompletes() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("pause-after-current-story-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoryProgressArtifacts(repository, pauseAfterCurrentStoryMarkdown(), pauseAfterCurrentStoryPrdJson());

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, passedNativePreflightReport(repository));
        seedStoredProject(storageDirectory, repository);

        Path fakeGitCommand = createFakeGitCommandScript();
        Path fakeCodexCommand = createFakeCodexCommandScript();

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(
                storageDirectory,
                "--ralphy.preflight.native.auto-run=false",
                "--ralphy.git.command=" + fakeGitCommand.toAbsolutePath().normalize(),
                "--ralphy.codex.command=" + fakeCodexCommand.toAbsolutePath().normalize()
        );

        harness.clickOn("#executionNavButton");
        harness.waitUntil(() -> harness.text("#executionSettingsStatusLabel").contains("Loaded"));
        harness.clickOn("#storyImplementationPresetRadioButton");
        harness.waitUntil(() -> harness.text("#singleStorySessionSummaryLabel").contains("Ready to play US-030"));
        assertFalse(harness.isDisabled("#startSingleStoryButton"));

        harness.clickOn("#startSingleStoryButton");
        String pauseRequestedSummary = harness.clickWhenEnabledAndReadText(
                "#pauseSingleStoryButton",
                "#singleStorySessionSummaryLabel"
        );
        assertEquals("Pause requested", pauseRequestedSummary);
        harness.waitUntil(() -> "Execution paused".equals(harness.text("#storyProgressSummaryLabel")), 20L);
        assertTrue(harness.text("#storyProgressCurrentStoryLabel").contains("Paused | US-031"));
        assertEquals("0", harness.text("#storyProgressRunningCountLabel"));
        assertEquals("1", harness.text("#storyProgressPassedCountLabel"));
        assertEquals("1", harness.text("#storyProgressPausedCountLabel"));
        assertEquals("Resume Play", harness.text("#startSingleStoryButton"));

        PrdTaskState taskState = harness.getRequiredBean(ActiveProjectService.class).prdTaskState().orElseThrow();
        assertEquals(PrdTaskStatus.COMPLETED, taskState.taskById("US-030").orElseThrow().status());
        assertEquals(PrdTaskStatus.READY, taskState.taskById("US-031").orElseThrow().status());
    }

    @Test
    void appShellCanSwitchBetweenAssistantSummaryAndRawOutputForPersistedRunArtifacts() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("run-output-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedRunOutputArtifacts(
                activeProject,
                "run-029-1",
                "US-029",
                codexOutputSample(),
                "warning: stderr output",
                "Implemented US-029. Ran verification."
        );
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-029-1",
                projectId,
                "US-029",
                "SUCCEEDED",
                "2026-03-15T22:10:00Z",
                "2026-03-15T22:12:00Z",
                "POWERSHELL",
                repository.toString(),
                1234L,
                0,
                List.of("powershell.exe", "-NoLogo"),
                new LocalMetadataStorage.RunArtifactPaths(
                        activeProject.promptsDirectoryPath().resolve("US-029").resolve("run-029-1").resolve("prompt.txt")
                                .toString(),
                        activeProject.logsDirectoryPath().resolve("US-029").resolve("run-029-1").resolve("stdout.log")
                                .toString(),
                        activeProject.logsDirectoryPath().resolve("US-029").resolve("run-029-1").resolve("stderr.log")
                                .toString(),
                        activeProject.logsDirectoryPath().resolve("US-029").resolve("run-029-1")
                                .resolve("structured-events.jsonl").toString(),
                        activeProject.artifactsDirectoryPath().resolve("US-029").resolve("run-029-1")
                                .resolve("attempt-summary.json").toString(),
                        activeProject.artifactsDirectoryPath().resolve("US-029").resolve("run-029-1")
                                .resolve("assistant-summary.txt").toString()
                )
        )));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertEquals("Latest run: US-029 | SUCCEEDED", harness.text("#runOutputSummaryLabel"));
        assertTrue(harness.text("#runOutputDetailLabel").contains("run-029-1"));
        assertTrue(harness.isSelected("#structuredRunOutputViewRadioButton"));
        assertTrue(harness.isVisible("#runOutputStructuredScrollPane"));
        assertFalse(harness.isVisible("#runOutputTextArea"));

        harness.clickOn("#rawOutputViewRadioButton");

        String rawOutput = harness.text("#runOutputTextArea");
        assertTrue(rawOutput.contains("\"type\" : \"thread.started\""));
        assertTrue(rawOutput.contains("[stderr]"));
        assertTrue(rawOutput.contains("warning: stderr output"));
        double runOutputViewportHeight = harness.height("#runOutputTextArea");

        harness.clickOn("#structuredRunOutputViewRadioButton");

        assertTrue(harness.isVisible("#runOutputStructuredScrollPane"));
        assertFalse(harness.isVisible("#runOutputTextArea"));
        assertTrue(harness.height("#runOutputStructuredScrollPane") > runOutputViewportHeight);
        String structuredOutput = harness.textContent("#runOutputStructuredContainer").toLowerCase();
        assertTrue(structuredOutput.contains("agent message"));
        assertTrue(structuredOutput.contains("command execution"));
        assertTrue(structuredOutput.contains("web search"));
        assertTrue(structuredOutput.contains("greetingcli.java"));
        assertTrue(structuredOutput.contains("turn completed"));
        assertTrue(structuredOutput.contains("warning: stderr output"));
        assertEquals(1, harness.nodeCount("#structuredRunOutputEntryitemitem16"));
        assertTrue(harness.exists("#structuredRunOutputToggleitemitem16"));

        String commandPreview = harness.textContent("#structuredRunOutputBodyitemitem16");
        assertTrue(commandPreview.contains("..."));
        assertTrue(commandPreview.contains("private GreetingCli()"));
        assertFalse(commandPreview.contains("public final class GreetingCli"));

        harness.clickOn("#structuredRunOutputToggleitemitem16");
        String expandedCommandOutput = harness.textContent("#structuredRunOutputBodyitemitem16");
        assertTrue(expandedCommandOutput.contains("public final class GreetingCli"));
        assertTrue(expandedCommandOutput.contains("private GreetingCli()"));

        harness.clickOn("#structuredRunOutputToggleitemitem16");
        String collapsedCommandOutput = harness.textContent("#structuredRunOutputBodyitemitem16");
        assertTrue(collapsedCommandOutput.contains("..."));
        assertFalse(collapsedCommandOutput.contains("public final class GreetingCli"));

        harness.clickOn("#assistantSummaryViewRadioButton");

        assertEquals("Implemented US-029. Ran verification.", harness.text("#runOutputTextArea"));
    }

    @Test
    void appShellForcesRawOutputWhenLatestRunUsedCopilot() throws Exception {
        Path storageDirectory = tempDir.resolve("copilot-run-output-storage");
        Path repository = createGitRepository("copilot-run-output-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedRunOutputArtifacts(
                activeProject,
                "run-copilot-1",
                "US-044",
                "Copilot raw output",
                "",
                ""
        );
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-copilot-1",
                projectId,
                "US-044",
                "SUCCEEDED",
                "2026-03-18T14:00:00Z",
                "2026-03-18T14:02:00Z",
                "POWERSHELL",
                repository.toString(),
                2233L,
                0,
                List.of("copilot", "-sp", "prompt", "--no-ask-user", "--allow-all-tools"),
                new LocalMetadataStorage.RunArtifactPaths(
                        activeProject.promptsDirectoryPath().resolve("US-044").resolve("run-copilot-1").resolve("prompt.txt")
                                .toString(),
                        activeProject.logsDirectoryPath().resolve("US-044").resolve("run-copilot-1").resolve("stdout.log")
                                .toString(),
                        activeProject.logsDirectoryPath().resolve("US-044").resolve("run-copilot-1").resolve("stderr.log")
                                .toString(),
                        null,
                        activeProject.artifactsDirectoryPath().resolve("US-044").resolve("run-copilot-1")
                                .resolve("attempt-summary.json").toString(),
                        activeProject.artifactsDirectoryPath().resolve("US-044").resolve("run-copilot-1")
                                .resolve("assistant-summary.txt").toString()
                )
        )));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertTrue(harness.isSelected("#rawOutputViewRadioButton"));
        assertFalse(harness.isVisible("#assistantSummaryViewRadioButton"));
        assertFalse(harness.isVisible("#structuredRunOutputViewRadioButton"));
        assertTrue(harness.isVisible("#runOutputTextArea"));
        assertEquals("Copilot raw output", harness.text("#runOutputTextArea"));
    }

    @Test
    void appShellDisplaysReasoningItemsAsPlainTextInStructuredOutput() throws Exception {
        Path storageDirectory = tempDir.resolve("structured-reasoning-storage");
        Path repository = createGitRepository("structured-reasoning-repo");
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        AppShellController controller = harness.getRequiredRootController(AppShellController.class);
        renderStructuredRunOutput(controller, """
                {"type":"item.completed","item":{"id":"item_reasoning","type":"reasoning","status":"completed","text":"Thinking through the remaining edge cases."}}
                """);

        harness.waitUntil(() -> harness.exists("#structuredRunOutputEntryitemitemreasoning"));
        String reasoningOutput = harness.textContent("#structuredRunOutputEntryitemitemreasoning");
        String normalizedReasoningOutput = reasoningOutput.toLowerCase();
        assertTrue(normalizedReasoningOutput.contains("reasoning"));
        assertTrue(reasoningOutput.contains("Thinking through the remaining edge cases."));
        assertFalse(reasoningOutput.contains("\"text\""));
    }

    @Test
    void appShellDisplaysWebSearchAndFileChangeItemsInStructuredOutput() throws Exception {
        Path storageDirectory = tempDir.resolve("structured-web-search-storage");
        Path repository = createGitRepository("structured-web-search-repo");
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        AppShellController controller = harness.getRequiredRootController(AppShellController.class);
        renderStructuredRunOutput(controller, """
                {"type":"item.completed","item":{"id":"item_search","type":"web_search","status":"completed","query":"spotless maven plugin googleJavaFormatVersion property","action":{"type":"search","query":"spotless maven plugin googleJavaFormatVersion property","queries":["spotless maven plugin googleJavaFormatVersion property"]}}}
                {"type":"item.completed","item":{"id":"item_file","type":"file_change","status":"completed","changes":[{"path":"C:\\\\Users\\\\james\\\\workspace\\\\project9\\\\pom.xml","kind":"update"},{"path":"C:\\\\Users\\\\james\\\\workspace\\\\project9\\\\src\\\\test\\\\java\\\\com\\\\example\\\\project9\\\\GreetingCliTest.java","kind":"update"}]}}
                """);

        harness.waitUntil(() -> harness.exists("#structuredRunOutputEntryitemitemsearch")
                && harness.exists("#structuredRunOutputEntryitemitemfile"));

        String webSearchOutput = harness.textContent("#structuredRunOutputEntryitemitemsearch");
        assertTrue(webSearchOutput.contains("web search"));
        assertTrue(webSearchOutput.contains("spotless maven plugin googleJavaFormatVersion property"));
        assertTrue(webSearchOutput.contains("Action search"));
        assertFalse(webSearchOutput.contains("\"queries\""));

        String fileChangeOutput = harness.textContent("#structuredRunOutputEntryitemitemfile");
        assertTrue(fileChangeOutput.contains("File changes"));
        assertTrue(fileChangeOutput.contains("update C:\\Users\\james\\workspace\\project9\\pom.xml"));
        assertTrue(fileChangeOutput.contains("update C:\\Users\\james\\workspace\\project9\\src\\test\\java\\com\\example\\project9\\GreetingCliTest.java"));
        assertFalse(fileChangeOutput.contains("\"changes\""));
    }

    @Test
    void appShellKeepsStructuredCommandOutputGroupedAndScrollAnchoredDuringLiveUpdates() throws Exception {
        Path storageDirectory = tempDir.resolve("structured-live-storage");
        Path repository = createGitRepository("structured-live-repo");
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);
        harness.clickOn("#executionNavButton");
        harness.clickOn("#structuredRunOutputViewRadioButton");

        AppShellController controller = harness.getRequiredRootController(AppShellController.class);
        renderStructuredRunOutput(controller, """
                {"type":"item.started","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"","exit_code":null,"status":"in_progress"}}
                {"type":"item.updated","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6","exit_code":null,"status":"in_progress"}}
                """);

        harness.waitUntil(() -> harness.exists("#structuredRunOutputToggleitemitemcmd"));
        assertEquals(1, harness.nodeCount("#structuredRunOutputEntryitemitemcmd"));

        String collapsedOutput = harness.textContent("#structuredRunOutputBodyitemitemcmd");
        assertTrue(collapsedOutput.contains("..."));
        assertTrue(collapsedOutput.contains("line 6"));
        assertFalse(collapsedOutput.contains("line 1"));

        harness.clickOn("#structuredRunOutputToggleitemitemcmd");
        harness.waitUntil(() -> harness.textContent("#structuredRunOutputBodyitemitemcmd").contains("line 1"));
        harness.setScrollValue("#structuredRunOutputBodyitemitemcmd", 0.0);

        renderStructuredRunOutput(controller, """
                {"type":"item.started","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"","exit_code":null,"status":"in_progress"}}
                {"type":"item.updated","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6","exit_code":null,"status":"in_progress"}}
                {"type":"item.updated","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6\\nline 7\\nline 8","exit_code":null,"status":"in_progress"}}
                """);

        harness.waitUntil(() -> harness.textContent("#structuredRunOutputBodyitemitemcmd").contains("line 8"));
        assertEquals(1, harness.nodeCount("#structuredRunOutputEntryitemitemcmd"));
        assertTrue(harness.scrollValue("#structuredRunOutputBodyitemitemcmd") < 0.15);

        harness.setScrollValue("#structuredRunOutputBodyitemitemcmd", 1.0);

        renderStructuredRunOutput(controller, """
                {"type":"item.started","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"","exit_code":null,"status":"in_progress"}}
                {"type":"item.updated","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6","exit_code":null,"status":"in_progress"}}
                {"type":"item.updated","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6\\nline 7\\nline 8","exit_code":null,"status":"in_progress"}}
                {"type":"item.completed","item":{"id":"item_cmd","type":"command_execution","command":"cmd.exe /c echo streaming","aggregated_output":"line 1\\nline 2\\nline 3\\nline 4\\nline 5\\nline 6\\nline 7\\nline 8\\nline 9\\nline 10","exit_code":0,"status":"completed"}}
                """);

        harness.waitUntil(() -> harness.textContent("#structuredRunOutputBodyitemitemcmd").contains("line 10"));
        assertEquals(1, harness.nodeCount("#structuredRunOutputEntryitemitemcmd"));

        harness.clickOn("#structuredRunOutputToggleitemitemcmd");
        String collapsedFinalOutput = harness.textContent("#structuredRunOutputBodyitemitemcmd");
        assertTrue(collapsedFinalOutput.contains("..."));
        assertTrue(collapsedFinalOutput.contains("line 10"));
        assertFalse(collapsedFinalOutput.contains("line 5"));

        String baselineEvents = structuredAgentMessageEvents(120);
        renderStructuredRunOutput(controller, baselineEvents);
        harness.waitUntil(() -> harness.exists("#structuredRunOutputEntryitemitem120"));
        harness.setScrollValue("#runOutputStructuredScrollPane", 1.0);
        double bottomScrollBeforeAppend = harness.scrollValue("#runOutputStructuredScrollPane");

        String appendedEvents = baselineEvents + System.lineSeparator() + structuredAgentMessageEvent(121);
        renderStructuredRunOutput(controller, appendedEvents);
        harness.waitUntil(() -> harness.exists("#structuredRunOutputEntryitemitem121"));
        harness.waitUntil(() -> harness.scrollValue("#runOutputStructuredScrollPane")
                >= Math.max(bottomScrollBeforeAppend - 0.1, 0.0));

        harness.setScrollValue("#runOutputStructuredScrollPane", 0.0);
        double topScrollBeforeAppend = harness.scrollValue("#runOutputStructuredScrollPane");
        String appendedEventsAgain = appendedEvents + System.lineSeparator() + structuredAgentMessageEvent(122);
        renderStructuredRunOutput(controller, appendedEventsAgain);
        harness.waitUntil(() -> harness.exists("#structuredRunOutputEntryitemitem122"));
        harness.waitUntil(() -> harness.scrollValue("#runOutputStructuredScrollPane")
                <= Math.min(topScrollBeforeAppend + 0.1, 1.0));
    }

    @Test
    void appShellShowsPersistedRunHistoryAndOpensStoredArtifactsAcrossRestarts() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("run-history-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoryProgressArtifacts(repository, runHistoryMarkdown(), runHistoryPrdJson());
        seedRunOutputArtifacts(
                activeProject,
                "run-history-1",
                "US-035",
                "{\"event\":\"assistant_message.completed\",\"content\":\"First pass\"}",
                "",
                "First pass summary"
        );
        seedRunOutputArtifacts(
                activeProject,
                "run-history-2",
                "US-035",
                "Retry stdout",
                "Retry stderr",
                "Retry failed summary"
        );
        Files.writeString(activeProject.promptsDirectoryPath().resolve("US-035").resolve("run-history-1")
                .resolve("prompt.txt"), "Prompt for first attempt");
        Files.writeString(activeProject.promptsDirectoryPath().resolve("US-035").resolve("run-history-2")
                .resolve("prompt.txt"), "Prompt for retry attempt");

        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(
                new LocalMetadataStorage.RunMetadataRecord(
                        "run-history-1",
                        projectId,
                        "US-035",
                        "SUCCEEDED",
                        "2026-03-16T09:11:00Z",
                        "2026-03-16T09:12:00Z",
                        "POWERSHELL",
                        repository.toString(),
                        1234L,
                        0,
                        List.of("powershell.exe", "-NoLogo"),
                        "ralph/run-history",
                        "CREATED",
                        new LocalMetadataStorage.RunArtifactPaths(
                                activeProject.promptsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("prompt.txt").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("stdout.log").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("stderr.log").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("structured-events.jsonl").toString(),
                                activeProject.artifactsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("attempt-summary.json").toString(),
                                activeProject.artifactsDirectoryPath().resolve("US-035").resolve("run-history-1")
                                        .resolve("assistant-summary.txt").toString()
                        )
                ),
                new LocalMetadataStorage.RunMetadataRecord(
                        "run-history-2",
                        projectId,
                        "US-035",
                        "FAILED",
                        "2026-03-16T09:16:00Z",
                        "2026-03-16T09:18:00Z",
                        "POWERSHELL",
                        repository.toString(),
                        2345L,
                        1,
                        List.of("powershell.exe", "-NoLogo"),
                        "ralph/run-history",
                        "SWITCHED",
                        new LocalMetadataStorage.RunArtifactPaths(
                                activeProject.promptsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("prompt.txt").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("stdout.log").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("stderr.log").toString(),
                                activeProject.logsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("structured-events.jsonl").toString(),
                                activeProject.artifactsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("attempt-summary.json").toString(),
                                activeProject.artifactsDirectoryPath().resolve("US-035").resolve("run-history-2")
                                        .resolve("assistant-summary.txt").toString()
                        )
                )
        ));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertTrue(harness.text("#runHistorySummaryLabel").contains("2 persisted attempts"));
        assertTrue(harness.text("#runHistoryDetailLabel").contains("Latest: US-035 | FAILED"));

        String historyText = harness.textContent("#runHistoryEntriesContainer");
        assertTrue(historyText.contains("US-035 | View Run History and Artifacts"));
        assertTrue(historyText.contains("Run run-history-2 | FAILED"));
        assertTrue(historyText.contains("Branch ralph/run-history (SWITCHED)"));
        assertTrue(historyText.contains("Commit commit-run-history-1 | US-035: View Run History and Artifacts"));
        assertTrue(historyText.contains("Preset Ralph/Codex Story Implementation v1"));
        assertTrue(historyText.contains("Preset Ralph/Codex Retry and Fix v1"));

        harness.clickOn("#runHistoryOpenPromptrunhistory2");
        assertEquals("US-035 | Prompt | run-history-2", harness.text("#runHistoryArtifactSummaryLabel"));
        assertTrue(harness.text("#runHistoryArtifactPathField").endsWith("prompt.txt"));
        assertEquals("Prompt for retry attempt", harness.text("#runHistoryArtifactTextArea"));

        harness.clickOn("#runHistoryOpenStdoutrunhistory2");
        assertEquals("Retry stdout", harness.text("#runHistoryArtifactTextArea"));

        harness.clickOn("#runHistoryOpenAssistantSummaryrunhistory1");
        assertEquals("First pass summary", harness.text("#runHistoryArtifactTextArea"));

        harness.clickOn("#runHistoryOpenArtifactSummaryrunhistory1");
        assertTrue(harness.text("#runHistoryArtifactTextArea").contains("\"runId\": \"run-history-1\""));

        harness.closeShell();
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertTrue(harness.text("#runHistorySummaryLabel").contains("2 persisted attempts"));
        harness.clickOn("#runHistoryOpenAssistantSummaryrunhistory2");
        assertEquals("Retry failed summary", harness.text("#runHistoryArtifactTextArea"));
    }

    @Test
    void appShellShowsStoryProgressDashboardCountsForPersistedStories() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("story-progress-dashboard-repo");
        seedStoryProgressArtifacts(repository, storyProgressDashboardMarkdown(), """
                {
                  "name": "Story Progress Dashboard",
                  "branchName": "ralph/story-progress-dashboard",
                  "description": "Track persisted story states in the execution dashboard.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-028",
                      "title": "Show pending story counts",
                      "description": "As a user, I want pending stories counted.",
                      "acceptanceCriteria": [
                        "Pending stories appear in the dashboard.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Pending stories appear in the dashboard.",
                      "ralphyStatus": "READY"
                    },
                    {
                      "id": "US-029",
                      "title": "Show blocked story counts",
                      "description": "As a user, I want blocked stories counted.",
                      "acceptanceCriteria": [
                        "Blocked stories appear in the dashboard.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Blocked stories appear in the dashboard.",
                      "ralphyStatus": "BLOCKED"
                    },
                    {
                      "id": "US-030",
                      "title": "Show the current running story",
                      "description": "As a user, I want the running story highlighted.",
                      "acceptanceCriteria": [
                        "The running story appears as the current story.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 3,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "The running story appears as the current story.",
                      "ralphyStatus": "RUNNING"
                    },
                    {
                      "id": "US-031",
                      "title": "Show passed story counts",
                      "description": "As a user, I want passed stories counted.",
                      "acceptanceCriteria": [
                        "Passed stories appear in the dashboard.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 4,
                      "passes": true,
                      "dependsOn": [],
                      "completionNotes": "Completed in a prior run.",
                      "outcome": "Passed stories appear in the dashboard.",
                      "ralphyStatus": "PASSED"
                    },
                    {
                      "id": "US-032",
                      "title": "Show failed story counts",
                      "description": "As a user, I want failed stories counted.",
                      "acceptanceCriteria": [
                        "Failed stories appear in the dashboard.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 5,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Failed stories appear in the dashboard.",
                      "ralphyStatus": "FAILED"
                    }
                  ]
                }
                """);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");

        assertEquals("Execution running", harness.text("#storyProgressSummaryLabel"));
        assertTrue(harness.text("#storyProgressDetailLabel").contains("US-030"));
        assertTrue(harness.text("#storyProgressCurrentStoryLabel").contains("Running | US-030"));
        assertTrue(harness.text("#storyProgressOverallCountsLabel").contains("5 total stories"));
        assertEquals("1", harness.text("#storyProgressPendingCountLabel"));
        assertEquals("1", harness.text("#storyProgressBlockedCountLabel"));
        assertEquals("1", harness.text("#storyProgressRunningCountLabel"));
        assertEquals("1", harness.text("#storyProgressPassedCountLabel"));
        assertEquals("1", harness.text("#storyProgressFailedCountLabel"));
        assertEquals("0", harness.text("#storyProgressPausedCountLabel"));
    }

    @Test
    void appShellRestoresPausedStoryProgressDashboardStateAfterRestart() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("paused-story-progress-repo");
        seedStoryProgressArtifacts(repository, pausedStoryProgressMarkdown(), """
                {
                  "name": "Paused Story Progress",
                  "branchName": "ralph/paused-story-progress",
                  "description": "Restore paused dashboard state from persisted metadata.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-028",
                      "title": "Restore the paused story",
                      "description": "As a user, I want a resumable story restored.",
                      "acceptanceCriteria": [
                        "The paused story appears after restart.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "The paused story appears after restart.",
                      "ralphyStatus": "RUNNING"
                    },
                    {
                      "id": "US-029",
                      "title": "Keep pending stories visible",
                      "description": "As a user, I want pending stories preserved.",
                      "acceptanceCriteria": [
                        "Pending story counts survive restart.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Pending story counts survive restart.",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """);
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-paused-1",
                projectId,
                "US-028",
                "RUNNING",
                "2026-03-15T20:00:00Z",
                null
        )));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertEquals("Execution paused", harness.text("#storyProgressSummaryLabel"));
        assertTrue(harness.text("#storyProgressDetailLabel").contains("US-028"));
        assertTrue(harness.text("#storyProgressCurrentStoryLabel").contains("Paused | US-028"));
        assertTrue(harness.text("#storyProgressOverallCountsLabel").contains("2 total stories"));
        assertEquals("1", harness.text("#storyProgressPendingCountLabel"));
        assertEquals("0", harness.text("#storyProgressBlockedCountLabel"));
        assertEquals("0", harness.text("#storyProgressRunningCountLabel"));
        assertEquals("0", harness.text("#storyProgressPassedCountLabel"));
        assertEquals("0", harness.text("#storyProgressFailedCountLabel"));
        assertEquals("1", harness.text("#storyProgressPausedCountLabel"));
    }

    @Test
    void appShellCanCaptureRevisitAndRestorePrdInterviewDraftAnswers() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("prd-interview-repo");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");

        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("0 of 7 planning inputs captured"));
        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Starter Prompt", harness.text("#prdInterviewTitleLabel"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("Quality Gates"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("User Stories"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("Functional Requirements"));
        assertTrue(harness.text("#prdInterviewDraftStateLabel")
                .contains(".ralph-tui/project-metadata.json"));

        harness.enterText("#prdInterviewAnswerArea", "Ralphy should guide users through PRD drafting.");
        harness.clickOn("#prdInterviewNextButton");

        assertEquals("Question 2 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("1 of 7 planning inputs captured"));
        assertTrue(harness.text("#prdInterviewDraftStateLabel").contains("Answer saved."));

        harness.enterText("#prdInterviewAnswerArea", "Primary users are developers running one repository at a time.");
        harness.clickOn("#prdInterviewQuestionOverviewContext");

        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Ralphy should guide users through PRD drafting.", harness.text("#prdInterviewAnswerArea"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("2 of 7 planning inputs captured"));

        harness.closeShell();
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertEquals("prd-interview-repo", harness.text("#activeProjectNameLabel"));
        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Ralphy should guide users through PRD drafting.", harness.text("#prdInterviewAnswerArea"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("2 of 7 planning inputs captured"));
        assertTrue(harness.text("#prdInterviewDraftStateLabel").contains("Draft restored."));
    }

    @Test
    void appShellCanGenerateAndRegenerateMarkdownPrdFromInterviewAnswers() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("generated-prd-repo");
        Path generatedPrdPath = repository.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#prdEditorNavButton");

        harness.enterText("#prdInterviewAnswerArea", "Repository-owned PRD generation for Codex-driven workflows.");
        harness.clickOn("#prdInterviewNextButton");
        harness.enterText("#prdInterviewAnswerArea", "Developers operating one repository at a time.");
        harness.clickOn("#prdInterviewQuestionGoalsOutcomes");
        harness.enterText("#prdInterviewAnswerArea", "Generate reviewable Markdown PRDs from interview answers.");
        harness.clickOn("#prdInterviewQuestionQualityGates");
        harness.enterText("#prdInterviewAnswerArea", ".\\mvnw.cmd clean verify jacoco:report");
        harness.clickOn("#prdInterviewQuestionUserStories");
        harness.enterText("#prdInterviewAnswerArea",
                "US-010: Edit the active PRD | Refine the generated Markdown before execution.\n"
                        + "US-018: Generate Markdown PRD from Interview Answers | Save reviewable Markdown to the active project.");

        harness.clickOn("#generatePrdButton");

        String initialPreview = harness.text("#prdDocumentPreviewArea");
        assertTrue(harness.text("#prdDocumentStateLabel").contains("Generated PRD saved to"));
        assertTrue(harness.text("#prdDocumentPathField").endsWith("active-prd.md"));
        assertTrue(harness.isEditable("#prdDocumentPreviewArea"));
        assertTrue(harness.isDisabled("#savePrdDocumentButton"));
        assertTrue(initialPreview.contains("## Overview"));
        assertTrue(initialPreview.contains("## Goals"));
        assertTrue(initialPreview.contains("## Quality Gates"));
        assertTrue(initialPreview.contains("## User Stories"));
        assertTrue(initialPreview.indexOf("### US-010: Edit the active PRD")
                < initialPreview.indexOf("### US-018: Generate Markdown PRD from Interview Answers"));
        assertTrue(Files.exists(generatedPrdPath));
        assertEquals(normalizeLineEndings(initialPreview), normalizeLineEndings(Files.readString(generatedPrdPath)));

        harness.enterText("#prdInterviewAnswerArea",
                "US-010: Edit the active PRD | Refine the generated Markdown before execution.\n"
                        + "US-018: Generate Markdown PRD from Interview Answers | Save reviewable Markdown to the active project.\n"
                        + "US-019: Validate the regenerated PRD | Confirm the latest draft replaces the prior Markdown.");
        harness.clickOn("#generatePrdButton");

        String regeneratedPreview = harness.text("#prdDocumentPreviewArea");
        assertTrue(regeneratedPreview.contains("### US-019: Validate the regenerated PRD"));
        assertEquals(normalizeLineEndings(regeneratedPreview), normalizeLineEndings(Files.readString(generatedPrdPath)));
    }

    @Test
    void appShellCanImportAndExportMarkdownPrdsThroughThePrdEditor() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("import-export-prd-repo");
        Path importedMarkdownPath = tempDir.resolve("external-import.md");
        Path exportedMarkdownPath = tempDir.resolve("exports").resolve("shared-prd.md");
        Path activePrdPath = repository.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md");
        String importedMarkdown = "# PRD: External Plan\r\n\r\n## Overview\r\nImported from another tool.\r\n";
        Files.writeString(importedMarkdownPath, importedMarkdown);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);
        MarkdownPrdFileChooser markdownPrdFileChooser = harness.getRequiredBean(MarkdownPrdFileChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#prdEditorNavButton");

        markdownPrdFileChooser.queueSelectionForTest(importedMarkdownPath);
        harness.clickOn("#importPrdDocumentButton");

        assertEquals(importedMarkdown.replace("\r\n", "\n"), harness.text("#prdDocumentPreviewArea"));
        assertTrue(harness.text("#prdDocumentStateLabel").contains("Imported Markdown PRD from"));
        assertEquals(importedMarkdown, Files.readString(activePrdPath));
        assertTrue(Files.readString(repository.resolve(".ralph-tui").resolve("project-metadata.json"))
                .contains(importedMarkdownPath.toAbsolutePath().normalize().toString().replace("\\", "\\\\")));

        markdownPrdFileChooser.queueSelectionForTest(exportedMarkdownPath);
        harness.clickOn("#exportPrdDocumentButton");

        assertTrue(harness.text("#prdDocumentStateLabel").contains("Exported Markdown PRD to"));
        assertEquals(importedMarkdown, Files.readString(exportedMarkdownPath));
        String metadataDocument = Files.readString(repository.resolve(".ralph-tui").resolve("project-metadata.json"));
        assertTrue(metadataDocument.contains(importedMarkdownPath.toAbsolutePath().normalize().toString().replace("\\", "\\\\")));
        assertTrue(metadataDocument.contains(exportedMarkdownPath.toAbsolutePath().normalize().toString().replace("\\", "\\\\")));
    }

    @Test
    void appShellCanImportPrdJsonAndSurfaceMarkdownConflictsClearly() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("import-prd-json-ui-repo");
        Path activePrdPath = repository.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md");
        Path importedPrdJsonPath = tempDir.resolve("external-import-prd.json");
        Files.createDirectories(activePrdPath.getParent());
        Files.writeString(activePrdPath, """
                # PRD: Task Sync Plan

                ## Overview
                Import tracker changes safely.

                ## Goals
                - Keep Markdown authoritative

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-022: Sync PRD stories
                **Outcome:** Internal tasks are created from the active PRD.

                ### US-023: Export prd json
                **Outcome:** Internal task state can be shared outside the app.

                ## Scope Boundaries
                ### In Scope
                - Tracker import

                ### Out of Scope
                - Editing tracker files in place
                """);
        Files.writeString(importedPrdJsonPath, """
                {
                  "name": "Task Sync Plan",
                  "branchName": "ralph/task-sync-plan",
                  "description": "Import tracker changes safely.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-022",
                      "title": "Sync PRD tracker stories",
                      "description": "As a user, I want tracker edits reflected.",
                      "acceptanceCriteria": [
                        "Tracker wording changed outside the app.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": true,
                      "dependsOn": [],
                      "completionNotes": "Completed in tracker.",
                      "outcome": "Tracker wording changed outside the app.",
                      "ralphyStatus": "COMPLETED"
                    },
                    {
                      "id": "US-023",
                      "title": "Export prd json",
                      "description": "As a user, I want exports preserved.",
                      "acceptanceCriteria": [
                        "Internal task state can be shared outside the app.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Internal task state can be shared outside the app.",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);
        PrdJsonFileChooser prdJsonFileChooser = harness.getRequiredBean(PrdJsonFileChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#prdEditorNavButton");

        prdJsonFileChooser.queueSelectionForTest(importedPrdJsonPath);
        harness.clickOn("#importPrdJsonButton");

        String importMessage = harness.text("#prdDocumentStateLabel");
        assertTrue(importMessage.contains("Imported compatible prd.json"));
        assertTrue(importMessage.contains("Conflicts:"));
        assertTrue(importMessage.contains("US-022 differs between active Markdown and imported prd.json"));
        assertTrue(harness.text("#prdDocumentPreviewArea").contains("### US-022: Sync PRD stories"));

        ActiveProjectService activeProjectService = harness.getRequiredBean(ActiveProjectService.class);
        assertEquals(PrdTaskStatus.COMPLETED,
                activeProjectService.prdTaskState().orElseThrow().taskById("US-022").orElseThrow().status());
        assertTrue(Files.readString(repository.resolve(".ralph-tui").resolve("prd-json").resolve("prd.json"))
                .contains("\"title\" : \"Sync PRD stories\""));
    }

    @Test
    void appShellCanEditSaveAndRestoreImportedMarkdownPrdsWithoutReformatting() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("editable-prd-repo");
        Path importedPrdPath = repository.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md");
        Files.createDirectories(importedPrdPath.getParent());
        Files.writeString(importedPrdPath,
                "# PRD: Imported Plan\r\n"
                        + "\r\n"
                        + "## Overview\r\n"
                        + "Keep the existing paragraph spacing exactly as written.\r\n"
                        + "\r\n"
                        + "### Notes\r\n"
                        + "- Preserve manual bullet wording\r\n");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#prdEditorNavButton");

        assertTrue(harness.isEditable("#prdDocumentPreviewArea"));
        assertTrue(harness.text("#prdDocumentStateLabel").contains("saved Markdown PRD"));
        assertTrue(harness.isDisabled("#savePrdDocumentButton"));

        String importedPreview = harness.text("#prdDocumentPreviewArea");
        String editedPreview = importedPreview + "\n### Manual Notes\n- Keep appended formatting";
        harness.enterText("#prdDocumentPreviewArea", editedPreview);

        assertTrue(harness.text("#prdDocumentStateLabel").contains("unsaved changes"));
        assertFalse(harness.isDisabled("#savePrdDocumentButton"));

        harness.clickOn("#savePrdDocumentButton");

        assertTrue(harness.text("#prdDocumentStateLabel").contains("Markdown PRD saved to"));
        assertTrue(harness.isDisabled("#savePrdDocumentButton"));
        assertEquals(editedPreview, normalizeLineEndings(Files.readString(importedPrdPath)));

        harness.closeShell();
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertEquals("editable-prd-repo", harness.text("#activeProjectNameLabel"));
        assertEquals(editedPreview, harness.text("#prdDocumentPreviewArea"));
        assertTrue(harness.isEditable("#prdDocumentPreviewArea"));
        assertTrue(harness.text("#prdDocumentStateLabel").contains("saved Markdown PRD"));
    }

    @Test
    void appShellShowsPrdValidationErrorsInExecutionAndClearsThemAfterSavingAValidPrd() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("prd-validation-ui-repo");
        Path importedPrdPath = repository.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md");
        Files.createDirectories(importedPrdPath.getParent());
        Files.writeString(importedPrdPath, """
                # PRD: Broken execution plan

                ## Overview
                Missing validation prerequisites.

                ## User Stories
                ### Story 1: Missing US identifier
                **Outcome:** This should be blocked.

                ## Scope Boundaries
                ### In Scope
                - Structural validation

                ### Out of Scope
                - Execution launcher work
                """);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#executionNavButton");

        assertEquals("PRD validation failed", harness.text("#prdValidationSummaryLabel"));
        assertTrue(harness.text("#prdValidationDetailLabel")
                .contains("Execution is blocked while structural validation errors remain."));
        String validationErrors = harness.text("#prdValidationErrorsLabel");
        assertTrue(validationErrors.contains("Section Goals"));
        assertTrue(validationErrors.contains("Section Quality Gates"));
        assertTrue(validationErrors.contains("Story heading `### Story 1: Missing US identifier`"));

        harness.clickOn("#prdEditorNavButton");
        harness.enterText("#prdDocumentPreviewArea", """
                # PRD: Valid execution plan

                ## Overview
                Validate PRDs before execution starts.

                ## Goals
                - Block malformed PRDs before execution

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report
                - Automated JavaFX UI tests

                ## User Stories
                ### US-020: Validate PRD structure before execution
                **Outcome:** Malformed task definitions do not reach the execution loop.

                ## Scope Boundaries
                ### In Scope
                - Structural PRD validation

                ### Out of Scope
                - Codex launch orchestration
                """);
        harness.clickOn("#savePrdDocumentButton");
        harness.clickOn("#executionNavButton");

        assertEquals("PRD ready for execution", harness.text("#prdValidationSummaryLabel"));
        assertTrue(harness.text("#prdValidationDetailLabel")
                .contains("required sections, a Quality Gates section, and valid story headers"));
        assertFalse(harness.isVisible("#prdValidationErrorsLabel"));
    }

    @Test
    void appShellCanOpenGitRepositoriesAndRejectNonGitFolders() throws Exception {
        Path nonGitDirectory = Files.createDirectory(tempDir.resolve("plain-folder"));
        Path gitRepository = createGitRepository("sample-repo");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(nonGitDirectory);
        harness.clickOn("#openRepositoryButton");

        assertEquals("Selected folder is not a Git repository: " + nonGitDirectory.toAbsolutePath().normalize(),
                harness.text("#projectValidationMessageLabel"));
        assertEquals("No active repository selected.", harness.text("#activeProjectNameLabel"));
        assertEquals("No active project selected.", harness.text("#activeProjectStatusLabel"));

        repositoryDirectoryChooser.queueSelectionForTest(gitRepository);
        harness.clickOn("#openRepositoryButton");

        Path expectedRepositoryPath = gitRepository.toAbsolutePath().normalize();
        assertEquals(gitRepository.getFileName().toString(), harness.text("#activeProjectNameLabel"));
        assertEquals(expectedRepositoryPath.toString(), harness.text("#activeProjectPathLabel"));
        assertEquals("", harness.text("#projectValidationMessageLabel"));
        assertEquals(gitRepository.getFileName().toString(), harness.text("#activeProjectStatusLabel"));
        assertEquals(expectedRepositoryPath,
                harness.getRequiredBean(ActiveProjectService.class).activeProject().orElseThrow().repositoryPath());
    }

    @Test
    void appShellCanCreateNewGitRepositories() throws Exception {
        Path parentDirectory = Files.createDirectory(tempDir.resolve("new-projects"));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);
        GitRepositoryInitializer gitRepositoryInitializer = harness.getRequiredBean(GitRepositoryInitializer.class);

        harness.clickOn("#projectsNavButton");
        harness.clickOn("#createRepositoryButton");
        assertEquals("Enter a project folder name.", harness.text("#projectValidationMessageLabel"));

        gitRepositoryInitializer.queueSuccessForTest();
        repositoryDirectoryChooser.queueSelectionForTest(parentDirectory);
        harness.enterText("#newProjectNameField", "starter-repo");
        harness.clickOn("#createRepositoryButton");

        Path expectedRepositoryPath = parentDirectory.resolve("starter-repo").toAbsolutePath().normalize();
        assertEquals("starter-repo", harness.text("#activeProjectNameLabel"));
        assertEquals(expectedRepositoryPath.toString(), harness.text("#activeProjectPathLabel"));
        assertEquals("", harness.text("#projectValidationMessageLabel"));
        assertEquals("starter-repo", harness.text("#activeProjectStatusLabel"));
        assertEquals("", harness.text("#newProjectNameField"));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".git")));
        assertTrue(Files.exists(expectedRepositoryPath.resolve(".ralph-tui").resolve("project-metadata.json")));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".ralph-tui").resolve("prds")));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".ralph-tui").resolve("prd-json")));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".ralph-tui").resolve("prompts")));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".ralph-tui").resolve("logs")));
        assertTrue(Files.isDirectory(expectedRepositoryPath.resolve(".ralph-tui").resolve("artifacts")));
        assertEquals(expectedRepositoryPath,
                harness.getRequiredBean(ActiveProjectService.class).activeProject().orElseThrow().repositoryPath());
    }

    @Test
    void appShellRestoresTheLastActiveRepositoryAndShowsResumableRunStateOnStartup() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("restored-repo");
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-restore-1",
                projectId,
                "US-011",
                "RUNNING",
                "2026-03-15T20:00:00Z",
                null
        )));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        Path expectedRepositoryPath = repository.toAbsolutePath().normalize();
        assertEquals("restored-repo", harness.text("#activeProjectNameLabel"));
        assertEquals(expectedRepositoryPath.toString(), harness.text("#activeProjectPathLabel"));
        assertEquals("restored-repo", harness.text("#activeProjectStatusLabel"));
        assertEquals("", harness.text("#projectValidationMessageLabel"));
        assertEquals("Resumable run available", harness.text("#executionOverviewHeadlineLabel"));
        assertEquals("Story US-011 from run run-restore-1 is in RUNNING state and can be resumed.",
                harness.text("#executionOverviewDetailLabel"));
        assertEquals(expectedRepositoryPath,
                harness.getRequiredBean(ActiveProjectService.class).activeProject().orElseThrow().repositoryPath());
    }

    @Test
    void appShellShowsClearRecoveryMessageWhenStoredRepositoryIsMissingOnStartup() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("missing-repo");
        seedStoredProject(storageDirectory, repository);
        Files.delete(repository.resolve(".git"));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        Path expectedRepositoryPath = repository.toAbsolutePath().normalize();
        assertEquals("No active repository selected.", harness.text("#activeProjectNameLabel"));
        assertEquals("No active project selected.", harness.text("#activeProjectStatusLabel"));
        assertEquals("Last active repository could not be restored because it is missing or no longer "
                        + "a Git repository: " + expectedRepositoryPath
                        + ". Open an existing repository or create a new one to continue.",
                harness.text("#projectValidationMessageLabel"));
        assertEquals("No active project", harness.text("#executionOverviewHeadlineLabel"));
        assertEquals("Choose or restore a project to view resumable or reviewable run state.",
                harness.text("#executionOverviewDetailLabel"));
        assertTrue(harness.getRequiredBean(ActiveProjectService.class).activeProject().isEmpty());
    }

    @Test
    void appShellCanEditSaveAndReloadExecutionProfiles() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("profile-repo");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(repository);
        harness.clickOn("#openRepositoryButton");
        harness.clickOn("#agentConfigurationNavButton");

        assertEquals("Native Windows PowerShell", harness.text("#executionProfileSummaryLabel"));

        harness.clickOn("#wslExecutionProfileRadioButton");
        harness.enterText("#wslDistributionField", "Ubuntu-24.04");
        harness.enterText("#windowsPathPrefixField", "C:\\Users\\james\\workspaces");
        harness.enterText("#wslPathPrefixField", "/mnt/c/Users/james/workspaces");
        harness.clickOn("#saveExecutionProfileButton");

        assertEquals("WSL: Ubuntu-24.04 (C:\\Users\\james\\workspaces -> /mnt/c/Users/james/workspaces)",
                harness.text("#executionProfileSummaryLabel"));
        assertEquals("", harness.text("#executionProfileMessageLabel"));

        harness.closeShell();
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);
        harness.clickOn("#agentConfigurationNavButton");

        assertEquals("profile-repo", harness.text("#activeProjectNameLabel"));
        assertEquals("WSL: Ubuntu-24.04 (C:\\Users\\james\\workspaces -> /mnt/c/Users/james/workspaces)",
                harness.text("#executionProfileSummaryLabel"));
        assertEquals("Ubuntu-24.04", harness.text("#wslDistributionField"));
        assertEquals("C:\\Users\\james\\workspaces", harness.text("#windowsPathPrefixField"));
        assertEquals("/mnt/c/Users/james/workspaces", harness.text("#wslPathPrefixField"));

        harness.clickOn("#nativeExecutionProfileRadioButton");
        harness.clickOn("#saveExecutionProfileButton");

        assertEquals("Native Windows PowerShell", harness.text("#executionProfileSummaryLabel"));
        assertEquals("", harness.text("#executionProfileMessageLabel"));
    }

    @Test
    void appShellShowsOnlyLinuxExecutionConfigurationWhenRunningOnLinux() throws Exception {
        String previousHostOverride = System.getProperty("ralphy.host.os-name");
        System.setProperty("ralphy.host.os-name", "Linux");
        try {
            Path storageDirectory = tempDir.resolve("linux-storage");
            Path repository = createGitRepository("linux-profile-repo");
            seedStoredProject(storageDirectory, repository);

            harness = new JavaFxUiHarness();
            harness.launchPrimaryShell(storageDirectory);
            harness.clickOn("#agentConfigurationNavButton");

            assertEquals("Native Linux Shell", harness.text("#executionProfileSummaryLabel"));
            assertFalse(harness.isVisible("#wslExecutionProfileRadioButton"));
            assertFalse(harness.isVisible("#wslPreflightSection"));
            assertFalse(harness.isVisible("#wslExecutionProfileFieldsContainer"));
        } finally {
            if (previousHostOverride == null) {
                System.clearProperty("ralphy.host.os-name");
            } else {
                System.setProperty("ralphy.host.os-name", previousHostOverride);
            }
        }
    }

    @Test
    void appShellShowsStoredNativePreflightFailuresWithCategories() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("preflight-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoredProject(storageDirectory, repository);
        seedUserPreferencesSettings(storageDirectory).saveExecutionProfile(new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                "C:\\Users\\james\\workspaces",
                "/mnt/c/Users/james/workspaces"
        ));

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, new NativeWindowsPreflightReport(
                "2026-03-15T22:30:00Z",
                NativeWindowsPreflightReport.OverallStatus.FAIL,
                List.of(
                        new NativeWindowsPreflightReport.CheckResult(
                                "codex_cli",
                                "Codex CLI",
                                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Detected codex-cli 0.114.0."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "copilot_cli",
                                "GitHub Copilot CLI",
                                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                                NativeWindowsPreflightReport.CheckStatus.FAIL,
                                "GitHub Copilot CLI is unavailable: copilot: not found",
                                List.of(
                                        new PreflightRemediationCommand(
                                                "Install GitHub Copilot CLI",
                                                "npm install -g @github/copilot"
                                        ),
                                        new PreflightRemediationCommand(
                                                "Authenticate GitHub Copilot CLI",
                                                "copilot login"
                                        )
                                )
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "codex_auth",
                                "Codex Auth",
                                NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                                NativeWindowsPreflightReport.CheckStatus.FAIL,
                                "No stored Codex credentials were found in C:\\Users\\james\\.codex\\auth.json.",
                                List.of(
                                        new PreflightRemediationCommand("Authenticate Codex CLI", "codex login"),
                                        new PreflightRemediationCommand("Check Codex login status", "codex login status")
                                )
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "git_ready",
                                "Git Readiness",
                                NativeWindowsPreflightReport.CheckCategory.GIT,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Git can access the active repository at " + repository.toAbsolutePath().normalize() + "."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "quality_gate",
                                "Quality Gate Command",
                                NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                                NativeWindowsPreflightReport.CheckStatus.FAIL,
                                "The quality-gate command .\\mvnw.cmd clean verify jacoco:report is unavailable because "
                                        + repository.toAbsolutePath().normalize().resolve("mvnw.cmd") + " is missing.",
                                List.of(
                                        new PreflightRemediationCommand(
                                                "Restore Maven wrapper files from Git",
                                                "git -C \"" + repository.toAbsolutePath().normalize()
                                                        + "\" restore mvnw.cmd pom.xml .mvn"
                                        ),
                                        new PreflightRemediationCommand(
                                                "Run the quality gate from the repository root",
                                                "Set-Location \"" + repository.toAbsolutePath().normalize()
                                                        + "\"; .\\mvnw.cmd clean verify jacoco:report"
                                        )
                                )
                        )
                )
        ));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);
        harness.clickOn("#agentConfigurationNavButton");

        assertEquals("Native execution blocked", harness.text("#nativePreflightSummaryLabel"));
        assertTrue(harness.text("#nativePreflightDetailLabel").contains("Native Windows PowerShell runs stay blocked"));
        String checks = harness.text("#nativePreflightChecksLabel");
        assertTrue(checks.contains("PASS | Tooling | Codex CLI"));
        assertTrue(checks.contains("FAIL | Tooling | GitHub Copilot CLI"));
        assertTrue(checks.contains("FAIL | Authentication | Codex Auth"));
        assertTrue(checks.contains("PASS | Git | Git Readiness"));
        assertTrue(checks.contains("FAIL | Quality Gate | Quality Gate Command"));
        assertTrue(harness.isVisible("#nativePreflightRemediationSection"));
        String remediation = harness.textContent("#nativePreflightRemediationSection");
        assertTrue(remediation.contains("Ralphy never installs or authenticates Codex automatically"));
        assertTrue(remediation.contains("@github/copilot"));
        assertTrue(remediation.contains("codex login"));
        assertTrue(remediation.contains(".\\mvnw.cmd clean verify jacoco:report"));
        assertTrue(remediation.contains("Copy"));
    }

    @Test
    void appShellShowsStoredWslPreflightFailuresWithCategories() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("wsl-workspaces"));
        Path repository = createGitRepository(workspaceRoot, "wsl-preflight-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        seedStoredProject(storageDirectory, repository);
        seedUserPreferencesSettings(storageDirectory).saveExecutionProfile(new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                workspaceRoot.toString(),
                "/mnt/c/wsl-workspaces"
        ));

        ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
        projectMetadataInitializer.writeMetadata(activeProject);
        projectMetadataInitializer.writeWslPreflight(activeProject, new WslPreflightReport(
                "2026-03-15T23:45:00Z",
                WslPreflightReport.OverallStatus.FAIL,
                List.of(
                        new WslPreflightReport.CheckResult(
                                "wsl_distribution",
                                "WSL Distribution",
                                WslPreflightReport.CheckCategory.DISTRIBUTION,
                                WslPreflightReport.CheckStatus.PASS,
                                "Found the configured WSL distribution Ubuntu-24.04."
                        ),
                        new WslPreflightReport.CheckResult(
                                "path_mapping",
                                "Repository Path Mapping",
                                WslPreflightReport.CheckCategory.PATH_MAPPING,
                                WslPreflightReport.CheckStatus.PASS,
                                "Mapped the active repository to /mnt/c/wsl-workspaces/wsl-preflight-repo."
                        ),
                        new WslPreflightReport.CheckResult(
                                "codex_cli",
                                "Codex CLI",
                                WslPreflightReport.CheckCategory.TOOLING,
                                WslPreflightReport.CheckStatus.FAIL,
                                "Codex CLI is unavailable inside the selected WSL distribution: codex: not found",
                                List.of(
                                        new PreflightRemediationCommand(
                                                "Install Codex CLI in the selected WSL distribution",
                                                "wsl.exe --distribution \"Ubuntu-24.04\" --exec /bin/sh -lc "
                                                        + "\"npm install -g @openai/codex\""
                                        )
                                )
                        ),
                        new WslPreflightReport.CheckResult(
                                "copilot_cli",
                                "GitHub Copilot CLI",
                                WslPreflightReport.CheckCategory.TOOLING,
                                WslPreflightReport.CheckStatus.FAIL,
                                "GitHub Copilot CLI is unavailable inside the selected WSL distribution: copilot: not found",
                                List.of(
                                        new PreflightRemediationCommand(
                                                "Install GitHub Copilot CLI in the selected WSL distribution",
                                                "wsl.exe --distribution \"Ubuntu-24.04\" --exec /bin/sh -lc "
                                                        + "\"npm install -g @github/copilot\""
                                        )
                                )
                        ),
                        new WslPreflightReport.CheckResult(
                                "codex_auth",
                                "Codex Auth",
                                WslPreflightReport.CheckCategory.AUTHENTICATION,
                                WslPreflightReport.CheckStatus.FAIL,
                                "No OPENAI_API_KEY environment variable or stored Codex credentials were found in /home/test/.codex/auth.json.",
                                List.of(
                                        new PreflightRemediationCommand(
                                                "Authenticate Codex CLI in the selected WSL distribution",
                                                "wsl.exe --distribution \"Ubuntu-24.04\" --exec /bin/sh -lc "
                                                        + "\"codex login\""
                                        )
                                )
                        ),
                        new WslPreflightReport.CheckResult(
                                "git_ready",
                                "Git Readiness",
                                WslPreflightReport.CheckCategory.GIT,
                                WslPreflightReport.CheckStatus.PASS,
                                "Git can access the active repository at /mnt/c/wsl-workspaces/wsl-preflight-repo."
                        )
                )
        ));

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);
        harness.clickOn("#agentConfigurationNavButton");

        assertEquals("WSL execution blocked", harness.text("#wslPreflightSummaryLabel"));
        assertTrue(harness.text("#wslPreflightDetailLabel").contains("WSL runs stay blocked"));
        String checks = harness.text("#wslPreflightChecksLabel");
        assertTrue(checks.contains("PASS | Distribution | WSL Distribution"));
        assertTrue(checks.contains("PASS | Path Mapping | Repository Path Mapping"));
        assertTrue(checks.contains("FAIL | Tooling | Codex CLI"));
        assertTrue(checks.contains("FAIL | Tooling | GitHub Copilot CLI"));
        assertTrue(checks.contains("FAIL | Authentication | Codex Auth"));
        assertTrue(checks.contains("PASS | Git | Git Readiness"));
        assertTrue(harness.isVisible("#wslPreflightRemediationSection"));
        String remediation = harness.textContent("#wslPreflightRemediationSection");
        assertTrue(remediation.contains("wsl.exe --distribution \"Ubuntu-24.04\""));
        assertTrue(remediation.contains("@github/copilot"));
        assertTrue(remediation.contains("codex login"));
        assertTrue(remediation.contains("Copy"));
    }

    @Test
    void appShellCanRerunNativePreflightFromTheRemediationSection() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("rerun-native-preflight-repo");
        seedStoredProject(storageDirectory, repository);

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertTrue(harness.isVisible("#nativePreflightRemediationSection"));
        String initialDetail = harness.text("#nativePreflightDetailLabel");

        harness.clickOn("#rerunNativePreflightFromRemediationButton");

        assertTrue(harness.isVisible("#nativePreflightRemediationSection"));
        assertNotEquals(initialDetail, harness.text("#nativePreflightDetailLabel"));
    }

    private Path createGitRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private Path createGitRepository(Path parentDirectory, String directoryName) throws IOException {
        Path repository = Files.createDirectory(parentDirectory.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private LocalMetadataStorage seedStoredProject(Path storageDirectory, Path repository) {
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        localMetadataStorage.recordProjectActivation(new ActiveProject(repository));
        localMetadataStorage.finishSession();
        return localMetadataStorage;
    }

    private UserPreferencesSettingsService seedUserPreferencesSettings(Path storageDirectory) {
        Preferences preferences = Preferences.userRoot().node("/net/uberfoo/ai/ralphy/tests/"
                + Integer.toUnsignedString(storageDirectory.toAbsolutePath().normalize().toString().hashCode()));
        UserPreferencesSettingsService settingsService = new UserPreferencesSettingsService(preferences);
        settingsService.clearForTest();
        return settingsService;
    }

    private void seedStoryProgressArtifacts(Path repository, String markdown, String prdJson) throws IOException {
        ActiveProject activeProject = new ActiveProject(repository);
        Files.createDirectories(activeProject.prdsDirectoryPath());
        Files.createDirectories(activeProject.prdJsonDirectoryPath());
        seedQualityGateFiles(repository);
        Files.writeString(activeProject.activePrdPath(), markdown);
        Files.writeString(activeProject.activePrdJsonPath(), prdJson);
    }

    private void seedQualityGateFiles(Path repository) throws IOException {
        Files.writeString(repository.resolve("mvnw.cmd"), "@echo off\r\nexit /b 0\r\n");
        Path posixWrapper = repository.resolve("mvnw");
        Files.writeString(posixWrapper, """
                #!/usr/bin/env sh
                exit 0
                """);
        posixWrapper.toFile().setExecutable(true);
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        Files.createDirectories(repository.resolve(".mvn"));
    }

    private void seedRunOutputArtifacts(ActiveProject activeProject,
                                        String runId,
                                        String storyId,
                                        String stdout,
                                        String stderr,
                                        String assistantSummary) throws IOException {
        Path promptDirectory = activeProject.promptsDirectoryPath().resolve(storyId).resolve(runId);
        Path logDirectory = activeProject.logsDirectoryPath().resolve(storyId).resolve(runId);
        Path artifactDirectory = activeProject.artifactsDirectoryPath().resolve(storyId).resolve(runId);
        Files.createDirectories(promptDirectory);
        Files.createDirectories(logDirectory);
        Files.createDirectories(artifactDirectory);
        Files.writeString(promptDirectory.resolve("prompt.txt"), "Prompt text");
        Files.writeString(logDirectory.resolve("stdout.log"), stdout);
        Files.writeString(logDirectory.resolve("stderr.log"), stderr);
        Files.writeString(logDirectory.resolve("structured-events.jsonl"), stdout);
        Files.writeString(artifactDirectory.resolve("assistant-summary.txt"), assistantSummary);
        Files.writeString(artifactDirectory.resolve("attempt-summary.json"), """
                {
                  "runId": "%s",
                  "storyId": "%s",
                  "status": "SUCCEEDED"
                }
                """.formatted(runId, storyId));
    }

    private String runHistoryMarkdown() {
        return """
                # PRD: Run History

                ## Overview
                Restore prior story attempts and their stored artifacts in the desktop shell.

                ## Goals
                - Keep attempt history visible across restarts.
                - Let users inspect stored prompt and log artifacts from the UI.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-035: View Run History and Artifacts
                **Outcome:** Review prior attempts from the desktop shell.

                ## Scope Boundaries
                ### In Scope
                - Persisted run history and artifact viewing

                ### Out of Scope
                - Editing stored run artifacts
                """;
    }

    private String runHistoryPrdJson() {
        return """
                {
                  "name": "Run History",
                  "branchName": "ralph/run-history",
                  "description": "Restore prior story attempts and artifacts in the desktop shell.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-035",
                      "title": "View Run History and Artifacts",
                      "description": "As a user, I want to inspect prior attempts from the UI.",
                      "acceptanceCriteria": [
                        "The history view lists story attempts.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Review prior attempts from the desktop shell.",
                      "ralphyStatus": "FAILED",
                      "history": [],
                      "attempts": [
                        {
                          "runId": "run-history-1",
                          "presetId": "ralph-codex-implement-v1",
                          "presetName": "Ralph/Codex Story Implementation",
                          "presetVersion": "v1",
                          "outcome": "PASSED",
                          "queuedAt": "2026-03-16T09:10:00Z",
                          "startedAt": "2026-03-16T09:11:00Z",
                          "endedAt": "2026-03-16T09:12:00Z",
                          "detail": "Created commit commit-run-history-1 after verification passed.",
                          "commitHash": "commit-run-history-1",
                          "commitMessage": "US-035: View Run History and Artifacts"
                        },
                        {
                          "runId": "run-history-2",
                          "presetId": "ralph-codex-fix-v1",
                          "presetName": "Ralph/Codex Retry and Fix",
                          "presetVersion": "v1",
                          "outcome": "FAILED",
                          "queuedAt": "2026-03-16T09:15:00Z",
                          "startedAt": "2026-03-16T09:16:00Z",
                          "endedAt": "2026-03-16T09:18:00Z",
                          "detail": "Retry attempt failed verification and remains reviewable.",
                          "commitHash": "",
                          "commitMessage": ""
                        }
                      ],
                      "createdAt": "2026-03-16T09:00:00Z",
                      "updatedAt": "2026-03-16T09:18:00Z"
                    }
                  ]
                }
                """;
    }

    private String storyProgressDashboardMarkdown() {
        return """
                # PRD: Story Progress Dashboard

                ## Overview
                Track persisted story states in the execution dashboard.

                ## Goals
                - Show running work and overall counts clearly.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-028: Show pending story counts
                **Outcome:** Pending stories appear in the dashboard.

                ### US-029: Show blocked story counts
                **Outcome:** Blocked stories appear in the dashboard.

                ### US-030: Show the current running story
                **Outcome:** The running story appears as the current story.

                ### US-031: Show passed story counts
                **Outcome:** Passed stories appear in the dashboard.

                ### US-032: Show failed story counts
                **Outcome:** Failed stories appear in the dashboard.

                ## Scope Boundaries
                ### In Scope
                - Persisted story progress

                ### Out of Scope
                - Multi-project execution
                """;
    }

    private String pausedStoryProgressMarkdown() {
        return """
                # PRD: Paused Story Progress

                ## Overview
                Restore paused dashboard state from persisted metadata.

                ## Goals
                - Resume work with the same story focus after restart.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-028: Restore the paused story
                **Outcome:** The paused story appears after restart.

                ### US-029: Keep pending stories visible
                **Outcome:** Pending story counts survive restart.

                ## Scope Boundaries
                ### In Scope
                - Persisted dashboard state

                ### Out of Scope
                - Live log streaming
                """;
    }

    private String playAutoAdvanceMarkdown() {
        return """
                # PRD: Play Auto Advance

                ## Overview
                Continue across ready stories and skip blocked stories with a visible reason.

                ## Goals
                - Start from the next eligible story.
                - Surface why blocked stories are skipped.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-030: Skip blocked stories during Play
                **Outcome:** Blocked stories are skipped with a visible reason.

                ### US-031: Continue from the next ready story
                **Outcome:** Play starts from the next eligible story.

                ### US-032: Continue automatically after each pass
                **Outcome:** Play continues across ready stories without another click.

                ## Scope Boundaries
                ### In Scope
                - Play loop orchestration

                ### Out of Scope
                - Retry automation
                """;
    }

    private String pauseAfterCurrentStoryMarkdown() {
        return """
                # PRD: Pause After Current Story

                ## Overview
                Pause the execution session only after the active story finishes.

                ## Goals
                - Keep the repository stable while pause is requested.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-030: Finish the active story before pausing
                **Outcome:** The running story completes before the pause takes effect.

                ### US-031: Keep the next story queued while paused
                **Outcome:** The next ready story waits for an explicit resume.

                ## Scope Boundaries
                ### In Scope
                - Pause-after-current-step behavior

                ### Out of Scope
                - Retry orchestration
                """;
    }

    private String playRetryOnceMarkdown() {
        return """
                # PRD: Retry Once and Continue

                ## Overview
                Retry a transient failure once and continue the loop when the retry passes.

                ## Goals
                - Recover from one transient failure automatically.
                - Continue with the next ready story after the retry passes.

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report

                ## User Stories
                ### US-032: Retry once after a transient failure
                **Outcome:** A failed story is retried automatically one time.

                ### US-033: Continue after the recovered retry
                **Outcome:** Play continues with the next ready story after the retry succeeds.

                ## Scope Boundaries
                ### In Scope
                - Automatic one-time retry during Play

                ### Out of Scope
                - Manual repair workflows after a repeated failure
                """;
    }

    private String playAutoAdvancePrdJson() {
        return """
                {
                  "name": "Play Auto Advance",
                  "branchName": "ralph/play-auto-advance",
                  "description": "Continue across ready stories and skip blocked stories with a visible reason.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-030",
                      "title": "Skip blocked stories during Play",
                      "description": "As a user, I want blocked stories skipped with a visible reason.",
                      "acceptanceCriteria": [
                        "Blocked stories are skipped with a visible reason.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Blocked stories are skipped with a visible reason.",
                      "ralphyStatus": "BLOCKED"
                    },
                    {
                      "id": "US-031",
                      "title": "Continue from the next ready story",
                      "description": "As a user, I want Play to start from the next eligible story.",
                      "acceptanceCriteria": [
                        "Play starts from the next eligible story.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Play starts from the next eligible story.",
                      "ralphyStatus": "READY"
                    },
                    {
                      "id": "US-032",
                      "title": "Continue automatically after each pass",
                      "description": "As a user, I want Play to continue across ready stories automatically.",
                      "acceptanceCriteria": [
                        "Play continues automatically across ready stories.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 3,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Play continues automatically across ready stories.",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """;
    }

    private String playRetryOncePrdJson() {
        return """
                {
                  "name": "Retry Once and Continue",
                  "branchName": "ralph/retry-once-and-continue",
                  "description": "Retry a transient failure once and continue when the retry passes.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-032",
                      "title": "Retry once after a transient failure",
                      "description": "As a user, I want a transient failure retried automatically once.",
                      "acceptanceCriteria": [
                        "A failed story is retried automatically one time.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "A failed story is retried automatically one time.",
                      "ralphyStatus": "READY"
                    },
                    {
                      "id": "US-033",
                      "title": "Continue after the recovered retry",
                      "description": "As a user, I want Play to continue after the retry succeeds.",
                      "acceptanceCriteria": [
                        "Play continues with the next ready story after the retry succeeds.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "Play continues with the next ready story after the retry succeeds.",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """;
    }

    private String pauseAfterCurrentStoryPrdJson() {
        return """
                {
                  "name": "Pause After Current Story",
                  "branchName": "ralph/pause-after-current-story",
                  "description": "Pause only after the running story finishes.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report"
                  ],
                  "userStories": [
                    {
                      "id": "US-030",
                      "title": "Finish the active story before pausing",
                      "description": "As a user, I want the running story to finish before the pause takes effect.",
                      "acceptanceCriteria": [
                        "The running story completes before the pause takes effect.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "The running story completes before the pause takes effect.",
                      "ralphyStatus": "READY"
                    },
                    {
                      "id": "US-031",
                      "title": "Keep the next story queued while paused",
                      "description": "As a user, I want the next story to wait for resume.",
                      "acceptanceCriteria": [
                        "The next ready story waits for an explicit resume.",
                        ".\\\\mvnw.cmd clean verify jacoco:report"
                      ],
                      "priority": 2,
                      "passes": false,
                      "dependsOn": [],
                      "completionNotes": "",
                      "outcome": "The next ready story waits for an explicit resume.",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """;
    }

    private NativeWindowsPreflightReport passedNativePreflightReport(Path repository) {
        return new NativeWindowsPreflightReport(
                "2026-03-15T23:10:00Z",
                NativeWindowsPreflightReport.OverallStatus.PASS,
                List.of(
                        new NativeWindowsPreflightReport.CheckResult(
                                "codex_cli",
                                "Codex CLI",
                                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Detected fake Codex CLI."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "copilot_cli",
                                "GitHub Copilot CLI",
                                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Detected fake GitHub Copilot CLI."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "codex_auth",
                                "Codex Auth",
                                NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Detected stored test credentials."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "git_ready",
                                "Git Readiness",
                                NativeWindowsPreflightReport.CheckCategory.GIT,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Git can access " + repository.toAbsolutePath().normalize() + "."
                        ),
                        new NativeWindowsPreflightReport.CheckResult(
                                "quality_gate",
                                "Quality Gate Command",
                                NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                                NativeWindowsPreflightReport.CheckStatus.PASS,
                                "Found mvnw.cmd and pom.xml for .\\mvnw.cmd clean verify jacoco:report."
                        )
                )
        );
    }

    private Path createFakeCodexCommandScript() throws IOException {
        return createFakeCodexCommandScript(3000L);
    }

    private Path createFakeGitCommandScript() throws IOException {
        if (HostOperatingSystem.detect(System.getProperty("os.name", "")).isWindows()) {
            Path commandPath = tempDir.resolve("fake-git.cmd");
            Files.writeString(commandPath, """
                    @echo off
                    setlocal EnableExtensions EnableDelayedExpansion
                    set "STATE_DIR=%CD%\\.ralph-tui\\test-git-state"
                    set "CURRENT_BRANCH_FILE=%STATE_DIR%\\current-branch.txt"
                    set "HEAD_HASH_FILE=%STATE_DIR%\\head-hash.txt"
                    set "COMMIT_COUNT_FILE=%STATE_DIR%\\commit-count.txt"
                    if not exist "%STATE_DIR%" mkdir "%STATE_DIR%" >nul 2>&1

                    if "%~1"=="rev-parse" (
                        if "%~2"=="--abbrev-ref" (
                            if exist "%CURRENT_BRANCH_FILE%" (
                                type "%CURRENT_BRANCH_FILE%"
                            ) else (
                                echo main
                            )
                            exit /b 0
                        )
                        if "%~2"=="HEAD" (
                            if exist "%HEAD_HASH_FILE%" (
                                type "%HEAD_HASH_FILE%"
                            ) else (
                                echo fake-commit-0
                            )
                            exit /b 0
                        )
                        exit /b 0
                    )

                    if "%~1"=="show-ref" (
                        set "REF=%~5"
                        set "BRANCH=%REF:refs/heads/=%"
                        set "BRANCH_FILE=%STATE_DIR%\\!BRANCH:/=__!.txt"
                        if exist "!BRANCH_FILE!" exit /b 0
                        exit /b 1
                    )

                    if "%~1"=="switch" (
                        if "%~2"=="-c" (
                            set "BRANCH=%~3"
                            set "BRANCH_FILE=%STATE_DIR%\\!BRANCH:/=__!.txt"
                            > "!BRANCH_FILE!" echo !BRANCH!
                            > "%CURRENT_BRANCH_FILE%" echo !BRANCH!
                            exit /b 0
                        )

                        set "BRANCH=%~2"
                        set "BRANCH_FILE=%STATE_DIR%\\!BRANCH:/=__!.txt"
                        if not exist "!BRANCH_FILE!" exit /b 1
                        > "%CURRENT_BRANCH_FILE%" echo !BRANCH!
                        exit /b 0
                    )

                    if "%~1"=="add" (
                        exit /b 0
                    )

                    if "%~1"=="status" (
                        echo M src\\main\\java\\net\\uberfoo\\ai\\ralphy\\AppShellController.java
                        exit /b 0
                    )

                    if "%~1"=="commit" (
                        set /a COUNT=0
                        if exist "%COMMIT_COUNT_FILE%" set /p COUNT=<"%COMMIT_COUNT_FILE%"
                        set /a COUNT=!COUNT!+1
                        > "%COMMIT_COUNT_FILE%" echo !COUNT!
                        > "%HEAD_HASH_FILE%" echo fake-commit-!COUNT!
                        exit /b 0
                    )

                    echo Unexpected fake git command 1>&2
                    exit /b 1
                    """);
            return commandPath;
        }

        Path commandPath = tempDir.resolve("fake-git.sh");
        Files.writeString(commandPath, """
                #!/usr/bin/env bash
                set -euo pipefail
                state_dir="$PWD/.ralph-tui/test-git-state"
                current_branch_file="$state_dir/current-branch.txt"
                head_hash_file="$state_dir/head-hash.txt"
                commit_count_file="$state_dir/commit-count.txt"
                mkdir -p "$state_dir"

                if [ "${1:-}" = "rev-parse" ]; then
                  if [ "${2:-}" = "--abbrev-ref" ]; then
                    if [ -f "$current_branch_file" ]; then
                      cat "$current_branch_file"
                    else
                      printf '%s\n' 'main'
                    fi
                    exit 0
                  fi
                  if [ "${2:-}" = "HEAD" ]; then
                    if [ -f "$head_hash_file" ]; then
                      cat "$head_hash_file"
                    else
                      printf '%s\n' 'fake-commit-0'
                    fi
                    exit 0
                  fi
                  exit 0
                fi

                if [ "${1:-}" = "show-ref" ]; then
                  ref="${5:-}"
                  branch="${ref#refs/heads/}"
                  branch_file="$state_dir/${branch//\\//__}.txt"
                  [ -f "$branch_file" ] && exit 0
                  exit 1
                fi

                if [ "${1:-}" = "switch" ]; then
                  if [ "${2:-}" = "-c" ]; then
                    branch="${3:-}"
                    branch_file="$state_dir/${branch//\\//__}.txt"
                    printf '%s\n' "$branch" > "$branch_file"
                    printf '%s\n' "$branch" > "$current_branch_file"
                    exit 0
                  fi

                  branch="${2:-}"
                  branch_file="$state_dir/${branch//\\//__}.txt"
                  [ -f "$branch_file" ] || exit 1
                  printf '%s\n' "$branch" > "$current_branch_file"
                  exit 0
                fi

                if [ "${1:-}" = "add" ]; then
                  exit 0
                fi

                if [ "${1:-}" = "status" ]; then
                  printf '%s\n' ' M src/main/java/net/uberfoo/ai/ralphy/AppShellController.java'
                  exit 0
                fi

                if [ "${1:-}" = "commit" ]; then
                  count=0
                  if [ -f "$commit_count_file" ]; then
                    count=$(cat "$commit_count_file")
                  fi
                  count=$((count + 1))
                  printf '%s\n' "$count" > "$commit_count_file"
                  printf '%s\n' "fake-commit-$count" > "$head_hash_file"
                  exit 0
                fi

                printf '%s\n' 'Unexpected fake git command' >&2
                exit 1
                """);
        commandPath.toFile().setExecutable(true);
        return commandPath;
    }

    private Path createFakeCodexCommandScript(long sleepMilliseconds) throws IOException {
        if (HostOperatingSystem.detect(System.getProperty("os.name", "")).isWindows()) {
            Path commandPath = tempDir.resolve("fake-codex.cmd");
            Files.writeString(commandPath, """
                    @echo off
                    if "%~1"=="--version" (
                        echo codex-cli 0.114.0
                        exit /b 0
                    )
                    if "%~1"=="app-server" (
                        powershell.exe -NoLogo -NoProfile -Command "$null = [Console]::In.ReadToEnd(); Write-Output '{\"jsonrpc\":\"2.0\",\"id\":\"init-1\",\"result\":{\"capabilities\":{}}}'; Write-Output '{\"jsonrpc\":\"2.0\",\"id\":\"model-list-1\",\"result\":{\"data\":[{\"id\":\"gpt-5.4\",\"displayName\":\"GPT-5.4\",\"description\":\"Frontier model\",\"isDefault\":true,\"hidden\":false,\"reasoningEfforts\":[\"low\",\"medium\",\"high\",\"xhigh\"]},{\"id\":\"gpt-5.4-mini\",\"displayName\":\"GPT-5.4 Mini\",\"description\":\"Fast model\",\"isDefault\":false,\"hidden\":false,\"reasoningEfforts\":[\"low\",\"medium\",\"high\"]}]}}'"
                        exit /b 0
                    )

                    powershell.exe -NoLogo -NoProfile -Command "$null = [Console]::In.ReadToEnd(); Start-Sleep -Milliseconds __SLEEP_MS__; Write-Output '{\"event\":\"assistant_message.delta\",\"role\":\"assistant\",\"delta\":\"Working...\"}'; Write-Output '{\"event\":\"assistant_message.completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Completed story.\"}]}'"
                    exit /b 0
                    """.replace("__SLEEP_MS__", Long.toString(sleepMilliseconds)));
            return commandPath;
        }

        Path commandPath = tempDir.resolve("fake-codex.sh");
        Files.writeString(commandPath, """
                #!/usr/bin/env sh
                if [ "$1" = "--version" ]; then
                  printf '%s\n' 'codex-cli 0.114.0'
                  exit 0
                fi
                if [ "$1" = "app-server" ]; then
                  cat >/dev/null
                  printf '%s\n' '{"jsonrpc":"2.0","id":"init-1","result":{"capabilities":{}}}'
                  printf '%s\n' '{"jsonrpc":"2.0","id":"model-list-1","result":{"data":[{"id":"gpt-5.4","displayName":"GPT-5.4","description":"Frontier model","isDefault":true,"hidden":false,"reasoningEfforts":["low","medium","high","xhigh"]},{"id":"gpt-5.4-mini","displayName":"GPT-5.4 Mini","description":"Fast model","isDefault":false,"hidden":false,"reasoningEfforts":["low","medium","high"]}]}}'
                  exit 0
                fi

                cat >/dev/null
                sleep __SLEEP_SECONDS__
                printf '%s\n' '{"event":"assistant_message.delta","role":"assistant","delta":"Working..."}'
                printf '%s\n' '{"event":"assistant_message.completed","role":"assistant","content":[{"type":"output_text","text":"Completed story."}]}'
                """.replace("__SLEEP_SECONDS__", formatSleepSeconds(sleepMilliseconds)));
        commandPath.toFile().setExecutable(true);
        return commandPath;
    }

    private void waitForPlannerReply(JavaFxUiHarness harness) throws Exception {
        try {
            harness.waitUntil(() -> !harness.isDisabled("#prdPlannerSendButton")
                    && harness.text("#prdPlannerTranscriptArea").contains("Completed story."));
        } catch (java.util.concurrent.TimeoutException timeoutException) {
            throw new AssertionError(
                    "Planner reply did not complete in time. sendDisabled=" + harness.isDisabled("#prdPlannerSendButton")
                            + ", progressVisible=" + harness.isVisible("#prdPlannerProgressRow")
                            + ", progressLabel=" + harness.text("#prdPlannerProgressLabel")
                            + ", plannerMessage=" + harness.text("#prdPlannerMessageLabel")
                            + ", transcript=" + harness.text("#prdPlannerTranscriptArea"),
                    timeoutException
            );
        }
    }

    private Path createFlakyCodexCommandScript(Path invocationCounterPath) throws IOException {
        if (HostOperatingSystem.detect(System.getProperty("os.name", "")).isWindows()) {
            Path commandPath = tempDir.resolve("fake-codex-flaky.cmd");
            Files.writeString(commandPath, """
                    @echo off
                    if "%~1"=="--version" (
                        echo codex-cli 0.114.0
                        exit /b 0
                    )
                    if "%~1"=="app-server" (
                        powershell.exe -NoLogo -NoProfile -Command "$null = [Console]::In.ReadToEnd(); Write-Output '{\"jsonrpc\":\"2.0\",\"id\":\"init-1\",\"result\":{\"capabilities\":{}}}'; Write-Output '{\"jsonrpc\":\"2.0\",\"id\":\"model-list-1\",\"result\":{\"data\":[{\"id\":\"gpt-5.4\",\"displayName\":\"GPT-5.4\",\"description\":\"Frontier model\",\"isDefault\":true,\"hidden\":false,\"reasoningEfforts\":[\"low\",\"medium\",\"high\",\"xhigh\"]},{\"id\":\"gpt-5.4-mini\",\"displayName\":\"GPT-5.4 Mini\",\"description\":\"Fast model\",\"isDefault\":false,\"hidden\":false,\"reasoningEfforts\":[\"low\",\"medium\",\"high\"]}]}}'"
                        exit /b 0
                    )

                    powershell.exe -NoLogo -NoProfile -Command "$null = [Console]::In.ReadToEnd(); $counterPath = '__COUNTER_PATH__'; $count = 0; if (Test-Path $counterPath) { $count = [int](Get-Content $counterPath -Raw) }; $count++; Set-Content -Path $counterPath -Value $count -NoNewline -Encoding ascii; if ($count -eq 1) { [Console]::Error.WriteLine('Transient failure.'); exit 1 }; Write-Output '{\"event\":\"assistant_message.delta\",\"role\":\"assistant\",\"delta\":\"Working...\"}'; Write-Output '{\"event\":\"assistant_message.completed\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Completed story.\"}]}'"
                    set "EXITCODE=%ERRORLEVEL%"
                    exit /b %EXITCODE%
                    """.replace(
                    "__COUNTER_PATH__",
                    invocationCounterPath.toAbsolutePath().normalize().toString().replace("'", "''")
            ));
            return commandPath;
        }

        Path commandPath = tempDir.resolve("fake-codex-flaky.sh");
        Files.writeString(commandPath, """
                #!/usr/bin/env sh
                if [ "$1" = "--version" ]; then
                  printf '%s\n' 'codex-cli 0.114.0'
                  exit 0
                fi
                if [ "$1" = "app-server" ]; then
                  cat >/dev/null
                  printf '%s\n' '{"jsonrpc":"2.0","id":"init-1","result":{"capabilities":{}}}'
                  printf '%s\n' '{"jsonrpc":"2.0","id":"model-list-1","result":{"data":[{"id":"gpt-5.4","displayName":"GPT-5.4","description":"Frontier model","isDefault":true,"hidden":false,"reasoningEfforts":["low","medium","high","xhigh"]},{"id":"gpt-5.4-mini","displayName":"GPT-5.4 Mini","description":"Fast model","isDefault":false,"hidden":false,"reasoningEfforts":["low","medium","high"]}]}}'
                  exit 0
                fi

                cat >/dev/null
                count=0
                if [ -f '__COUNTER_PATH__' ]; then
                  count=$(cat '__COUNTER_PATH__')
                fi
                count=$((count + 1))
                printf '%s' "$count" > '__COUNTER_PATH__'
                if [ "$count" -eq 1 ]; then
                  printf '%s\n' 'Transient failure.' >&2
                  exit 1
                fi
                printf '%s\n' '{"event":"assistant_message.delta","role":"assistant","delta":"Working..."}'
                printf '%s\n' '{"event":"assistant_message.completed","role":"assistant","content":[{"type":"output_text","text":"Completed story."}]}'
                """.replace(
                "__COUNTER_PATH__",
                invocationCounterPath.toAbsolutePath().normalize().toString().replace("'", "'\"'\"'")
        ));
        commandPath.toFile().setExecutable(true);
        return commandPath;
    }

    private String formatSleepSeconds(long sleepMilliseconds) {
        long normalizedMilliseconds = HostOperatingSystem.detect(System.getProperty("os.name", "")).isWindows()
                ? sleepMilliseconds
                : Math.max(sleepMilliseconds, 250L);
        long wholeSeconds = normalizedMilliseconds / 1000;
        long remainingMilliseconds = Math.floorMod(normalizedMilliseconds, 1000);
        if (remainingMilliseconds == 0) {
            return Long.toString(wholeSeconds);
        }
        return wholeSeconds + "." + "%03d".formatted(remainingMilliseconds);
    }

    private String structuredAgentMessageEvents(int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            if (builder.length() > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(structuredAgentMessageEvent(index));
        }
        return builder.toString();
    }

    private String structuredAgentMessageEvent(int index) {
        return """
                {"type":"item.completed","item":{"id":"item_%d","type":"agent_message","status":"completed","text":"message %d"}}
                """.formatted(index, index).trim();
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    private String codexOutputSample() throws IOException {
        return Files.readString(Path.of("codex output sample.json"));
    }

    private void renderStructuredRunOutput(AppShellController controller, String rawOutput) throws Exception {
        runOnFxThread(() -> {
            Field runOutputPresentationStateField = AppShellController.class.getDeclaredField("runOutputPresentationState");
            runOutputPresentationStateField.setAccessible(true);
            runOutputPresentationStateField.set(controller, liveRunOutputPresentationState(rawOutput));

            Method renderRunOutputViewMethod = AppShellController.class.getDeclaredMethod("renderRunOutputView");
            renderRunOutputViewMethod.setAccessible(true);
            renderRunOutputViewMethod.invoke(controller);
        });
    }

    private Object liveRunOutputPresentationState(String rawOutput) throws Exception {
        Class<?> stateClass = Class.forName("net.uberfoo.ai.ralphy.AppShellController$RunOutputPresentationState");
        Method liveMethod = stateClass.getDeclaredMethod(
                "live",
                ExecutionAgentProvider.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        liveMethod.setAccessible(true);
        return liveMethod.invoke(
                null,
                ExecutionAgentProvider.CODEX,
                "Streaming US-040 | RUNNING",
                "Run run-040-1 started in test.",
                "",
                rawOutput
        );
    }

    private void runOnFxThread(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
