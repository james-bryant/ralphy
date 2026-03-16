package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveProjectServiceTest {
    private final GitRepositoryInitializer gitRepositoryInitializer = new GitRepositoryInitializer();
    private final ProjectMetadataInitializer projectMetadataInitializer = new ProjectMetadataInitializer();
    private final ProjectStorageInitializer projectStorageInitializer = new ProjectStorageInitializer();
    private final PrdMarkdownGenerator prdMarkdownGenerator = new PrdMarkdownGenerator();

    @TempDir
    Path tempDir;

    @Test
    void openRepositoryAcceptsFoldersWithGitMetadataDirectoryOrFile() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path gitDirectoryRepository = createGitDirectoryRepository("git-directory-repo");

        ActiveProjectService.ProjectActivationResult gitDirectoryResult =
                activeProjectService.openRepository(gitDirectoryRepository);

        assertTrue(gitDirectoryResult.successful());
        assertEquals(gitDirectoryRepository.toAbsolutePath().normalize(), gitDirectoryResult.activeProject().repositoryPath());
        assertManagedProjectStorage(gitDirectoryResult.activeProject());
        assertTrue(Files.readString(gitDirectoryResult.activeProject().projectMetadataPath())
                .contains("\"prdsDirectoryPath\""));

        Path gitFileRepository = Files.createDirectory(tempDir.resolve("git-file-repo"));
        Files.writeString(gitFileRepository.resolve(".git"), "gitdir: C:/tmp/worktree");

        ActiveProjectService.ProjectActivationResult gitFileResult =
                activeProjectService.openRepository(gitFileRepository);

        assertTrue(gitFileResult.successful());
        assertEquals(gitFileRepository.toAbsolutePath().normalize(), gitFileResult.activeProject().repositoryPath());
        assertManagedProjectStorage(gitFileResult.activeProject());
    }

    @Test
    void openRepositoryRejectsNonGitFoldersWithoutClearingExistingActiveProject() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path validRepository = createGitDirectoryRepository("valid-repo");
        activeProjectService.openRepository(validRepository);

        Path plainDirectory = Files.createDirectory(tempDir.resolve("plain-folder"));

        ActiveProjectService.ProjectActivationResult selectionResult =
                activeProjectService.openRepository(plainDirectory);

        assertFalse(selectionResult.successful());
        assertEquals("Selected folder is not a Git repository: " + plainDirectory.toAbsolutePath().normalize(),
                selectionResult.message());
        assertEquals(validRepository.toAbsolutePath().normalize(),
                activeProjectService.activeProject().orElseThrow().repositoryPath());
    }

    @Test
    void createRepositoryInitializesGitMetadataProjectMetadataAndActiveProject() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path parentDirectory = Files.createDirectory(tempDir.resolve("projects"));
        gitRepositoryInitializer.queueSuccessForTest();

        ActiveProjectService.ProjectActivationResult creationResult =
                activeProjectService.createRepository(parentDirectory, "starter-repo");

        Path createdRepository = parentDirectory.resolve("starter-repo").toAbsolutePath().normalize();
        ActiveProject activeProject = creationResult.activeProject();

        assertTrue(creationResult.successful());
        assertEquals(createdRepository, activeProject.repositoryPath());
        assertTrue(Files.isDirectory(createdRepository.resolve(".git")));
        assertManagedProjectStorage(activeProject);
        String projectMetadataDocument = Files.readString(activeProject.projectMetadataPath());
        assertTrue(projectMetadataDocument.contains("\"projectName\""));
        assertTrue(projectMetadataDocument.contains("starter-repo"));
        assertTrue(projectMetadataDocument.contains("\"activePrdJsonPath\""));
        assertEquals(createdRepository, activeProjectService.activeProject().orElseThrow().repositoryPath());

        LocalMetadataStorage.LocalMetadataSnapshot metadataSnapshot = createStorage().snapshot();
        assertEquals(1, metadataSnapshot.projects().size());
        assertEquals(1, metadataSnapshot.sessions().size());
        assertEquals(1, metadataSnapshot.profiles().size());
        assertTrue(metadataSnapshot.runMetadata().isEmpty());
        assertEquals(createdRepository.toString(), metadataSnapshot.projects().getFirst().repositoryPath());
        assertEquals(activeProject.prdsDirectoryPath().toString(),
                metadataSnapshot.projects().getFirst().storagePaths().prdsDirectoryPath());
        assertEquals(activeProject.logsDirectoryPath().toString(),
                metadataSnapshot.sessions().getFirst().storagePaths().logsDirectoryPath());
        assertEquals("POWERSHELL", metadataSnapshot.profiles().getFirst().profileType());
    }

    @Test
    void createRepositoryRollsBackFailedInitializationAndKeepsExistingActiveProject() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path validRepository = createGitDirectoryRepository("valid-repo");
        activeProjectService.openRepository(validRepository);

        Path parentDirectory = Files.createDirectory(tempDir.resolve("projects"));
        gitRepositoryInitializer.queueFailureForTest("Git init failed for test.");

        ActiveProjectService.ProjectActivationResult creationResult =
                activeProjectService.createRepository(parentDirectory, "broken-repo");

        Path failedRepository = parentDirectory.resolve("broken-repo").toAbsolutePath().normalize();
        assertFalse(creationResult.successful());
        assertEquals("Git init failed for test.", creationResult.message());
        assertFalse(Files.exists(failedRepository));
        assertEquals(validRepository.toAbsolutePath().normalize(),
                activeProjectService.activeProject().orElseThrow().repositoryPath());
    }

    @Test
    void openRepositoryRecreatesMissingManagedStorageDirectories() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path repository = createGitDirectoryRepository("existing-repo");

        ActiveProjectService.ProjectActivationResult firstOpenResult = activeProjectService.openRepository(repository);
        assertTrue(firstOpenResult.successful());

        ActiveProject activeProject = firstOpenResult.activeProject();
        Files.delete(activeProject.prdJsonDirectoryPath());
        Files.delete(activeProject.logsDirectoryPath());

        ActiveProjectService.ProjectActivationResult secondOpenResult = activeProjectService.openRepository(repository);

        assertTrue(secondOpenResult.successful());
        assertTrue(Files.isDirectory(activeProject.prdJsonDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.logsDirectoryPath()));
    }

    @Test
    void startupRestoresTheLastActiveRepositoryWhenItStillExists() throws IOException {
        Path repository = createGitDirectoryRepository("restored-repo");
        LocalMetadataStorage localMetadataStorage = createStorage();
        localMetadataStorage.recordProjectActivation(new ActiveProject(repository));
        localMetadataStorage.finishSession();

        ActiveProjectService activeProjectService = createService(localMetadataStorage);

        ActiveProject restoredProject = activeProjectService.activeProject().orElseThrow();
        assertEquals(repository.toAbsolutePath().normalize(), restoredProject.repositoryPath());
        assertEquals("", activeProjectService.startupRecoveryMessage());
        assertManagedProjectStorage(restoredProject);
    }

    @Test
    void startupShowsRecoveryMessageWhenTheLastActiveRepositoryCannotBeRestored() throws IOException {
        Path repository = createGitDirectoryRepository("missing-repo");
        LocalMetadataStorage localMetadataStorage = createStorage();
        localMetadataStorage.recordProjectActivation(new ActiveProject(repository));
        localMetadataStorage.finishSession();
        Files.delete(repository.resolve(".git"));

        ActiveProjectService activeProjectService = createService(localMetadataStorage);

        assertTrue(activeProjectService.activeProject().isEmpty());
        assertEquals("Last active repository could not be restored because it is missing or no longer "
                        + "a Git repository: " + repository.toAbsolutePath().normalize()
                        + ". Open an existing repository or create a new one to continue.",
                activeProjectService.startupRecoveryMessage());
    }

    @Test
    void latestRunRecoveryStateMarksActiveRunsAsResumable() throws IOException {
        Path repository = createGitDirectoryRepository("resumable-repo");
        LocalMetadataStorage localMetadataStorage = createStorage();
        localMetadataStorage.recordProjectActivation(new ActiveProject(repository));
        localMetadataStorage.finishSession();

        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-123",
                projectId,
                "US-011",
                "RUNNING",
                "2026-03-15T19:10:00Z",
                null
        )));

        ActiveProjectService activeProjectService = createService(localMetadataStorage);

        ActiveProjectService.RunRecoveryCandidate candidate =
                activeProjectService.latestRunRecoveryState().orElseThrow();
        assertEquals(ActiveProjectService.RunRecoveryAction.RESUMABLE, candidate.action());
        assertEquals("RUNNING", candidate.status());
        assertEquals("US-011", candidate.storyId());
    }

    @Test
    void latestRunRecoveryStateMarksEndedIncompleteRunsAsReviewable() throws IOException {
        Path repository = createGitDirectoryRepository("reviewable-repo");
        LocalMetadataStorage localMetadataStorage = createStorage();
        localMetadataStorage.recordProjectActivation(new ActiveProject(repository));
        localMetadataStorage.finishSession();

        String projectId = localMetadataStorage.snapshot().projects().getFirst().projectId();
        localMetadataStorage.replaceRunMetadataForTest(List.of(new LocalMetadataStorage.RunMetadataRecord(
                "run-456",
                projectId,
                "US-011",
                "FAILED",
                "2026-03-15T19:10:00Z",
                "2026-03-15T19:15:00Z"
        )));

        ActiveProjectService activeProjectService = createService(localMetadataStorage);

        ActiveProjectService.RunRecoveryCandidate candidate =
                activeProjectService.latestRunRecoveryState().orElseThrow();
        assertEquals(ActiveProjectService.RunRecoveryAction.REVIEWABLE, candidate.action());
        assertEquals("FAILED", candidate.status());
        assertEquals("US-011", candidate.storyId());
    }

    @Test
    void saveExecutionProfilePersistsWslSettingsAndReloadsThemPerProject() throws IOException {
        LocalMetadataStorage localMetadataStorage = createStorage();
        ActiveProjectService activeProjectService = createService(localMetadataStorage);
        Path repositoryOne = createGitDirectoryRepository("profile-one");
        Path repositoryTwo = createGitDirectoryRepository("profile-two");

        assertTrue(activeProjectService.openRepository(repositoryOne).successful());
        assertEquals(ExecutionProfile.ProfileType.POWERSHELL,
                activeProjectService.executionProfile().orElseThrow().type());

        ActiveProjectService.ExecutionProfileSaveResult wslSaveResult = activeProjectService.saveExecutionProfile(
                new ExecutionProfile(
                        ExecutionProfile.ProfileType.WSL,
                        "Ubuntu-24.04",
                        "C:\\Users\\james\\workspaces",
                        "/mnt/c/Users/james/workspaces"
                )
        );

        assertTrue(wslSaveResult.successful());
        assertEquals(ExecutionProfile.ProfileType.WSL, wslSaveResult.executionProfile().type());
        assertEquals("Ubuntu-24.04", wslSaveResult.executionProfile().wslDistribution());
        assertEquals("C:\\Users\\james\\workspaces", wslSaveResult.executionProfile().windowsPathPrefix());
        assertEquals("/mnt/c/Users/james/workspaces", wslSaveResult.executionProfile().wslPathPrefix());

        assertTrue(activeProjectService.openRepository(repositoryTwo).successful());
        assertEquals(ExecutionProfile.ProfileType.POWERSHELL,
                activeProjectService.executionProfile().orElseThrow().type());

        assertTrue(activeProjectService.openRepository(repositoryOne).successful());
        ExecutionProfile restoredProfile = activeProjectService.executionProfile().orElseThrow();
        assertEquals(ExecutionProfile.ProfileType.WSL, restoredProfile.type());
        assertEquals("Ubuntu-24.04", restoredProfile.wslDistribution());
        assertEquals("C:\\Users\\james\\workspaces", restoredProfile.windowsPathPrefix());
        assertEquals("/mnt/c/Users/james/workspaces", restoredProfile.wslPathPrefix());

        LocalMetadataStorage.ProfileRecord persistedProfile = localMetadataStorage.snapshot().profiles().stream()
                .filter(profileRecord -> profileRecord.projectId().equals(
                        localMetadataStorage.projectRecordForRepository(repositoryOne).orElseThrow().projectId()))
                .findFirst()
                .orElseThrow();
        assertEquals("WSL", persistedProfile.profileType());
        assertEquals("Ubuntu-24.04", persistedProfile.wslDistribution());
    }

    @Test
    void saveExecutionProfileRejectsIncompleteWslSettingsWithoutOverwritingExistingProfile() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path repository = createGitDirectoryRepository("wsl-validation-repo");
        assertTrue(activeProjectService.openRepository(repository).successful());

        ActiveProjectService.ExecutionProfileSaveResult saveResult = activeProjectService.saveExecutionProfile(
                new ExecutionProfile(
                        ExecutionProfile.ProfileType.WSL,
                        "",
                        "C:\\Users\\james\\workspaces",
                        "/mnt/c/Users/james/workspaces"
                )
        );

        assertFalse(saveResult.successful());
        assertEquals("Enter a WSL distribution before saving the WSL execution profile.", saveResult.message());
        assertEquals(ExecutionProfile.ProfileType.POWERSHELL,
                activeProjectService.executionProfile().orElseThrow().type());
    }

    @Test
    void savePrdInterviewDraftPersistsAnswersAndReloadsThemOnStartup() throws IOException {
        LocalMetadataStorage localMetadataStorage = createStorage();
        Path repository = createGitDirectoryRepository("prd-interview-repo");

        ActiveProjectService activeProjectService = createService(localMetadataStorage);
        assertTrue(activeProjectService.openRepository(repository).successful());

        PrdInterviewDraft draft = PrdInterviewDraft.empty()
                .withAnswer(
                        new PrdInterviewQuestion(
                                "overviewContext",
                                "overview",
                                "Overview",
                                "Product Context",
                                "What product, workflow, or repository initiative is this PRD defining?",
                                "Describe the current context, the problem being addressed, and why the work matters now."
                        ),
                        "Interactive PRD drafting for local Ralph workflows.",
                        1
                )
                .withAnswer(
                        new PrdInterviewQuestion(
                                "qualityGates",
                                "quality-gates",
                                "Quality Gates",
                                "Quality Gates",
                                "What automated or manual quality gates must every implementation story satisfy?",
                                "List the commands, validations, smoke checks, or review expectations that should stay true story by story."
                        ),
                        ".\\mvnw.cmd clean verify jacoco:report and Windows smoke.",
                        3
                );

        ActiveProjectService.PrdInterviewDraftSaveResult saveResult = activeProjectService.savePrdInterviewDraft(draft);

        assertTrue(saveResult.successful());
        assertEquals(2, saveResult.draft().answeredQuestionCount());
        assertTrue(Files.readString(new ActiveProject(repository).projectMetadataPath())
                .contains("\"prdInterviewDraft\""));

        localMetadataStorage.finishSession();
        ActiveProjectService restoredService = createService(localMetadataStorage);
        PrdInterviewDraft restoredDraft = restoredService.prdInterviewDraft().orElseThrow();

        assertEquals("Interactive PRD drafting for local Ralph workflows.",
                restoredDraft.answerFor("overviewContext"));
        assertEquals(".\\mvnw.cmd clean verify jacoco:report and Windows smoke.",
                restoredDraft.answerFor("qualityGates"));
        assertEquals(3, restoredDraft.selectedQuestionIndex());
    }

    @Test
    void saveActivePrdPersistsMarkdownAndReloadsItOnStartup() throws IOException {
        LocalMetadataStorage localMetadataStorage = createStorage();
        Path repository = createGitDirectoryRepository("active-prd-repo");

        ActiveProjectService activeProjectService = createService(localMetadataStorage);
        assertTrue(activeProjectService.openRepository(repository).successful());

        PrdInterviewDraft draft = new PrdInterviewDraft(
                0,
                List.of(
                        new PrdInterviewDraft.Answer(
                                "overviewContext",
                                "overview",
                                "Interactive PRD generation for local Ralph workflows."
                        ),
                        new PrdInterviewDraft.Answer(
                                "userStories",
                                "user-stories",
                                "Generate the active PRD | Save Markdown into the active project."
                        )
                ),
                "2026-03-15T18:00:00Z",
                "2026-03-15T18:05:00Z"
        );
        String initialMarkdown = prdMarkdownGenerator.generate(new ActiveProject(repository), draft);

        ActiveProjectService.ActivePrdSaveResult initialSaveResult = activeProjectService.saveActivePrd(initialMarkdown);

        assertTrue(initialSaveResult.successful());
        assertEquals(initialMarkdown, Files.readString(new ActiveProject(repository).activePrdPath()));
        assertEquals(initialMarkdown, activeProjectService.activePrdMarkdown().orElseThrow());

        PrdInterviewDraft regeneratedDraft = new PrdInterviewDraft(
                0,
                List.of(
                        new PrdInterviewDraft.Answer(
                                "overviewContext",
                                "overview",
                                "Interactive PRD generation for local Ralph workflows."
                        ),
                        new PrdInterviewDraft.Answer(
                                "userStories",
                                "user-stories",
                                "Generate the active PRD | Save Markdown into the active project.\n"
                                        + "Regenerate the active PRD | Overwrite the saved file with the latest draft."
                        )
                ),
                "2026-03-15T18:00:00Z",
                "2026-03-15T18:10:00Z"
        );
        String regeneratedMarkdown = prdMarkdownGenerator.generate(new ActiveProject(repository), regeneratedDraft);

        ActiveProjectService.ActivePrdSaveResult regenerationResult =
                activeProjectService.saveActivePrd(regeneratedMarkdown);

        assertTrue(regenerationResult.successful());
        assertEquals(regeneratedMarkdown, Files.readString(new ActiveProject(repository).activePrdPath()));

        localMetadataStorage.finishSession();
        ActiveProjectService restoredService = createService(localMetadataStorage);
        assertEquals(regeneratedMarkdown, restoredService.activePrdMarkdown().orElseThrow());
    }

    @Test
    void openRepositoryRunsAndStoresNativeWindowsPreflightForPowerShellProjects() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path repository = createGitDirectoryRepository("preflight-repo");
        Files.writeString(repository.resolve("mvnw.cmd"), "@echo off\r\n");
        Files.writeString(repository.resolve("pom.xml"), "<project/>");

        ActiveProjectService.ProjectActivationResult activationResult = activeProjectService.openRepository(repository);

        assertTrue(activationResult.successful());
        NativeWindowsPreflightReport report = activeProjectService.latestNativeWindowsPreflightReport().orElseThrow();
        assertTrue(report.passed());
        assertEquals(4, report.checks().size());
        NativeWindowsPreflightReport storedReport = projectMetadataInitializer
                .readNativeWindowsPreflight(new ActiveProject(repository))
                .orElseThrow();
        assertEquals(report.status(), storedReport.status());
        assertTrue(Files.readString(new ActiveProject(repository).projectMetadataPath())
                .contains("\"nativeWindowsPreflight\""));
    }

    @Test
    void saveExecutionProfileRunsAndStoresWslPreflightForWslProjects() throws IOException {
        ActiveProjectService activeProjectService = createService();
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("wsl-workspaces"));
        Path repository = createGitDirectoryRepository(workspaceRoot, "wsl-preflight-repo");

        assertTrue(activeProjectService.openRepository(repository).successful());

        ActiveProjectService.ExecutionProfileSaveResult saveResult = activeProjectService.saveExecutionProfile(
                new ExecutionProfile(
                        ExecutionProfile.ProfileType.WSL,
                        "Ubuntu-24.04",
                        workspaceRoot.toString(),
                        "/mnt/c/wsl-workspaces"
                )
        );

        assertTrue(saveResult.successful());
        WslPreflightReport report = activeProjectService.latestWslPreflightReport().orElseThrow();
        assertTrue(report.passed());
        assertEquals(5, report.checks().size());
        WslPreflightReport storedReport = projectMetadataInitializer
                .readWslPreflight(new ActiveProject(repository))
                .orElseThrow();
        assertEquals(report.status(), storedReport.status());
        assertTrue(Files.readString(new ActiveProject(repository).projectMetadataPath())
                .contains("\"wslPreflight\""));
    }

    private Path createGitDirectoryRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private Path createGitDirectoryRepository(Path parentDirectory, String directoryName) throws IOException {
        Path repository = Files.createDirectory(parentDirectory.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private ActiveProjectService createService() throws IOException {
        return createService(createStorage());
    }

    private ActiveProjectService createService(LocalMetadataStorage localMetadataStorage) throws IOException {
        return new ActiveProjectService(
                gitRepositoryInitializer,
                projectMetadataInitializer,
                projectStorageInitializer,
                localMetadataStorage,
                createNativePreflightService(),
                createWslPreflightService(),
                true
        );
    }

    private LocalMetadataStorage createStorage() {
        return LocalMetadataStorage.forTest(tempDir.resolve("local-storage"));
    }

    private NativeWindowsPreflightService createNativePreflightService() throws IOException {
        Path codexHome = tempDir.resolve("codex-home");
        Files.createDirectories(codexHome);
        Files.writeString(codexHome.resolve("auth.json"), """
                {
                  "OPENAI_API_KEY": null,
                  "tokens": {
                    "access_token": "token"
                  }
                }
                """);

        return new NativeWindowsPreflightService(
                java.util.Map.of(),
                codexHome,
                (workingDirectory, command) -> switch (command.getFirst()) {
                    case "codex" -> NativeWindowsPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
                    case "git" -> NativeWindowsPreflightService.CommandResult.success(0, "true");
                    default -> NativeWindowsPreflightService.CommandResult.failure("Unexpected command");
                }
        );
    }

    private WslPreflightService createWslPreflightService() {
        return new WslPreflightService((workingDirectory, command) -> {
            if (command.contains("--list")) {
                return WslPreflightService.CommandResult.success(0, "Ubuntu-24.04\nDebian");
            }

            String script = command.getLast();
            if (script.contains("pwd")) {
                return WslPreflightService.CommandResult.success(0, "/mnt/c/wsl-workspaces/wsl-preflight-repo");
            }
            if (script.contains("codex --version")) {
                return WslPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
            }
            if (script.contains("OPENAI_API_KEY")) {
                return WslPreflightService.CommandResult.success(0,
                        "Detected stored Codex login tokens in /home/test/.codex/auth.json.");
            }
            if (script.contains("git rev-parse --is-inside-work-tree")) {
                return WslPreflightService.CommandResult.success(0, "true");
            }

            return WslPreflightService.CommandResult.failure("Unexpected command");
        });
    }

    private void assertManagedProjectStorage(ActiveProject activeProject) {
        assertTrue(Files.exists(activeProject.projectMetadataPath()));
        assertTrue(Files.isDirectory(activeProject.ralphyDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.prdsDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.prdJsonDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.promptsDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.logsDirectoryPath()));
        assertTrue(Files.isDirectory(activeProject.artifactsDirectoryPath()));
    }
}
