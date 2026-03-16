package net.uberfoo.ai.ralphy;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellUiTest {
    private JavaFxUiHarness harness;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        JavaFxUiHarness.startToolkit();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.closeShell();
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
        assertEquals(shellDescriptor.workspacePlaceholder(), harness.text("#workspacePlaceholderLabel"));
        assertEquals(shellDescriptor.statusPlaceholder(), harness.text("#statusLabel"));
        assertTrue(harness.sceneHasStylesheet(AppTheme.stylesheetUrl()));
        assertTrue(harness.hasRootStyleClass(AppTheme.rootStyleClass()));
        assertEquals(Color.web("#020617"), harness.backgroundColor("#shellRoot"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#navigationPane"));
        assertEquals(Color.web("#111827"), harness.backgroundColor("#workspacePane"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#statusPane"));
        assertEquals(Color.web("#e5eefc"), harness.textFill("#brandLabel"));
        assertEquals(Color.web("#94a3b8"), harness.textFill("#taglineLabel"));

        harness.clickOn("#prdEditorNavButton");

        assertEquals("PRD Editor", harness.text("#workspaceTitleLabel"));
        assertEquals("Create, refine, and validate the active PRD in this workspace.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("PRD Editor workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#prdEditorNavButton", "shell-nav-button-active"));

        harness.clickOn("#executionNavButton");

        assertEquals("Execution", harness.text("#workspaceTitleLabel"));
        assertEquals("Run controls, current story progress, and live execution output will appear here.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("Execution workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#executionNavButton", "shell-nav-button-active"));
    }

    @Test
    void appShellShowsBuiltInPresetCatalogWithReadOnlyPreview() throws Exception {
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(tempDir.resolve("storage"));

        assertEquals("Ralph/Codex PRD Creation", harness.text("#presetPreviewNameLabel"));
        assertTrue(harness.text("#presetPreviewVersionLabel").contains("v1"));
        assertTrue(harness.text("#presetRequiredSkillsValueLabel").contains("ralph-tui-prd"));
        assertTrue(harness.text("#presetPromptPreviewArea")
                .contains("repository-owned Product Requirements Document"));
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

        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("0 of 7 questions answered"));
        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Product Context", harness.text("#prdInterviewTitleLabel"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("Quality Gates"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("User Stories"));
        assertTrue(harness.textContent("#prdInterviewQuestionsContainer").contains("In Scope"));
        assertTrue(harness.text("#prdInterviewDraftStateLabel")
                .contains(".ralph-tui/project-metadata.json"));

        harness.enterText("#prdInterviewAnswerArea", "Ralphy should guide users through PRD drafting.");
        harness.clickOn("#prdInterviewNextButton");

        assertEquals("Question 2 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("1 of 7 questions answered"));
        assertTrue(harness.text("#prdInterviewDraftStateLabel").contains("Answer saved."));

        harness.enterText("#prdInterviewAnswerArea", "Primary users are developers running one repository at a time.");
        harness.clickOn("#prdInterviewQuestionOverviewContext");

        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Ralphy should guide users through PRD drafting.", harness.text("#prdInterviewAnswerArea"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("2 of 7 questions answered"));

        harness.closeShell();
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell(storageDirectory);

        assertEquals("prd-interview-repo", harness.text("#activeProjectNameLabel"));
        assertEquals("Question 1 of 7", harness.text("#prdInterviewQuestionCounterLabel"));
        assertEquals("Ralphy should guide users through PRD drafting.", harness.text("#prdInterviewAnswerArea"));
        assertTrue(harness.text("#prdInterviewSummaryLabel").contains("2 of 7 questions answered"));
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
        assertEquals(editedPreview.replace("\n", "\r\n"), Files.readString(importedPrdPath));

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
    void appShellShowsStoredNativePreflightFailuresWithCategories() throws Exception {
        Path storageDirectory = tempDir.resolve("storage");
        Path repository = createGitRepository("preflight-repo");
        ActiveProject activeProject = new ActiveProject(repository);
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.saveExecutionProfile(projectId, new ExecutionProfile(
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

        assertEquals("Native execution blocked", harness.text("#nativePreflightSummaryLabel"));
        assertTrue(harness.text("#nativePreflightDetailLabel").contains("Native PowerShell runs stay blocked"));
        String checks = harness.text("#nativePreflightChecksLabel");
        assertTrue(checks.contains("PASS | Tooling | Codex CLI"));
        assertTrue(checks.contains("FAIL | Authentication | Codex Auth"));
        assertTrue(checks.contains("PASS | Git | Git Readiness"));
        assertTrue(checks.contains("FAIL | Quality Gate | Quality Gate Command"));
        assertTrue(harness.isVisible("#nativePreflightRemediationSection"));
        String remediation = harness.textContent("#nativePreflightRemediationSection");
        assertTrue(remediation.contains("Ralphy never installs or authenticates Codex automatically"));
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
        LocalMetadataStorage localMetadataStorage = seedStoredProject(storageDirectory, repository);
        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.saveExecutionProfile(projectId, new ExecutionProfile(
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

        assertEquals("WSL execution blocked", harness.text("#wslPreflightSummaryLabel"));
        assertTrue(harness.text("#wslPreflightDetailLabel").contains("WSL runs stay blocked"));
        String checks = harness.text("#wslPreflightChecksLabel");
        assertTrue(checks.contains("PASS | Distribution | WSL Distribution"));
        assertTrue(checks.contains("PASS | Path Mapping | Repository Path Mapping"));
        assertTrue(checks.contains("FAIL | Tooling | Codex CLI"));
        assertTrue(checks.contains("FAIL | Authentication | Codex Auth"));
        assertTrue(checks.contains("PASS | Git | Git Readiness"));
        assertTrue(harness.isVisible("#wslPreflightRemediationSection"));
        String remediation = harness.textContent("#wslPreflightRemediationSection");
        assertTrue(remediation.contains("wsl.exe --distribution \"Ubuntu-24.04\""));
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

    private void seedStoryProgressArtifacts(Path repository, String markdown, String prdJson) throws IOException {
        ActiveProject activeProject = new ActiveProject(repository);
        Files.createDirectories(activeProject.prdsDirectoryPath());
        Files.createDirectories(activeProject.prdJsonDirectoryPath());
        Files.writeString(activeProject.activePrdPath(), markdown);
        Files.writeString(activeProject.activePrdJsonPath(), prdJson);
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

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }
}
