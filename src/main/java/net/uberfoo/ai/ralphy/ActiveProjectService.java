package net.uberfoo.ai.ralphy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ActiveProjectService {
    private final GitRepositoryInitializer gitRepositoryInitializer;
    private final LocalMetadataStorage localMetadataStorage;
    private final NativeWindowsPreflightService nativeWindowsPreflightService;
    private final PresetCatalogService presetCatalogService;
    private final PrdStructureValidator prdStructureValidator;
    private final PrdTaskStateStore prdTaskStateStore;
    private final PrdTaskSynchronizer prdTaskSynchronizer;
    private final RalphPrdJsonMapper ralphPrdJsonMapper;
    private final ProjectMetadataInitializer projectMetadataInitializer;
    private final ProjectStorageInitializer projectStorageInitializer;
    private final WslPreflightService wslPreflightService;
    private final CodexLauncherService codexLauncherService;
    private final boolean autoRunNativeWindowsPreflight;
    private final boolean autoRunWslPreflight;
    private ActiveProject activeProject;
    private NativeWindowsPreflightReport latestNativeWindowsPreflightReport;
    private WslPreflightReport latestWslPreflightReport;
    private PrdInterviewDraft prdInterviewDraft;
    private String activePrdMarkdown;
    private PrdTaskState prdTaskState;
    private PrdTaskSyncResult lastPrdTaskSyncResult;
    private MarkdownPrdExchangeLocations markdownPrdExchangeLocations;
    private String startupRecoveryMessage = "";

    @Autowired
    public ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                                ProjectMetadataInitializer projectMetadataInitializer,
                                ProjectStorageInitializer projectStorageInitializer,
                                LocalMetadataStorage localMetadataStorage,
                                NativeWindowsPreflightService nativeWindowsPreflightService,
                                PrdStructureValidator prdStructureValidator,
                                PrdTaskStateStore prdTaskStateStore,
                                PrdTaskSynchronizer prdTaskSynchronizer,
                                RalphPrdJsonMapper ralphPrdJsonMapper,
                                PresetCatalogService presetCatalogService,
                                CodexLauncherService codexLauncherService,
                                WslPreflightService wslPreflightService,
                                @Value("${ralphy.preflight.native.auto-run:true}") boolean autoRunNativeWindowsPreflight,
                                @Value("${ralphy.preflight.wsl.auto-run:true}") boolean autoRunWslPreflight) {
        this.gitRepositoryInitializer = gitRepositoryInitializer;
        this.projectMetadataInitializer = projectMetadataInitializer;
        this.projectStorageInitializer = projectStorageInitializer;
        this.localMetadataStorage = localMetadataStorage;
        this.nativeWindowsPreflightService = nativeWindowsPreflightService;
        this.prdStructureValidator = prdStructureValidator;
        this.prdTaskStateStore = prdTaskStateStore;
        this.prdTaskSynchronizer = prdTaskSynchronizer;
        this.ralphPrdJsonMapper = ralphPrdJsonMapper;
        this.presetCatalogService = presetCatalogService;
        this.codexLauncherService = codexLauncherService;
        this.wslPreflightService = wslPreflightService;
        this.autoRunNativeWindowsPreflight = autoRunNativeWindowsPreflight;
        this.autoRunWslPreflight = autoRunWslPreflight;
        this.localMetadataStorage.startSession();
        restoreLastActiveProject();
    }

    ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                         ProjectMetadataInitializer projectMetadataInitializer,
                         ProjectStorageInitializer projectStorageInitializer,
                         LocalMetadataStorage localMetadataStorage,
                         NativeWindowsPreflightService nativeWindowsPreflightService,
                         PrdStructureValidator prdStructureValidator,
                         PrdTaskStateStore prdTaskStateStore,
                         PrdTaskSynchronizer prdTaskSynchronizer,
                         RalphPrdJsonMapper ralphPrdJsonMapper,
                         PresetCatalogService presetCatalogService,
                         CodexLauncherService codexLauncherService,
                         WslPreflightService wslPreflightService) {
        this(
                gitRepositoryInitializer,
                projectMetadataInitializer,
                projectStorageInitializer,
                localMetadataStorage,
                nativeWindowsPreflightService,
                prdStructureValidator,
                prdTaskStateStore,
                prdTaskSynchronizer,
                ralphPrdJsonMapper,
                presetCatalogService,
                codexLauncherService,
                wslPreflightService,
                true,
                true
        );
    }

    public synchronized Optional<ActiveProject> activeProject() {
        return Optional.ofNullable(activeProject);
    }

    public synchronized String startupRecoveryMessage() {
        return startupRecoveryMessage;
    }

    public synchronized Optional<RunRecoveryCandidate> latestRunRecoveryState() {
        if (activeProject == null) {
            return Optional.empty();
        }

        return localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                .flatMap(projectRecord -> localMetadataStorage.latestRunMetadataForProject(projectRecord.projectId()))
                .flatMap(this::toRunRecoveryCandidate);
    }

    public synchronized Optional<LocalMetadataStorage.RunMetadataRecord> latestRunMetadata() {
        if (activeProject == null) {
            return Optional.empty();
        }

        return localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                .flatMap(projectRecord -> localMetadataStorage.latestRunMetadataForProject(projectRecord.projectId()));
    }

    public synchronized Optional<ExecutionProfile> executionProfile() {
        if (activeProject == null) {
            return Optional.empty();
        }

        return localMetadataStorage.executionProfileForRepository(activeProject.repositoryPath());
    }

    public synchronized Optional<NativeWindowsPreflightReport> latestNativeWindowsPreflightReport() {
        return Optional.ofNullable(latestNativeWindowsPreflightReport);
    }

    public synchronized Optional<WslPreflightReport> latestWslPreflightReport() {
        return Optional.ofNullable(latestWslPreflightReport);
    }

    public synchronized Optional<PrdInterviewDraft> prdInterviewDraft() {
        return Optional.ofNullable(prdInterviewDraft);
    }

    public synchronized Optional<String> activePrdMarkdown() {
        return Optional.ofNullable(activePrdMarkdown);
    }

    public synchronized Optional<PrdTaskState> prdTaskState() {
        return Optional.ofNullable(prdTaskState);
    }

    public synchronized Optional<PrdTaskSyncResult> lastPrdTaskSyncResult() {
        return Optional.ofNullable(lastPrdTaskSyncResult);
    }

    public synchronized Optional<MarkdownPrdExchangeLocations> markdownPrdExchangeLocations() {
        return Optional.ofNullable(markdownPrdExchangeLocations);
    }

    public synchronized SingleStorySessionAvailability singleStorySessionAvailability(PresetUseCase presetUseCase) {
        if (activeProject == null) {
            return SingleStorySessionAvailability.unavailable(
                    "No active project",
                    "Open a repository before starting a single story session."
            );
        }

        BuiltInPreset preset = presetForSingleStorySession(presetUseCase);
        if (preset == null) {
            return SingleStorySessionAvailability.unavailable(
                    "Preset unavailable",
                    "Select Story Implementation or Retry/Fix before starting a single story session."
            );
        }

        PrdExecutionGate executionGate = prdExecutionGate();
        if (executionGate.executionBlocked()) {
            return SingleStorySessionAvailability.unavailable(executionGate.summary(), executionGate.detail());
        }

        SingleStorySessionAvailability preflightAvailability = preflightAvailability(executionProfile()
                .orElse(ExecutionProfile.nativePowerShell()));
        if (!preflightAvailability.startable()) {
            return preflightAvailability;
        }

        if (prdTaskState == null || prdTaskState.tasks().isEmpty()) {
            return SingleStorySessionAvailability.unavailable(
                    "No task state",
                    "Save a valid PRD so Ralphy can create queued story records before starting execution."
            );
        }

        Optional<PrdTaskRecord> runningTask = prdTaskState.tasks().stream()
                .filter(task -> task.status() == PrdTaskStatus.RUNNING)
                .findFirst();
        if (runningTask.isPresent()) {
            return SingleStorySessionAvailability.unavailable(
                    "Story already running",
                    runningTask.get().taskId() + " is already marked RUNNING and must be reviewed before another story starts."
            );
        }

        Optional<PrdTaskRecord> eligibleTask = eligibleStoryForPreset(presetUseCase);
        if (eligibleTask.isEmpty()) {
            return SingleStorySessionAvailability.unavailable(
                    presetUseCase == PresetUseCase.RETRY_FIX ? "No failed story available" : "No queued story available",
                    presetUseCase == PresetUseCase.RETRY_FIX
                            ? "Select a story with a failed attempt before running Retry/Fix."
                            : "All synced stories are either passed, blocked, or already running."
            );
        }

        PrdTaskRecord task = eligibleTask.get();
        String action = presetUseCase == PresetUseCase.RETRY_FIX ? "retry" : "start";
        return SingleStorySessionAvailability.ready(
                "Ready to " + action + " " + task.taskId(),
                task.taskId() + ": " + task.title() + " will run with " + preset.displayName() + ".",
                task,
                preset
        );
    }

    public synchronized SingleStoryStartResult startEligibleSingleStory(PresetUseCase presetUseCase) {
        return startEligibleSingleStory(presetUseCase, CodexLauncherService.RunOutputListener.noop());
    }

    public synchronized SingleStoryStartResult startEligibleSingleStory(PresetUseCase presetUseCase,
                                                                        CodexLauncherService.RunOutputListener runOutputListener) {
        SingleStorySessionAvailability availability = singleStorySessionAvailability(presetUseCase);
        if (!availability.startable()) {
            return SingleStoryStartResult.failure(availability.summary(), availability.detail());
        }

        ExecutionProfile executionProfile = executionProfile().orElse(ExecutionProfile.nativePowerShell());
        PrdTaskRecord eligibleTask = availability.story();
        CodexLauncherService.CodexLaunchPlan launchPlan;
        try {
            launchPlan = codexLauncherService.buildLaunch(buildLaunchRequest(
                    eligibleTask,
                    availability.preset(),
                    executionProfile
            ));
        } catch (IllegalArgumentException exception) {
            return SingleStoryStartResult.failure(
                    "Unable to start story",
                    "The story launch could not be prepared: " + exception.getMessage()
            );
        }

        try {
            PrdTaskRecord queuedTask = eligibleTask.queueAttempt(
                    launchPlan.runId(),
                    availability.preset(),
                    Instant.now().toString(),
                    "Queued " + availability.preset().displayName() + " for " + eligibleTask.taskId() + "."
            );
            persistPrdTaskState(prdTaskState.replaceTask(queuedTask, queuedTask.updatedAt()));

            PrdTaskRecord runningTask = prdTaskState.taskById(eligibleTask.taskId())
                    .orElseThrow()
                    .startAttempt(
                            launchPlan.runId(),
                            Instant.now().toString(),
                            "Started " + availability.preset().displayName() + " for " + eligibleTask.taskId() + "."
                    );
            persistPrdTaskState(prdTaskState.replaceTask(runningTask, runningTask.updatedAt()));
        } catch (IOException exception) {
            return SingleStoryStartResult.failure(
                    "Unable to persist story state",
                    "The queued or running story state could not be stored: " + exception.getMessage()
            );
        }

        CodexLauncherService.CodexLaunchResult launchResult = null;
        PrdTaskStatus finalStatus = PrdTaskStatus.FAILED;
        String finalMessage;
        String finishedAt = Instant.now().toString();
        try {
            launchResult = codexLauncherService.launch(launchPlan, runOutputListener);
            finalStatus = launchResult.successful() ? PrdTaskStatus.COMPLETED : PrdTaskStatus.FAILED;
            finalMessage = launchResult.successful()
                    ? "Story attempt passed."
                    : firstNonBlank(launchResult.message(), "Story attempt failed.");
            finishedAt = launchResult.endedAt();
        } catch (RuntimeException exception) {
            finalMessage = "Codex launch failed: " + exception.getMessage();
        }

        try {
            PrdTaskRecord finalizedTask = prdTaskState.taskById(eligibleTask.taskId())
                    .orElseThrow()
                    .finishAttempt(launchPlan.runId(), finalStatus, finishedAt, finalMessage);
            persistPrdTaskState(prdTaskState.replaceTask(finalizedTask, finalizedTask.updatedAt()));
            return SingleStoryStartResult.success(
                    eligibleTask.taskId(),
                    finalStatus,
                    finalMessage,
                    finalizedTask.attempts().get(finalizedTask.attempts().size() - 1),
                    launchResult
            );
        } catch (IOException exception) {
            return SingleStoryStartResult.failure(
                    "Unable to persist story outcome",
                    "The final story outcome could not be stored: " + exception.getMessage(),
                    launchResult
            );
        }
    }

    public synchronized PrdExecutionGate prdExecutionGate() {
        if (activeProject == null) {
            return PrdExecutionGate.blocked(
                    "No active project",
                    "Open or create a repository before validating a PRD for execution.",
                    PrdValidationReport.empty()
            );
        }

        if (activePrdMarkdown == null || activePrdMarkdown.isBlank()) {
            return PrdExecutionGate.blocked(
                    "No active PRD",
                    "Execution is blocked until the active project has a saved Markdown PRD.",
                    PrdValidationReport.failure(List.of(new PrdValidationError(
                            "Active PRD",
                            "Generate or save `.ralph-tui/prds/active-prd.md` before execution."
                    )))
            );
        }

        PrdValidationReport validationReport = prdStructureValidator.validate(activePrdMarkdown);
        if (!validationReport.valid()) {
            return PrdExecutionGate.blocked(
                    "PRD validation failed",
                    "Execution is blocked while structural validation errors remain.",
                    validationReport
            );
        }

        return PrdExecutionGate.ready(
                "PRD ready for execution",
                "The active PRD has the required sections, a Quality Gates section, and valid story headers.",
                validationReport
        );
    }

    private SingleStorySessionAvailability preflightAvailability(ExecutionProfile executionProfile) {
        if (executionProfile.type() == ExecutionProfile.ProfileType.WSL) {
            if (latestWslPreflightReport == null) {
                return SingleStorySessionAvailability.unavailable(
                        "WSL preflight not run",
                        "Run WSL preflight before starting a WSL story session."
                );
            }
            if (!latestWslPreflightReport.passed()) {
                return SingleStorySessionAvailability.unavailable(
                        "WSL preflight blocked",
                        "WSL execution stays blocked until every WSL preflight check passes."
                );
            }
            return SingleStorySessionAvailability.ready("", "", null, null);
        }

        if (latestNativeWindowsPreflightReport == null) {
            return SingleStorySessionAvailability.unavailable(
                    "Native preflight not run",
                    "Run native preflight before starting a PowerShell story session."
            );
        }
        if (!latestNativeWindowsPreflightReport.passed()) {
            return SingleStorySessionAvailability.unavailable(
                    "Native preflight blocked",
                    "Native PowerShell execution stays blocked until every native preflight check passes."
            );
        }
        return SingleStorySessionAvailability.ready("", "", null, null);
    }

    private BuiltInPreset presetForSingleStorySession(PresetUseCase presetUseCase) {
        if (presetUseCase != PresetUseCase.STORY_IMPLEMENTATION && presetUseCase != PresetUseCase.RETRY_FIX) {
            return null;
        }
        return presetCatalogService.defaultPreset(presetUseCase);
    }

    private Optional<PrdTaskRecord> eligibleStoryForPreset(PresetUseCase presetUseCase) {
        if (prdTaskState == null) {
            return Optional.empty();
        }

        return switch (presetUseCase) {
            case RETRY_FIX -> prdTaskState.tasks().stream()
                    .filter(task -> task.status() == PrdTaskStatus.FAILED)
                    .findFirst();
            case STORY_IMPLEMENTATION -> prdTaskState.tasks().stream()
                    .filter(task -> task.status() == PrdTaskStatus.READY)
                    .findFirst();
            default -> Optional.empty();
        };
    }

    private CodexLauncherService.CodexLaunchRequest buildLaunchRequest(PrdTaskRecord task,
                                                                      BuiltInPreset preset,
                                                                      ExecutionProfile executionProfile) {
        List<CodexLauncherService.PromptInput> promptInputs = new ArrayList<>();
        promptInputs.add(new CodexLauncherService.PromptInput("Story", task.taskId() + ": " + task.title()));
        promptInputs.add(new CodexLauncherService.PromptInput("Outcome", task.outcome()));
        if (prdTaskState != null && !prdTaskState.qualityGates().isEmpty()) {
            promptInputs.add(new CodexLauncherService.PromptInput(
                    "Quality Gates",
                    String.join(System.lineSeparator(), prdTaskState.qualityGates())
            ));
        }

        return new CodexLauncherService.CodexLaunchRequest(
                task.taskId(),
                activeProject,
                executionProfile,
                preset,
                promptInputs,
                "",
                List.of("--json")
        );
    }

    private void persistPrdTaskState(PrdTaskState replacementState) throws IOException {
        Objects.requireNonNull(replacementState, "replacementState must not be null");
        prdTaskStateStore.write(activeProject, replacementState);
        prdTaskState = replacementState;
    }

    public synchronized ExecutionProfileSaveResult saveExecutionProfile(ExecutionProfile executionProfile) {
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");
        if (activeProject == null) {
            return ExecutionProfileSaveResult.failure(
                    "Open or create a repository before saving an execution profile."
            );
        }

        String validationMessage = validateExecutionProfile(executionProfile);
        if (!validationMessage.isBlank()) {
            return ExecutionProfileSaveResult.failure(validationMessage);
        }

        Optional<LocalMetadataStorage.ProjectRecord> projectRecord =
                localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath());
        if (projectRecord.isEmpty()) {
            return ExecutionProfileSaveResult.failure(
                    "The active repository is missing local metadata. Reopen the repository and try again."
            );
        }

        ExecutionProfile savedProfile =
                localMetadataStorage.saveExecutionProfile(projectRecord.get().projectId(), executionProfile);
        if (savedProfile.type() == ExecutionProfile.ProfileType.POWERSHELL) {
            runNativeWindowsPreflightInternal();
        } else {
            clearWslPreflightState();
            if (autoRunWslPreflight) {
                runWslPreflightInternal();
            }
        }
        return ExecutionProfileSaveResult.success(savedProfile);
    }

    public synchronized PrdInterviewDraftSaveResult savePrdInterviewDraft(PrdInterviewDraft replacementDraft) {
        Objects.requireNonNull(replacementDraft, "replacementDraft must not be null");
        if (activeProject == null) {
            return PrdInterviewDraftSaveResult.failure(
                    "Open or create a repository before saving PRD interview answers."
            );
        }

        try {
            projectMetadataInitializer.writePrdInterviewDraft(activeProject, replacementDraft);
            prdInterviewDraft = replacementDraft;
            return PrdInterviewDraftSaveResult.success(replacementDraft);
        } catch (IOException exception) {
            return PrdInterviewDraftSaveResult.failure(
                    replacementDraft,
                    "Unable to store PRD interview answers: " + exception.getMessage()
            );
        }
    }

    public synchronized ActivePrdSaveResult saveActivePrd(String markdown) {
        Objects.requireNonNull(markdown, "markdown must not be null");
        if (activeProject == null) {
            return ActivePrdSaveResult.failure("Open or create a repository before saving a Markdown PRD.");
        }

        try {
            Files.createDirectories(activeProject.prdsDirectoryPath());
            Path activePrdPath = activeProject.activePrdPath();
            Path temporaryPrdPath = activePrdPath.resolveSibling(activePrdPath.getFileName() + ".tmp");
            Files.writeString(temporaryPrdPath, markdown, StandardCharsets.UTF_8);
            moveIntoPlace(temporaryPrdPath, activePrdPath);
            activePrdMarkdown = markdown;
            lastPrdTaskSyncResult = syncActivePrdTaskStateInternal(false);
            return ActivePrdSaveResult.success(markdown, activePrdPath);
        } catch (IOException exception) {
            return ActivePrdSaveResult.failure(
                    markdown,
                    activeProject.activePrdPath(),
                    "Unable to store the active Markdown PRD: " + exception.getMessage()
            );
        }
    }

    public synchronized PrdTaskSyncResult syncActivePrdTaskState(boolean confirmDestructiveRemap) {
        lastPrdTaskSyncResult = syncActivePrdTaskStateInternal(confirmDestructiveRemap);
        return lastPrdTaskSyncResult;
    }

    public synchronized PrdJsonImportResult importPrdJson(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        if (activeProject == null) {
            return PrdJsonImportResult.failure(
                    "Open or create a repository before importing prd.json."
            );
        }
        if (activePrdMarkdown == null || activePrdMarkdown.isBlank()) {
            return PrdJsonImportResult.failure(
                    "Generate, save, or import a Markdown PRD before importing prd.json."
            );
        }

        Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSourcePath)) {
            return PrdJsonImportResult.failure(
                    "Select an existing compatible prd.json file to import: " + normalizedSourcePath
            );
        }

        PrdValidationReport validationReport = prdStructureValidator.validate(activePrdMarkdown);
        if (!validationReport.valid()) {
            return PrdJsonImportResult.failure(
                    "Resolve PRD validation errors before importing prd.json."
            );
        }

        final PrdTaskStateStore.ImportedPrdJson importedPrdJson;
        try {
            importedPrdJson = prdTaskStateStore.readCompatibleImport(normalizedSourcePath, activeProject);
        } catch (IOException exception) {
            return PrdJsonImportResult.failure(
                    normalizedSourcePath,
                    activeProject.activePrdJsonPath(),
                    "Unable to import prd.json: " + exception.getMessage()
            );
        }

        String timestamp = Instant.now().toString();
        PrdTaskSynchronizer.SyncPlan markdownSyncPlan =
                prdTaskSynchronizer.plan(activePrdMarkdown, importedPrdJson.taskState());
        PrdTaskState markdownViewState = prdTaskSynchronizer.synchronize(
                activeProject,
                activePrdMarkdown,
                importedPrdJson.taskState(),
                timestamp
        );
        List<String> conflictDetails = buildPrdJsonConflictDetails(
                importedPrdJson.document(),
                markdownViewState,
                markdownSyncPlan
        );

        if (!markdownSyncPlan.addedTaskIds().isEmpty() || !markdownSyncPlan.removedTaskIds().isEmpty()) {
            return PrdJsonImportResult.failure(
                    normalizedSourcePath,
                    activeProject.activePrdJsonPath(),
                    markdownSyncPlan,
                    conflictDetails,
                    buildBlockedPrdJsonImportMessage(markdownSyncPlan)
            );
        }

        PrdTaskState mergedImportedState = mergeImportedTaskState(importedPrdJson.taskState(), timestamp);
        PrdTaskState reconciledState = prdTaskSynchronizer.synchronize(
                activeProject,
                activePrdMarkdown,
                mergedImportedState,
                timestamp
        );

        try {
            prdTaskStateStore.write(activeProject, reconciledState);
            prdTaskState = reconciledState;
            lastPrdTaskSyncResult = PrdTaskSyncResult.success(
                    reconciledState,
                    markdownSyncPlan,
                    conflictDetails.isEmpty()
                            ? "Imported compatible prd.json into internal task state."
                            : "Imported compatible prd.json while preserving Markdown as the canonical definition."
            );
            return PrdJsonImportResult.success(
                    reconciledState,
                    normalizedSourcePath,
                    activeProject.activePrdJsonPath(),
                    markdownSyncPlan,
                    conflictDetails,
                    buildSuccessfulPrdJsonImportMessage(
                            normalizedSourcePath,
                            activeProject.activePrdJsonPath(),
                            reconciledState,
                            conflictDetails
                    )
            );
        } catch (IOException exception) {
            return PrdJsonImportResult.failure(
                    normalizedSourcePath,
                    activeProject.activePrdJsonPath(),
                    markdownSyncPlan,
                    conflictDetails,
                    "Unable to store reconciled prd.json state: " + exception.getMessage()
            );
        }
    }

    public synchronized MarkdownPrdImportResult importMarkdownPrd(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        if (activeProject == null) {
            return MarkdownPrdImportResult.failure(
                    "Open or create a repository before importing a Markdown PRD."
            );
        }

        Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSourcePath)) {
            return MarkdownPrdImportResult.failure(
                    "Select an existing Markdown PRD file to import: " + normalizedSourcePath
            );
        }

        final String markdown;
        try {
            markdown = Files.readString(normalizedSourcePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return MarkdownPrdImportResult.failure(
                    "Unable to read the selected Markdown PRD: " + exception.getMessage()
            );
        }

        ActivePrdSaveResult saveResult = saveActivePrd(markdown);
        if (!saveResult.successful()) {
            return MarkdownPrdImportResult.failure(markdown, saveResult.path(), normalizedSourcePath, saveResult.message());
        }

        MarkdownPrdExchangeLocations updatedLocations = currentMarkdownPrdExchangeLocations()
                .withLastImportedPath(normalizedSourcePath.toString());
        try {
            projectMetadataInitializer.writeMarkdownPrdExchangeLocations(activeProject, updatedLocations);
            markdownPrdExchangeLocations = updatedLocations;
            return MarkdownPrdImportResult.success(markdown, saveResult.path(), normalizedSourcePath);
        } catch (IOException exception) {
            return MarkdownPrdImportResult.failure(
                    markdown,
                    saveResult.path(),
                    normalizedSourcePath,
                    "Imported Markdown PRD saved, but the import location could not be tracked: "
                            + exception.getMessage()
            );
        }
    }

    public synchronized MarkdownPrdExportResult exportActivePrd(Path destinationPath) {
        Objects.requireNonNull(destinationPath, "destinationPath must not be null");
        if (activeProject == null) {
            return MarkdownPrdExportResult.failure(
                    "Open or create a repository before exporting a Markdown PRD."
            );
        }
        if (activePrdMarkdown == null) {
            return MarkdownPrdExportResult.failure(
                    "Generate or import a Markdown PRD before exporting it."
            );
        }

        Path normalizedDestinationPath = destinationPath.toAbsolutePath().normalize();
        try {
            Path parentDirectory = normalizedDestinationPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            Path temporaryExportPath = normalizedDestinationPath.resolveSibling(
                    normalizedDestinationPath.getFileName() + ".tmp"
            );
            Files.writeString(temporaryExportPath, activePrdMarkdown, StandardCharsets.UTF_8);
            moveIntoPlace(temporaryExportPath, normalizedDestinationPath);
        } catch (IOException exception) {
            return MarkdownPrdExportResult.failure(
                    normalizedDestinationPath,
                    "Unable to export the active Markdown PRD: " + exception.getMessage()
            );
        }

        MarkdownPrdExchangeLocations updatedLocations = currentMarkdownPrdExchangeLocations()
                .withLastExportedPath(normalizedDestinationPath.toString());
        try {
            projectMetadataInitializer.writeMarkdownPrdExchangeLocations(activeProject, updatedLocations);
            markdownPrdExchangeLocations = updatedLocations;
            return MarkdownPrdExportResult.success(normalizedDestinationPath);
        } catch (IOException exception) {
            return MarkdownPrdExportResult.failure(
                    normalizedDestinationPath,
                    "Exported the active Markdown PRD, but the export location could not be tracked: "
                            + exception.getMessage()
            );
        }
    }

    public synchronized NativeWindowsPreflightRunResult runNativeWindowsPreflight() {
        if (activeProject == null) {
            return NativeWindowsPreflightRunResult.failure(
                    "Open or create a repository before running native Windows preflight."
            );
        }

        return runNativeWindowsPreflightInternal();
    }

    public synchronized WslPreflightRunResult runWslPreflight() {
        if (activeProject == null) {
            return WslPreflightRunResult.failure(
                    "Open or create a repository before running WSL preflight."
            );
        }
        if (executionProfile().orElse(ExecutionProfile.nativePowerShell()).type() != ExecutionProfile.ProfileType.WSL) {
            return WslPreflightRunResult.failure(
                    "Save a WSL execution profile before running WSL preflight."
            );
        }

        return runWslPreflightInternal();
    }

    public synchronized ProjectActivationResult openRepository(Path selectedDirectory) {
        Path normalizedDirectory = selectedDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDirectory)) {
            return ProjectActivationResult.failure("Selected path is not a folder: " + normalizedDirectory);
        }

        if (!isGitRepository(normalizedDirectory)) {
            return ProjectActivationResult.failure("Selected folder is not a Git repository: " + normalizedDirectory);
        }

        return activateProject(normalizedDirectory);
    }

    public synchronized ProjectActivationResult createRepository(Path parentDirectory, String projectDirectoryName) {
        if (parentDirectory == null) {
            return ProjectActivationResult.failure("Select a parent folder for the new repository.");
        }

        Path normalizedParentDirectory = parentDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedParentDirectory)) {
            return ProjectActivationResult.failure("Selected parent path is not a folder: " + normalizedParentDirectory);
        }

        String normalizedProjectDirectoryName = normalizeProjectDirectoryName(projectDirectoryName);
        if (normalizedProjectDirectoryName.isBlank()) {
            return ProjectActivationResult.failure("Enter a project folder name.");
        }

        if (!isSingleDirectoryName(normalizedProjectDirectoryName)) {
            return ProjectActivationResult.failure("Project folder name must be a single folder name.");
        }

        Path repositoryDirectory = normalizedParentDirectory.resolve(normalizedProjectDirectoryName).normalize();
        if (!normalizedParentDirectory.equals(repositoryDirectory.getParent())) {
            return ProjectActivationResult.failure("Project folder name must stay within the selected parent folder.");
        }

        if (Files.exists(repositoryDirectory)) {
            return ProjectActivationResult.failure("Project folder already exists: " + repositoryDirectory);
        }

        try {
            Files.createDirectory(repositoryDirectory);
        } catch (IOException exception) {
            return ProjectActivationResult.failure("Unable to create project folder: " + repositoryDirectory
                    + " (" + exception.getMessage() + ")");
        }

        GitRepositoryInitializer.InitializationResult initializationResult =
                gitRepositoryInitializer.initializeRepository(repositoryDirectory);
        if (!initializationResult.successful()) {
            return ProjectActivationResult.failure(rollbackNewRepository(
                    repositoryDirectory,
                    initializationResult.message()
            ));
        }

        ProjectActivationResult activationResult = activateProject(new ActiveProject(repositoryDirectory));
        if (!activationResult.successful()) {
            return ProjectActivationResult.failure(rollbackNewRepository(
                    repositoryDirectory,
                    activationResult.message()
            ));
        }

        return activationResult;
    }

    private ProjectActivationResult activateProject(Path repositoryDirectory) {
        return activateProject(new ActiveProject(repositoryDirectory));
    }

    private ProjectActivationResult activateProject(ActiveProject candidateProject) {
        try {
            projectStorageInitializer.ensureStorageDirectories(candidateProject);
            projectMetadataInitializer.writeMetadata(candidateProject);
        } catch (IOException exception) {
            return ProjectActivationResult.failure("Unable to prepare project storage: " + exception.getMessage());
        }

        activeProject = candidateProject;
        localMetadataStorage.recordProjectActivation(candidateProject);
        refreshPreflightState();
        refreshPrdInterviewDraft();
        refreshActivePrd();
        refreshPrdTaskState();
        refreshMarkdownPrdExchangeLocations();
        lastPrdTaskSyncResult = activePrdMarkdown == null || activePrdMarkdown.isBlank()
                ? null
                : syncActivePrdTaskStateInternal(false);
        startupRecoveryMessage = "";
        return ProjectActivationResult.success(candidateProject);
    }

    private void restoreLastActiveProject() {
        Optional<LocalMetadataStorage.ProjectRecord> lastActiveProjectRecord =
                localMetadataStorage.lastActiveProjectRecord();
        if (lastActiveProjectRecord.isEmpty()) {
            return;
        }

        String repositoryPathText = lastActiveProjectRecord.get().repositoryPath();
        Path repositoryPath;
        try {
            repositoryPath = Path.of(repositoryPathText).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            startupRecoveryMessage = "Last active repository path is invalid: " + repositoryPathText;
            return;
        }

        if (!isGitRepository(repositoryPath)) {
            startupRecoveryMessage = "Last active repository could not be restored because it is missing or no longer "
                    + "a Git repository: " + repositoryPath
                    + ". Open an existing repository or create a new one to continue.";
            return;
        }

        ProjectActivationResult restoreResult = activateProject(repositoryPath);
        if (!restoreResult.successful()) {
            startupRecoveryMessage = "Last active repository could not be restored: " + restoreResult.message();
        }
    }

    private boolean isGitRepository(Path repositoryDirectory) {
        return Files.isDirectory(repositoryDirectory) && Files.exists(repositoryDirectory.resolve(".git"));
    }

    private void refreshPreflightState() {
        latestNativeWindowsPreflightReport = readStoredNativeWindowsPreflight().orElse(null);
        latestWslPreflightReport = readStoredWslPreflight().orElse(null);
        ExecutionProfile executionProfile = executionProfile().orElse(ExecutionProfile.nativePowerShell());
        if (executionProfile.type() == ExecutionProfile.ProfileType.POWERSHELL && autoRunNativeWindowsPreflight) {
            runNativeWindowsPreflightInternal();
        } else if (autoRunWslPreflight && latestWslPreflightReport == null) {
            runWslPreflightInternal();
        }
    }

    private Optional<NativeWindowsPreflightReport> readStoredNativeWindowsPreflight() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return projectMetadataInitializer.readNativeWindowsPreflight(activeProject);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<WslPreflightReport> readStoredWslPreflight() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return projectMetadataInitializer.readWslPreflight(activeProject);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private void refreshPrdInterviewDraft() {
        prdInterviewDraft = readStoredPrdInterviewDraft().orElse(null);
    }

    private void refreshActivePrd() {
        activePrdMarkdown = readStoredActivePrd().orElse(null);
    }

    private void refreshPrdTaskState() {
        prdTaskState = readStoredPrdTaskState().orElse(null);
    }

    private void refreshMarkdownPrdExchangeLocations() {
        markdownPrdExchangeLocations = readStoredMarkdownPrdExchangeLocations().orElse(null);
    }

    private Optional<PrdInterviewDraft> readStoredPrdInterviewDraft() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return projectMetadataInitializer.readPrdInterviewDraft(activeProject);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> readStoredActivePrd() {
        if (activeProject == null || !Files.exists(activeProject.activePrdPath())) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readString(activeProject.activePrdPath(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<PrdTaskState> readStoredPrdTaskState() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return prdTaskStateStore.read(activeProject);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<MarkdownPrdExchangeLocations> readStoredMarkdownPrdExchangeLocations() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return projectMetadataInitializer.readMarkdownPrdExchangeLocations(activeProject);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private MarkdownPrdExchangeLocations currentMarkdownPrdExchangeLocations() {
        return markdownPrdExchangeLocations == null
                ? new MarkdownPrdExchangeLocations(null, null)
                : markdownPrdExchangeLocations;
    }

    private List<String> buildPrdJsonConflictDetails(RalphPrdJsonDocument importedDocument,
                                                     PrdTaskState markdownViewState,
                                                     PrdTaskSynchronizer.SyncPlan markdownSyncPlan) {
        RalphPrdJsonDocument markdownDocument = ralphPrdJsonMapper.toDocument(activeProject, activePrdMarkdown, markdownViewState);
        List<String> conflictDetails = new ArrayList<>();
        if (!markdownSyncPlan.addedTaskIds().isEmpty()) {
            conflictDetails.add("Stories only in active Markdown: "
                    + String.join(", ", markdownSyncPlan.addedTaskIds()) + ".");
        }
        if (!markdownSyncPlan.removedTaskIds().isEmpty()) {
            conflictDetails.add("Stories only in imported prd.json: "
                    + String.join(", ", markdownSyncPlan.removedTaskIds()) + ".");
        }
        if (!normalizeComparisonValue(markdownDocument.name()).equals(normalizeComparisonValue(importedDocument.name()))) {
            conflictDetails.add("PRD title differs between active Markdown and imported prd.json.");
        }
        if (!normalizeComparisonValue(markdownDocument.description())
                .equals(normalizeComparisonValue(importedDocument.description()))) {
            conflictDetails.add("Overview description differs between active Markdown and imported prd.json.");
        }
        if (!normalizeListForComparison(markdownDocument.qualityGates())
                .equals(normalizeListForComparison(importedDocument.qualityGates()))) {
            conflictDetails.add("Quality Gates differ between active Markdown and imported prd.json.");
        }

        Map<String, RalphPrdJsonUserStory> markdownStoriesById = indexStoriesById(markdownDocument.userStories());
        Map<String, RalphPrdJsonUserStory> importedStoriesById = indexStoriesById(importedDocument.userStories());
        for (String storyId : markdownStoriesById.keySet()) {
            if (!importedStoriesById.containsKey(storyId)) {
                continue;
            }

            RalphPrdJsonUserStory markdownStory = markdownStoriesById.get(storyId);
            RalphPrdJsonUserStory importedStory = importedStoriesById.get(storyId);
            List<String> driftedFields = new ArrayList<>();
            if (!normalizeComparisonValue(markdownStory.title()).equals(normalizeComparisonValue(importedStory.title()))) {
                driftedFields.add("title");
            }
            if (!normalizeComparisonValue(markdownStory.description())
                    .equals(normalizeComparisonValue(importedStory.description()))) {
                driftedFields.add("description");
            }
            if (!normalizeListForComparison(markdownStory.acceptanceCriteria())
                    .equals(normalizeListForComparison(importedStory.acceptanceCriteria()))) {
                driftedFields.add("acceptance criteria");
            }
            if (!normalizeListForComparison(markdownStory.dependsOn())
                    .equals(normalizeListForComparison(importedStory.dependsOn()))) {
                driftedFields.add("dependencies");
            }
            if (markdownStory.priority() != importedStory.priority()) {
                driftedFields.add("priority/order");
            }

            if (!driftedFields.isEmpty()) {
                conflictDetails.add(storyId + " differs between active Markdown and imported prd.json for "
                        + String.join(", ", driftedFields) + ".");
            }
        }

        return conflictDetails;
    }

    private Map<String, RalphPrdJsonUserStory> indexStoriesById(List<RalphPrdJsonUserStory> userStories) {
        Map<String, RalphPrdJsonUserStory> indexedStories = new LinkedHashMap<>();
        for (RalphPrdJsonUserStory userStory : userStories) {
            indexedStories.put(userStory.id(), userStory);
        }
        return indexedStories;
    }

    private String buildBlockedPrdJsonImportMessage(PrdTaskSynchronizer.SyncPlan markdownSyncPlan) {
        List<String> changeSummary = new ArrayList<>();
        if (!markdownSyncPlan.addedTaskIds().isEmpty()) {
            changeSummary.add("Markdown only: " + String.join(", ", markdownSyncPlan.addedTaskIds()));
        }
        if (!markdownSyncPlan.removedTaskIds().isEmpty()) {
            changeSummary.add("prd.json only: " + String.join(", ", markdownSyncPlan.removedTaskIds()));
        }
        return "Import blocked because the active Markdown PRD and imported prd.json do not describe the same story IDs ("
                + String.join("; ", changeSummary) + ").";
    }

    private String buildSuccessfulPrdJsonImportMessage(Path importedFromPath,
                                                       Path activePrdJsonPath,
                                                       PrdTaskState reconciledState,
                                                       List<String> conflictDetails) {
        String baseMessage = "Imported compatible prd.json from "
                + importedFromPath
                + " into "
                + activePrdJsonPath
                + " and reconciled "
                + reconciledState.tasks().size()
                + " stories.";
        if (conflictDetails.isEmpty()) {
            return baseMessage;
        }
        return baseMessage + " Markdown remains the source of truth for conflicting definitions.";
    }

    private PrdTaskState mergeImportedTaskState(PrdTaskState importedTaskState, String timestamp) {
        if (prdTaskState == null) {
            return importedTaskState;
        }

        Map<String, PrdTaskRecord> currentTasksById = indexTaskRecordsById(prdTaskState.tasks());
        List<PrdTaskRecord> mergedTasks = new ArrayList<>(importedTaskState.tasks().size());
        for (PrdTaskRecord importedTask : importedTaskState.tasks()) {
            PrdTaskRecord currentTask = currentTasksById.get(importedTask.taskId());
            mergedTasks.add(currentTask == null
                    ? importedTask
                    : mergeTaskRecord(currentTask, importedTask, timestamp));
        }

        return new PrdTaskState(
                PrdTaskState.SCHEMA_VERSION,
                importedTaskState.sourcePrdPath(),
                importedTaskState.qualityGates(),
                mergedTasks,
                earliestTimestamp(prdTaskState.createdAt(), importedTaskState.createdAt(), timestamp),
                latestTimestamp(prdTaskState.updatedAt(), importedTaskState.updatedAt(), timestamp)
        );
    }

    private Map<String, PrdTaskRecord> indexTaskRecordsById(List<PrdTaskRecord> tasks) {
        Map<String, PrdTaskRecord> indexedTasks = new LinkedHashMap<>();
        for (PrdTaskRecord task : tasks) {
            indexedTasks.put(task.taskId(), task);
        }
        return indexedTasks;
    }

    private PrdTaskRecord mergeTaskRecord(PrdTaskRecord currentTask,
                                          PrdTaskRecord importedTask,
                                          String timestamp) {
        boolean importedStatusAlreadyRecorded = importedTask.history().stream()
                .anyMatch(entry -> entry.status() == importedTask.status());
        boolean addImportedStatusEntry = currentTask.status() != importedTask.status() && !importedStatusAlreadyRecorded;

        LinkedHashSet<PrdTaskHistoryEntry> mergedHistoryEntries = new LinkedHashSet<>(currentTask.history());
        mergedHistoryEntries.addAll(importedTask.history());
        if (addImportedStatusEntry) {
            mergedHistoryEntries.add(new PrdTaskHistoryEntry(
                    timestamp,
                    "STATUS_CHANGE",
                    importedTask.status(),
                    "Reconciled status from imported prd.json."
            ));
        }

        List<PrdTaskHistoryEntry> orderedHistory = mergedHistoryEntries.stream()
                .sorted(Comparator.comparing(PrdTaskHistoryEntry::timestamp)
                        .thenComparing(PrdTaskHistoryEntry::type)
                        .thenComparing(entry -> entry.status().name())
                        .thenComparing(PrdTaskHistoryEntry::message))
                .toList();
        List<PrdStoryAttemptRecord> orderedAttempts = mergeTaskAttempts(currentTask, importedTask);

        return new PrdTaskRecord(
                importedTask.taskId(),
                importedTask.title(),
                importedTask.outcome(),
                importedTask.status(),
                orderedHistory,
                orderedAttempts,
                earliestTimestamp(currentTask.createdAt(), importedTask.createdAt(), timestamp),
                addImportedStatusEntry
                        ? timestamp
                        : latestTimestamp(currentTask.updatedAt(), importedTask.updatedAt(), timestamp)
        );
    }

    private List<PrdStoryAttemptRecord> mergeTaskAttempts(PrdTaskRecord currentTask, PrdTaskRecord importedTask) {
        LinkedHashMap<String, PrdStoryAttemptRecord> attemptsByRunId = new LinkedHashMap<>();
        for (PrdStoryAttemptRecord attempt : currentTask.attempts()) {
            attemptsByRunId.put(attempt.runId(), attempt);
        }
        for (PrdStoryAttemptRecord attempt : importedTask.attempts()) {
            attemptsByRunId.put(attempt.runId(), attempt);
        }

        return attemptsByRunId.values().stream()
                .sorted(Comparator.comparing(this::attemptSortKey)
                        .thenComparing(PrdStoryAttemptRecord::runId))
                .toList();
    }

    private String attemptSortKey(PrdStoryAttemptRecord attempt) {
        return firstNonBlank(attempt.queuedAt(), attempt.startedAt(), attempt.endedAt(), "");
    }

    private String earliestTimestamp(String... values) {
        return Stream.of(values)
                .filter(this::hasText)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String latestTimestamp(String... values) {
        return Stream.of(values)
                .filter(this::hasText)
                .sorted()
                .reduce((left, right) -> right)
                .orElse("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private List<String> normalizeListForComparison(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .filter(this::hasText)
                .map(this::normalizeComparisonValue)
                .toList();
    }

    private String normalizeComparisonValue(String value) {
        return hasText(value)
                ? value.trim().replace("\r\n", "\n").replace('\r', '\n')
                : "";
    }

    private PrdTaskSyncResult syncActivePrdTaskStateInternal(boolean confirmDestructiveRemap) {
        if (activeProject == null) {
            return PrdTaskSyncResult.failure("Open or create a repository before syncing PRD task state.");
        }
        if (activePrdMarkdown == null || activePrdMarkdown.isBlank()) {
            return PrdTaskSyncResult.failure("Generate, save, or import a Markdown PRD before syncing task state.");
        }

        PrdValidationReport validationReport = prdStructureValidator.validate(activePrdMarkdown);
        if (!validationReport.valid()) {
            return PrdTaskSyncResult.failure(
                    "Resolve PRD validation errors before syncing internal task state."
            );
        }

        PrdTaskSynchronizer.SyncPlan syncPlan = prdTaskSynchronizer.plan(activePrdMarkdown, prdTaskState);
        if (syncPlan.destructive() && !confirmDestructiveRemap) {
            return PrdTaskSyncResult.confirmationRequired(
                    syncPlan,
                    "Story ID changes would remove existing task records: "
                            + String.join(", ", syncPlan.removedTaskIds())
                            + ". Confirm the destructive remap to apply the new task state."
            );
        }

        String timestamp = Instant.now().toString();
        PrdTaskState synchronizedState = prdTaskSynchronizer.synchronize(
                activeProject,
                activePrdMarkdown,
                prdTaskState,
                timestamp
        );
        if (prdTaskState != null
                && syncPlan.addedTaskIds().isEmpty()
                && syncPlan.updatedTaskIds().isEmpty()
                && syncPlan.removedTaskIds().isEmpty()
                && prdTaskState.qualityGates().equals(synchronizedState.qualityGates())
                && prdTaskState.sourcePrdPath().equals(synchronizedState.sourcePrdPath())
                && prdTaskStateStore.isCompatibleExport(activeProject)) {
            return PrdTaskSyncResult.success(prdTaskState, syncPlan, "Internal task state already matches the active PRD.");
        }

        try {
            prdTaskStateStore.write(activeProject, synchronizedState);
            prdTaskState = synchronizedState;
            return PrdTaskSyncResult.success(
                    synchronizedState,
                    syncPlan,
                    buildPrdTaskSyncMessage(syncPlan, synchronizedState)
            );
        } catch (IOException exception) {
            return PrdTaskSyncResult.failure(
                    syncPlan,
                    "Unable to store internal PRD task state: " + exception.getMessage()
            );
        }
    }

    private String buildPrdTaskSyncMessage(PrdTaskSynchronizer.SyncPlan syncPlan, PrdTaskState synchronizedState) {
        List<String> changeSummary = new ArrayList<>();
        if (!syncPlan.addedTaskIds().isEmpty()) {
            changeSummary.add(syncPlan.addedTaskIds().size() + " added");
        }
        if (!syncPlan.updatedTaskIds().isEmpty()) {
            changeSummary.add(syncPlan.updatedTaskIds().size() + " updated");
        }
        if (!syncPlan.removedTaskIds().isEmpty()) {
            changeSummary.add(syncPlan.removedTaskIds().size() + " removed");
        }

        if (changeSummary.isEmpty()) {
            return "Internal task state already matches the active PRD.";
        }

        return "Synced " + synchronizedState.tasks().size() + " PRD task records ("
                + String.join(", ", changeSummary) + ").";
    }

    private void moveIntoPlace(Path temporaryPath, Path destinationPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    destinationPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private NativeWindowsPreflightRunResult runNativeWindowsPreflightInternal() {
        if (activeProject == null) {
            return NativeWindowsPreflightRunResult.failure(
                    "Open or create a repository before running native Windows preflight."
            );
        }

        NativeWindowsPreflightReport report = nativeWindowsPreflightService.run(activeProject);
        latestNativeWindowsPreflightReport = report;
        try {
            projectMetadataInitializer.writeNativeWindowsPreflight(activeProject, report);
            return NativeWindowsPreflightRunResult.success(report);
        } catch (IOException exception) {
            return NativeWindowsPreflightRunResult.failure(
                    report,
                    "Unable to store the native Windows preflight result: " + exception.getMessage()
            );
        }
    }

    private WslPreflightRunResult runWslPreflightInternal() {
        if (activeProject == null) {
            return WslPreflightRunResult.failure(
                    "Open or create a repository before running WSL preflight."
            );
        }

        ExecutionProfile executionProfile = executionProfile().orElse(null);
        if (executionProfile == null || executionProfile.type() != ExecutionProfile.ProfileType.WSL) {
            return WslPreflightRunResult.failure(
                    "Save a WSL execution profile before running WSL preflight."
            );
        }

        WslPreflightReport report = wslPreflightService.run(activeProject, executionProfile);
        latestWslPreflightReport = report;
        try {
            projectMetadataInitializer.writeWslPreflight(activeProject, report);
            return WslPreflightRunResult.success(report);
        } catch (IOException exception) {
            return WslPreflightRunResult.failure(
                    report,
                    "Unable to store the WSL preflight result: " + exception.getMessage()
            );
        }
    }

    private void clearWslPreflightState() {
        latestWslPreflightReport = null;
        if (activeProject == null) {
            return;
        }

        try {
            projectMetadataInitializer.writeWslPreflight(activeProject, null);
        } catch (IOException ignored) {
            // Ignore metadata-clear failures here because the execution profile itself was already saved.
        }
    }

    private Optional<RunRecoveryCandidate> toRunRecoveryCandidate(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        RunRecoveryAction recoveryAction = determineRunRecoveryAction(runMetadataRecord);
        if (recoveryAction == null) {
            return Optional.empty();
        }

        return Optional.of(new RunRecoveryCandidate(
                runMetadataRecord.runId(),
                runMetadataRecord.storyId(),
                displayStatus(runMetadataRecord.status()),
                recoveryAction
        ));
    }

    private RunRecoveryAction determineRunRecoveryAction(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        String normalizedStatus = normalizeStatus(runMetadataRecord.status());
        return switch (normalizedStatus) {
            case "COMPLETED", "DONE", "SUCCESS", "SUCCEEDED" -> null;
            case "ABORTED", "CANCELED", "CANCELLED", "ERROR", "FAILED", "NEEDS_REVIEW", "REVIEWABLE" ->
                    RunRecoveryAction.REVIEWABLE;
            default -> hasEnded(runMetadataRecord) ? RunRecoveryAction.REVIEWABLE : RunRecoveryAction.RESUMABLE;
        };
    }

    private boolean hasEnded(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        return runMetadataRecord.endedAt() != null && !runMetadataRecord.endedAt().isBlank();
    }

    private String validateExecutionProfile(ExecutionProfile executionProfile) {
        if (executionProfile.type() != ExecutionProfile.ProfileType.WSL) {
            return "";
        }

        if (!hasText(executionProfile.wslDistribution())) {
            return "Enter a WSL distribution before saving the WSL execution profile.";
        }
        if (!hasText(executionProfile.windowsPathPrefix())) {
            return "Enter the Windows path prefix before saving the WSL execution profile.";
        }
        if (!hasText(executionProfile.wslPathPrefix())) {
            return "Enter the WSL path prefix before saving the WSL execution profile.";
        }

        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String displayStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        return normalizedStatus.isBlank() ? "UNKNOWN" : normalizedStatus;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSingleDirectoryName(String projectDirectoryName) {
        try {
            Path path = Path.of(projectDirectoryName).normalize();
            return !path.isAbsolute()
                    && path.getNameCount() == 1
                    && !projectDirectoryName.equals(".")
                    && !projectDirectoryName.equals("..");
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private String normalizeProjectDirectoryName(String projectDirectoryName) {
        return projectDirectoryName == null ? "" : projectDirectoryName.trim();
    }

    private String rollbackNewRepository(Path repositoryDirectory, String failureMessage) {
        try {
            deleteRecursively(repositoryDirectory);
            return failureMessage;
        } catch (IOException rollbackException) {
            return failureMessage + " Cleanup also failed for " + repositoryDirectory + ": "
                    + rollbackException.getMessage();
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException exception) {
                            throw new RecursiveDeletionException(exception);
                        }
                    });
        } catch (RecursiveDeletionException exception) {
            throw exception.ioException();
        }
    }

    public record ProjectActivationResult(boolean successful, ActiveProject activeProject, String message) {
        private static ProjectActivationResult success(ActiveProject activeProject) {
            return new ProjectActivationResult(true, activeProject, "");
        }

        private static ProjectActivationResult failure(String message) {
            return new ProjectActivationResult(false, null, message);
        }
    }

    private static final class RecursiveDeletionException extends RuntimeException {
        private final IOException ioException;

        private RecursiveDeletionException(IOException ioException) {
            this.ioException = ioException;
        }

        private IOException ioException() {
            return ioException;
        }
    }

    public record RunRecoveryCandidate(String runId, String storyId, String status, RunRecoveryAction action) {
    }

    public record SingleStorySessionAvailability(boolean startable,
                                                 String summary,
                                                 String detail,
                                                 PrdTaskRecord story,
                                                 BuiltInPreset preset) {
        private static SingleStorySessionAvailability ready(String summary,
                                                            String detail,
                                                            PrdTaskRecord story,
                                                            BuiltInPreset preset) {
            return new SingleStorySessionAvailability(true, summary, detail, story, preset);
        }

        private static SingleStorySessionAvailability unavailable(String summary, String detail) {
            return new SingleStorySessionAvailability(false, summary, detail, null, null);
        }
    }

    public record SingleStoryStartResult(boolean successful,
                                         String summary,
                                         String detail,
                                         String storyId,
                                         PrdTaskStatus finalStatus,
                                         PrdStoryAttemptRecord attempt,
                                         CodexLauncherService.CodexLaunchResult launchResult) {
        private static SingleStoryStartResult success(String storyId,
                                                      PrdTaskStatus finalStatus,
                                                      String detail,
                                                      PrdStoryAttemptRecord attempt,
                                                      CodexLauncherService.CodexLaunchResult launchResult) {
            String summary = finalStatus == PrdTaskStatus.COMPLETED
                    ? "Story passed"
                    : "Story failed";
            return new SingleStoryStartResult(true, summary, detail, storyId, finalStatus, attempt, launchResult);
        }

        private static SingleStoryStartResult failure(String summary, String detail) {
            return new SingleStoryStartResult(false, summary, detail, null, null, null, null);
        }

        private static SingleStoryStartResult failure(String summary,
                                                      String detail,
                                                      CodexLauncherService.CodexLaunchResult launchResult) {
            return new SingleStoryStartResult(false, summary, detail, null, null, null, launchResult);
        }
    }

    public record ExecutionProfileSaveResult(boolean successful, ExecutionProfile executionProfile, String message) {
        private static ExecutionProfileSaveResult success(ExecutionProfile executionProfile) {
            return new ExecutionProfileSaveResult(true, executionProfile, "");
        }

        private static ExecutionProfileSaveResult failure(String message) {
            return new ExecutionProfileSaveResult(false, null, message);
        }
    }

    public record PrdInterviewDraftSaveResult(boolean successful, PrdInterviewDraft draft, String message) {
        private static PrdInterviewDraftSaveResult success(PrdInterviewDraft draft) {
            return new PrdInterviewDraftSaveResult(true, draft, "");
        }

        private static PrdInterviewDraftSaveResult failure(String message) {
            return new PrdInterviewDraftSaveResult(false, null, message);
        }

        private static PrdInterviewDraftSaveResult failure(PrdInterviewDraft draft, String message) {
            return new PrdInterviewDraftSaveResult(false, draft, message);
        }
    }

    public record ActivePrdSaveResult(boolean successful, String markdown, Path path, String message) {
        private static ActivePrdSaveResult success(String markdown, Path path) {
            return new ActivePrdSaveResult(true, markdown, path, "");
        }

        private static ActivePrdSaveResult failure(String message) {
            return new ActivePrdSaveResult(false, null, null, message);
        }

        private static ActivePrdSaveResult failure(String markdown, Path path, String message) {
            return new ActivePrdSaveResult(false, markdown, path, message);
        }
    }

    public record PrdTaskSyncResult(boolean successful,
                                    boolean confirmationRequired,
                                    PrdTaskState taskState,
                                    PrdTaskSynchronizer.SyncPlan syncPlan,
                                    String message) {
        public boolean destructiveChangesDetected() {
            return syncPlan != null && syncPlan.destructive();
        }

        private static PrdTaskSyncResult success(PrdTaskState taskState,
                                                 PrdTaskSynchronizer.SyncPlan syncPlan,
                                                 String message) {
            return new PrdTaskSyncResult(true, false, taskState, syncPlan, message);
        }

        private static PrdTaskSyncResult confirmationRequired(PrdTaskSynchronizer.SyncPlan syncPlan, String message) {
            return new PrdTaskSyncResult(false, true, null, syncPlan, message);
        }

        private static PrdTaskSyncResult failure(String message) {
            return new PrdTaskSyncResult(false, false, null, null, message);
        }

        private static PrdTaskSyncResult failure(PrdTaskSynchronizer.SyncPlan syncPlan, String message) {
            return new PrdTaskSyncResult(false, false, null, syncPlan, message);
        }
    }

    public record PrdJsonImportResult(boolean successful,
                                      boolean conflictsDetected,
                                      boolean blockingConflictsDetected,
                                      PrdTaskState taskState,
                                      PrdTaskSynchronizer.SyncPlan markdownSyncPlan,
                                      Path activePrdJsonPath,
                                      Path importedFromPath,
                                      List<String> conflictDetails,
                                      String message) {
        public PrdJsonImportResult {
            conflictDetails = conflictDetails == null ? List.of() : List.copyOf(conflictDetails);
        }

        private static PrdJsonImportResult success(PrdTaskState taskState,
                                                   Path importedFromPath,
                                                   Path activePrdJsonPath,
                                                   PrdTaskSynchronizer.SyncPlan markdownSyncPlan,
                                                   List<String> conflictDetails,
                                                   String message) {
            return new PrdJsonImportResult(
                    true,
                    !conflictDetails.isEmpty(),
                    false,
                    taskState,
                    markdownSyncPlan,
                    activePrdJsonPath,
                    importedFromPath,
                    conflictDetails,
                    message
            );
        }

        private static PrdJsonImportResult failure(String message) {
            return new PrdJsonImportResult(false, false, false, null, null, null, null, List.of(), message);
        }

        private static PrdJsonImportResult failure(Path importedFromPath,
                                                   Path activePrdJsonPath,
                                                   String message) {
            return new PrdJsonImportResult(
                    false,
                    false,
                    false,
                    null,
                    null,
                    activePrdJsonPath,
                    importedFromPath,
                    List.of(),
                    message
            );
        }

        private static PrdJsonImportResult failure(Path importedFromPath,
                                                   Path activePrdJsonPath,
                                                   PrdTaskSynchronizer.SyncPlan markdownSyncPlan,
                                                   List<String> conflictDetails,
                                                   String message) {
            return new PrdJsonImportResult(
                    false,
                    !conflictDetails.isEmpty(),
                    markdownSyncPlan != null
                            && (!markdownSyncPlan.addedTaskIds().isEmpty() || !markdownSyncPlan.removedTaskIds().isEmpty()),
                    null,
                    markdownSyncPlan,
                    activePrdJsonPath,
                    importedFromPath,
                    conflictDetails,
                    message
            );
        }
    }

    public record MarkdownPrdImportResult(boolean successful,
                                          String markdown,
                                          Path activePrdPath,
                                          Path importedFromPath,
                                          String message) {
        private static MarkdownPrdImportResult success(String markdown, Path activePrdPath, Path importedFromPath) {
            return new MarkdownPrdImportResult(true, markdown, activePrdPath, importedFromPath, "");
        }

        private static MarkdownPrdImportResult failure(String message) {
            return new MarkdownPrdImportResult(false, null, null, null, message);
        }

        private static MarkdownPrdImportResult failure(String markdown,
                                                       Path activePrdPath,
                                                       Path importedFromPath,
                                                       String message) {
            return new MarkdownPrdImportResult(false, markdown, activePrdPath, importedFromPath, message);
        }
    }

    public record MarkdownPrdExportResult(boolean successful, Path exportedToPath, String message) {
        private static MarkdownPrdExportResult success(Path exportedToPath) {
            return new MarkdownPrdExportResult(true, exportedToPath, "");
        }

        private static MarkdownPrdExportResult failure(String message) {
            return new MarkdownPrdExportResult(false, null, message);
        }

        private static MarkdownPrdExportResult failure(Path exportedToPath, String message) {
            return new MarkdownPrdExportResult(false, exportedToPath, message);
        }
    }

    public record PrdExecutionGate(boolean executionBlocked,
                                   String summary,
                                   String detail,
                                   PrdValidationReport validationReport) {
        private static PrdExecutionGate blocked(String summary,
                                                String detail,
                                                PrdValidationReport validationReport) {
            return new PrdExecutionGate(true, summary, detail, validationReport);
        }

        private static PrdExecutionGate ready(String summary,
                                              String detail,
                                              PrdValidationReport validationReport) {
            return new PrdExecutionGate(false, summary, detail, validationReport);
        }
    }

    public record NativeWindowsPreflightRunResult(boolean successful,
                                                  NativeWindowsPreflightReport report,
                                                  String message) {
        private static NativeWindowsPreflightRunResult success(NativeWindowsPreflightReport report) {
            return new NativeWindowsPreflightRunResult(true, report, "");
        }

        private static NativeWindowsPreflightRunResult failure(String message) {
            return new NativeWindowsPreflightRunResult(false, null, message);
        }

        private static NativeWindowsPreflightRunResult failure(NativeWindowsPreflightReport report, String message) {
            return new NativeWindowsPreflightRunResult(false, report, message);
        }
    }

    public record WslPreflightRunResult(boolean successful, WslPreflightReport report, String message) {
        private static WslPreflightRunResult success(WslPreflightReport report) {
            return new WslPreflightRunResult(true, report, "");
        }

        private static WslPreflightRunResult failure(String message) {
            return new WslPreflightRunResult(false, null, message);
        }

        private static WslPreflightRunResult failure(WslPreflightReport report, String message) {
            return new WslPreflightRunResult(false, report, message);
        }
    }

    public enum RunRecoveryAction {
        RESUMABLE("Resumable"),
        REVIEWABLE("Reviewable");

        private final String label;

        RunRecoveryAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
