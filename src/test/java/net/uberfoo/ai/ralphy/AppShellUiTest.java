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
}
