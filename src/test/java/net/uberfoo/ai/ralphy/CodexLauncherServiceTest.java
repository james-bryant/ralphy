package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexLauncherServiceTest {
    private final PresetCatalogService presetCatalogService = new PresetCatalogService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void buildLaunchCreatesNativePowerShellCommandAndPromptPayload() throws IOException {
        ActiveProject activeProject = new ActiveProject(createGitRepository("native-launch-repo"));
        CodexLauncherService launcherService = createLauncherService(plan -> {
            throw new AssertionError("buildLaunch should not execute the process");
        });

        CodexLauncherService.CodexLaunchPlan launchPlan = launcherService.buildLaunch(new CodexLauncherService.CodexLaunchRequest(
                "US-025",
                activeProject,
                ExecutionProfile.nativePowerShell(),
                presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                List.of(
                        new CodexLauncherService.PromptInput("Story", "US-025: Build a Native and WSL Codex Launcher"),
                        new CodexLauncherService.PromptInput(
                                "Quality Gate",
                                ".\\mvnw.cmd clean verify jacoco:report"
                        )
                ),
                "Implement this story and keep the change set scoped.",
                List.of("--json", "--color", "never")
        ));

        assertEquals("run-123", launchPlan.runId());
        assertEquals(
                List.of(
                        "powershell.exe",
                        "-NoLogo",
                        "-NoProfile",
                        "-Command",
                        "& 'codex' 'exec' '--json' '--color' 'never' '-'"
                ),
                launchPlan.command()
        );
        assertEquals(activeProject.repositoryPath(), launchPlan.processWorkingDirectory());
        assertEquals(activeProject.repositoryPath().toString(), launchPlan.executionWorkingDirectory());
        assertTrue(launchPlan.promptText().contains("Preset Inputs:"));
        assertTrue(launchPlan.promptText().contains("- Story: US-025: Build a Native and WSL Codex Launcher"));
        assertTrue(launchPlan.promptText().contains("Additional Instructions:"));
        assertTrue(launchPlan.promptText().contains("Implement this story and keep the change set scoped."));
    }

    @Test
    void buildLaunchCreatesWslCommandWithMappedRepositoryPath() throws IOException {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspace-root"));
        ActiveProject activeProject = new ActiveProject(createGitRepository(workspaceRoot, "wsl-launch-repo"));
        CodexLauncherService launcherService = createLauncherService(plan -> {
            throw new AssertionError("buildLaunch should not execute the process");
        });

        CodexLauncherService.CodexLaunchPlan launchPlan = launcherService.buildLaunch(new CodexLauncherService.CodexLaunchRequest(
                "US-025",
                activeProject,
                new ExecutionProfile(
                        ExecutionProfile.ProfileType.WSL,
                        "Ubuntu-24.04",
                        workspaceRoot.toString(),
                        "/mnt/c/workspace-root"
                ),
                presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                List.of(new CodexLauncherService.PromptInput("Story", "US-025")),
                "",
                List.of("--json")
        ));

        assertEquals("run-123", launchPlan.runId());
        assertEquals(
                List.of(
                        "wsl.exe",
                        "--distribution",
                        "Ubuntu-24.04",
                        "--cd",
                        "/mnt/c/workspace-root/wsl-launch-repo",
                        "--exec",
                        "/bin/sh",
                        "-lc",
                        "'codex' 'exec' '--json' '-'"
                ),
                launchPlan.command()
        );
        assertNull(launchPlan.processWorkingDirectory());
        assertEquals("/mnt/c/workspace-root/wsl-launch-repo", launchPlan.executionWorkingDirectory());
        assertTrue(launchPlan.promptText().contains("- Story: US-025"));
    }

    @Test
    void launchCapturesExitCodeProcessMetadataPersistsRunMetadataAndStoresArtifacts() throws IOException {
        Path storageDirectory = tempDir.resolve("local-storage");
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        ActiveProject activeProject = new ActiveProject(createGitRepository("launch-metadata-repo"));
        localMetadataStorage.startSession();
        localMetadataStorage.recordProjectActivation(activeProject);

        AtomicReference<CodexLauncherService.CodexLaunchPlan> executedPlan = new AtomicReference<>();
        CodexLauncherService launcherService = new CodexLauncherService(
                localMetadataStorage,
                Clock.fixed(Instant.parse("2026-03-15T21:30:00Z"), ZoneOffset.UTC),
                () -> "run-789",
                launchPlan -> {
                    executedPlan.set(launchPlan);
                    return CodexLauncherService.ProcessExecution.completed(4321L, 0, "{\"event\":\"done\"}", "");
                }
        );

        CodexLauncherService.CodexLaunchResult launchResult =
                launcherService.launch(new CodexLauncherService.CodexLaunchRequest(
                        "US-025",
                        activeProject,
                        ExecutionProfile.nativePowerShell(),
                        presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                        List.of(new CodexLauncherService.PromptInput(
                                "Story",
                                "US-025: Build a Native and WSL Codex Launcher"
                        )),
                        "Run the project quality gate before finishing.",
                        List.of("--json")
                ));

        assertTrue(launchResult.successful());
        assertEquals("SUCCEEDED", launchResult.status());
        assertEquals(Long.valueOf(4321L), launchResult.processId());
        assertEquals(Integer.valueOf(0), launchResult.exitCode());
        assertEquals(executedPlan.get(), launchResult.launchPlan());
        assertEquals("", launchResult.message());
        assertEquals("{\"event\":\"done\"}", launchResult.stdout());

        Path promptPath = activeProject.promptsDirectoryPath().resolve("US-025").resolve("run-789").resolve("prompt.txt");
        Path stdoutPath = activeProject.logsDirectoryPath().resolve("US-025").resolve("run-789").resolve("stdout.log");
        Path stderrPath = activeProject.logsDirectoryPath().resolve("US-025").resolve("run-789").resolve("stderr.log");
        Path structuredEventsPath = activeProject.logsDirectoryPath()
                .resolve("US-025")
                .resolve("run-789")
                .resolve("structured-events.jsonl");
        Path summaryPath = activeProject.artifactsDirectoryPath()
                .resolve("US-025")
                .resolve("run-789")
                .resolve("attempt-summary.json");
        Path assistantSummaryPath = activeProject.artifactsDirectoryPath()
                .resolve("US-025")
                .resolve("run-789")
                .resolve("assistant-summary.txt");

        assertEquals(promptPath, launchResult.artifacts().promptPath());
        assertEquals(stdoutPath, launchResult.artifacts().stdoutPath());
        assertEquals(stderrPath, launchResult.artifacts().stderrPath());
        assertEquals(structuredEventsPath, launchResult.artifacts().structuredEventsPath());
        assertEquals(summaryPath, launchResult.artifacts().summaryPath());
        assertEquals(assistantSummaryPath, launchResult.artifacts().assistantSummaryPath());
        assertEquals(executedPlan.get().promptText(), Files.readString(promptPath));
        assertEquals("{\"event\":\"done\"}", Files.readString(stdoutPath));
        assertEquals("", Files.readString(stderrPath));
        assertEquals("{\"event\":\"done\"}", Files.readString(structuredEventsPath));
        assertEquals("", Files.readString(assistantSummaryPath));
        assertEquals("", launchResult.assistantSummary());

        JsonNode summaryArtifact = objectMapper.readTree(Files.readString(summaryPath));
        assertEquals("run-789", summaryArtifact.path("runId").asText());
        assertEquals("US-025", summaryArtifact.path("storyId").asText());
        assertEquals("SUCCEEDED", summaryArtifact.path("status").asText());
        assertEquals("POWERSHELL", summaryArtifact.path("profileType").asText());
        assertEquals(0, summaryArtifact.path("exitCode").asInt());
        assertEquals(summaryPath.toString(), summaryArtifact.path("artifactPaths").path("summaryPath").asText());
        assertEquals(assistantSummaryPath.toString(),
                summaryArtifact.path("artifactPaths").path("assistantSummaryPath").asText());

        LocalMetadataStorage.RunMetadataRecord persistedRunMetadata =
                localMetadataStorage.latestRunMetadataForProject(
                        localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                                .orElseThrow()
                                .projectId()
                ).orElseThrow();

        assertEquals("run-789", persistedRunMetadata.runId());
        assertEquals("US-025", persistedRunMetadata.storyId());
        assertEquals("SUCCEEDED", persistedRunMetadata.status());
        assertEquals("POWERSHELL", persistedRunMetadata.profileType());
        assertEquals(activeProject.repositoryPath().toString(), persistedRunMetadata.workingDirectory());
        assertEquals(Long.valueOf(4321L), persistedRunMetadata.processId());
        assertEquals(Integer.valueOf(0), persistedRunMetadata.exitCode());
        assertEquals(executedPlan.get().command(), persistedRunMetadata.command());
        assertEquals(new LocalMetadataStorage.RunArtifactPaths(
                promptPath.toString(),
                stdoutPath.toString(),
                stderrPath.toString(),
                structuredEventsPath.toString(),
                summaryPath.toString(),
                assistantSummaryPath.toString()
        ), persistedRunMetadata.artifactPaths());

        LocalMetadataStorage restartedStorage = LocalMetadataStorage.forTest(storageDirectory);
        LocalMetadataStorage.RunMetadataRecord restartedRunMetadata =
                restartedStorage.latestRunMetadataForProject(
                        restartedStorage.projectRecordForRepository(activeProject.repositoryPath())
                                .orElseThrow()
                                .projectId()
                ).orElseThrow();
        assertEquals(persistedRunMetadata, restartedRunMetadata);
    }

    @Test
    void launchCapturesFailureMetadataWhenTheProcessCannotStart() throws IOException {
        Path storageDirectory = tempDir.resolve("failed-local-storage");
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        ActiveProject activeProject = new ActiveProject(createGitRepository("failed-launch-repo"));
        localMetadataStorage.startSession();
        localMetadataStorage.recordProjectActivation(activeProject);

        CodexLauncherService launcherService = new CodexLauncherService(
                localMetadataStorage,
                Clock.fixed(Instant.parse("2026-03-15T21:45:00Z"), ZoneOffset.UTC),
                () -> "run-999",
                launchPlan -> CodexLauncherService.ProcessExecution.failure("Access is denied.")
        );

        CodexLauncherService.CodexLaunchResult launchResult =
                launcherService.launch(new CodexLauncherService.CodexLaunchRequest(
                        "US-025",
                        activeProject,
                        ExecutionProfile.nativePowerShell(),
                        presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                        List.of(),
                        "",
                        List.of()
                ));

        assertFalse(launchResult.successful());
        assertEquals("FAILED", launchResult.status());
        assertNull(launchResult.processId());
        assertNull(launchResult.exitCode());
        assertEquals("Access is denied.", launchResult.message());

        LocalMetadataStorage.RunMetadataRecord persistedRunMetadata =
                localMetadataStorage.latestRunMetadataForProject(
                        localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                                .orElseThrow()
                                .projectId()
                ).orElseThrow();

        assertEquals("FAILED", persistedRunMetadata.status());
        assertNull(persistedRunMetadata.processId());
        assertNull(persistedRunMetadata.exitCode());
        assertEquals(List.of(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-Command",
                "& 'codex' 'exec' '-'"
        ), persistedRunMetadata.command());
    }

    @Test
    void launchPersistsRunningMetadataBeforeWaitingForTheFinalResult() throws IOException {
        Path storageDirectory = tempDir.resolve("running-local-storage");
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        ActiveProject activeProject = new ActiveProject(createGitRepository("running-launch-repo"));
        localMetadataStorage.startSession();
        localMetadataStorage.recordProjectActivation(activeProject);
        String projectId = localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                .orElseThrow()
                .projectId();

        AtomicReference<LocalMetadataStorage.RunMetadataRecord> runningMetadata = new AtomicReference<>();
        CodexLauncherService launcherService = new CodexLauncherService(
                localMetadataStorage,
                Clock.fixed(Instant.parse("2026-03-15T21:55:00Z"), ZoneOffset.UTC),
                () -> "run-running-1",
                launchPlan -> {
                    runningMetadata.set(localMetadataStorage.latestRunMetadataForProject(projectId).orElseThrow());
                    return CodexLauncherService.ProcessExecution.completed(2222L, 0, "", "");
                }
        );

        CodexLauncherService.CodexLaunchResult launchResult =
                launcherService.launch(new CodexLauncherService.CodexLaunchRequest(
                        "US-027",
                        activeProject,
                        ExecutionProfile.nativePowerShell(),
                        presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                        List.of(new CodexLauncherService.PromptInput("Story", "US-027")),
                        "",
                        List.of("--json")
                ));

        assertTrue(launchResult.successful());
        assertEquals("RUNNING", runningMetadata.get().status());
        assertEquals("run-running-1", runningMetadata.get().runId());
        assertEquals("US-027", runningMetadata.get().storyId());
        assertEquals("2026-03-15T21:55:00Z", runningMetadata.get().startedAt());
        assertNull(runningMetadata.get().endedAt());
    }

    @Test
    void launchStoresRawStdoutStderrAndSummaryArtifactsForFailedAttempts() throws IOException {
        Path storageDirectory = tempDir.resolve("stderr-local-storage");
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        ActiveProject activeProject = new ActiveProject(createGitRepository("stderr-launch-repo"));
        localMetadataStorage.startSession();
        localMetadataStorage.recordProjectActivation(activeProject);

        CodexLauncherService launcherService = new CodexLauncherService(
                localMetadataStorage,
                Clock.fixed(Instant.parse("2026-03-15T22:00:00Z"), ZoneOffset.UTC),
                () -> "run-321",
                launchPlan -> CodexLauncherService.ProcessExecution.completed(
                        6789L,
                        7,
                        "plain stdout",
                        "plain stderr"
                )
        );

        CodexLauncherService.CodexLaunchResult launchResult =
                launcherService.launch(new CodexLauncherService.CodexLaunchRequest(
                        "US-026",
                        activeProject,
                        ExecutionProfile.nativePowerShell(),
                        presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                        List.of(new CodexLauncherService.PromptInput("Story", "US-026")),
                        "",
                        List.of()
                ));

        assertFalse(launchResult.successful());
        assertEquals("FAILED", launchResult.status());
        assertEquals("plain stdout", Files.readString(launchResult.artifacts().stdoutPath()));
        assertEquals("plain stderr", Files.readString(launchResult.artifacts().stderrPath()));
        assertNull(launchResult.artifacts().structuredEventsPath());
        assertFalse(Files.exists(activeProject.logsDirectoryPath()
                .resolve("US-026")
                .resolve("run-321")
                .resolve("structured-events.jsonl")));
        assertTrue(Files.exists(launchResult.artifacts().summaryPath()));

        JsonNode summaryArtifact = objectMapper.readTree(Files.readString(launchResult.artifacts().summaryPath()));
        assertEquals("FAILED", summaryArtifact.path("status").asText());
        assertEquals(7, summaryArtifact.path("exitCode").asInt());
        assertEquals("Codex exited with code 7.", summaryArtifact.path("message").asText());
        assertTrue(summaryArtifact.path("artifactPaths").path("structuredEventsPath").isNull());
    }

    @Test
    void launchStreamsLiveOutputAndStoresAssistantSummarySeparately() throws IOException {
        Path storageDirectory = tempDir.resolve("streaming-local-storage");
        LocalMetadataStorage localMetadataStorage = LocalMetadataStorage.forTest(storageDirectory);
        ActiveProject activeProject = new ActiveProject(createGitRepository("streaming-launch-repo"));
        localMetadataStorage.startSession();
        localMetadataStorage.recordProjectActivation(activeProject);

        List<String> streamedStdout = new ArrayList<>();
        List<String> streamedStderr = new ArrayList<>();
        CodexLauncherService.ProcessExecutor processExecutor = new CodexLauncherService.ProcessExecutor() {
            @Override
            public CodexLauncherService.ProcessExecution execute(CodexLauncherService.CodexLaunchPlan launchPlan,
                                                                 CodexLauncherService.RunOutputListener runOutputListener) {
                runOutputListener.onStdout("""
                        {"event":"assistant_message.delta","role":"assistant","delta":"Planning..."}
                        """);
                runOutputListener.onStderr("warning: live stderr");
                String stdout = """
                        {"event":"assistant_message.delta","role":"assistant","delta":"Planning..."}
                        {"event":"assistant_message.completed","role":"assistant","content":[{"type":"output_text","text":"Implemented US-029. Ran tests."}]}
                        """.trim();
                return CodexLauncherService.ProcessExecution.completed(9999L, 0, stdout, "warning: live stderr");
            }

            @Override
            public CodexLauncherService.ProcessExecution execute(CodexLauncherService.CodexLaunchPlan launchPlan) {
                throw new AssertionError("Streaming path should be used.");
            }
        };

        CodexLauncherService launcherService = new CodexLauncherService(
                localMetadataStorage,
                Clock.fixed(Instant.parse("2026-03-15T22:05:00Z"), ZoneOffset.UTC),
                () -> "run-stream-1",
                processExecutor
        );

        CodexLauncherService.CodexLaunchResult launchResult =
                launcherService.launch(new CodexLauncherService.CodexLaunchRequest(
                        "US-029",
                        activeProject,
                        ExecutionProfile.nativePowerShell(),
                        presetCatalogService.defaultPreset(PresetUseCase.STORY_IMPLEMENTATION),
                        List.of(new CodexLauncherService.PromptInput("Story", "US-029")),
                        "",
                        List.of("--json")
                ), new CodexLauncherService.RunOutputListener() {
                    @Override
                    public void onStdout(String text) {
                        streamedStdout.add(text);
                    }

                    @Override
                    public void onStderr(String text) {
                        streamedStderr.add(text);
                    }
                });

        assertEquals(1, streamedStdout.size());
        assertTrue(streamedStdout.getFirst().contains("Planning..."));
        assertEquals(List.of("warning: live stderr"), streamedStderr);
        assertEquals("Implemented US-029. Ran tests.", launchResult.assistantSummary());
        assertEquals("Implemented US-029. Ran tests.",
                Files.readString(launchResult.artifacts().assistantSummaryPath()));
    }

    private CodexLauncherService createLauncherService(CodexLauncherService.ProcessExecutor processExecutor) {
        return new CodexLauncherService(
                LocalMetadataStorage.forTest(tempDir.resolve("unused-storage")),
                Clock.fixed(Instant.parse("2026-03-15T21:00:00Z"), ZoneOffset.UTC),
                () -> "run-123",
                processExecutor
        );
    }

    private Path createGitRepository(String directoryName) throws IOException {
        return createGitRepository(tempDir, directoryName);
    }

    private Path createGitRepository(Path parentDirectory, String directoryName) throws IOException {
        Path repository = Files.createDirectory(parentDirectory.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }
}
