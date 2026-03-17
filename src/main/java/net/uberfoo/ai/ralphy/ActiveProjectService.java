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
    private static final int MAX_AUTOMATIC_ATTEMPTS_PER_STORY = 2;

    private final GitFeatureBranchService gitFeatureBranchService;
    private final GitRepositoryInitializer gitRepositoryInitializer;
    private final LocalMetadataStorage localMetadataStorage;
    private final NativeWindowsPreflightService nativeWindowsPreflightService;
    private final PresetCatalogService presetCatalogService;
    private final PrdStructureValidator prdStructureValidator;
    private final PrdTaskStateStore prdTaskStateStore;
    private final PrdTaskSynchronizer prdTaskSynchronizer;
    private final RalphPrdJsonMapper ralphPrdJsonMapper;
    private final StoryCompletionService storyCompletionService;
    private final ProjectMetadataInitializer projectMetadataInitializer;
    private final ProjectStorageInitializer projectStorageInitializer;
    private final WslPreflightService wslPreflightService;
    private final CodexLauncherService codexLauncherService;
    private final boolean autoRunNativeWindowsPreflight;
    private final boolean autoRunWslPreflight;
    private final Object storySessionLock = new Object();
    private ActiveProject activeProject;
    private NativeWindowsPreflightReport latestNativeWindowsPreflightReport;
    private WslPreflightReport latestWslPreflightReport;
    private PrdInterviewDraft prdInterviewDraft;
    private PrdPlanningSession prdPlanningSession;
    private String activePrdMarkdown;
    private PrdTaskState prdTaskState;
    private PrdTaskSyncResult lastPrdTaskSyncResult;
    private MarkdownPrdExchangeLocations markdownPrdExchangeLocations;
    private String startupRecoveryMessage = "";

    @Autowired
    public ActiveProjectService(GitFeatureBranchService gitFeatureBranchService,
                                GitRepositoryInitializer gitRepositoryInitializer,
                                ProjectMetadataInitializer projectMetadataInitializer,
                                ProjectStorageInitializer projectStorageInitializer,
                                LocalMetadataStorage localMetadataStorage,
                                NativeWindowsPreflightService nativeWindowsPreflightService,
                                PrdStructureValidator prdStructureValidator,
                                PrdTaskStateStore prdTaskStateStore,
                                PrdTaskSynchronizer prdTaskSynchronizer,
                                RalphPrdJsonMapper ralphPrdJsonMapper,
                                StoryCompletionService storyCompletionService,
                                PresetCatalogService presetCatalogService,
                                CodexLauncherService codexLauncherService,
                                WslPreflightService wslPreflightService,
                                @Value("${ralphy.preflight.native.auto-run:true}") boolean autoRunNativeWindowsPreflight,
                                @Value("${ralphy.preflight.wsl.auto-run:true}") boolean autoRunWslPreflight) {
        this.gitFeatureBranchService = gitFeatureBranchService;
        this.gitRepositoryInitializer = gitRepositoryInitializer;
        this.projectMetadataInitializer = projectMetadataInitializer;
        this.projectStorageInitializer = projectStorageInitializer;
        this.localMetadataStorage = localMetadataStorage;
        this.nativeWindowsPreflightService = nativeWindowsPreflightService;
        this.prdStructureValidator = prdStructureValidator;
        this.prdTaskStateStore = prdTaskStateStore;
        this.prdTaskSynchronizer = prdTaskSynchronizer;
        this.ralphPrdJsonMapper = ralphPrdJsonMapper;
        this.storyCompletionService = storyCompletionService;
        this.presetCatalogService = presetCatalogService;
        this.codexLauncherService = codexLauncherService;
        this.wslPreflightService = wslPreflightService;
        this.autoRunNativeWindowsPreflight = autoRunNativeWindowsPreflight;
        this.autoRunWslPreflight = autoRunWslPreflight;
        this.localMetadataStorage.startSession();
        restoreLastActiveProject();
    }

    ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                         GitFeatureBranchService gitFeatureBranchService,
                         ProjectMetadataInitializer projectMetadataInitializer,
                         ProjectStorageInitializer projectStorageInitializer,
                         LocalMetadataStorage localMetadataStorage,
                         NativeWindowsPreflightService nativeWindowsPreflightService,
                         PrdStructureValidator prdStructureValidator,
                         PrdTaskStateStore prdTaskStateStore,
                         PrdTaskSynchronizer prdTaskSynchronizer,
                         RalphPrdJsonMapper ralphPrdJsonMapper,
                         StoryCompletionService storyCompletionService,
                         PresetCatalogService presetCatalogService,
                         CodexLauncherService codexLauncherService,
                         WslPreflightService wslPreflightService) {
        this(
                gitFeatureBranchService,
                gitRepositoryInitializer,
                projectMetadataInitializer,
                projectStorageInitializer,
                localMetadataStorage,
                nativeWindowsPreflightService,
                prdStructureValidator,
                prdTaskStateStore,
                prdTaskSynchronizer,
                ralphPrdJsonMapper,
                storyCompletionService,
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

    public synchronized RunHistoryReport runHistory() {
        if (activeProject == null) {
            return RunHistoryReport.unavailable(
                    "No active project",
                    "Open a repository before reviewing prior story attempts."
            );
        }

        if (prdTaskState == null || prdTaskState.tasks().isEmpty()) {
            return RunHistoryReport.empty(
                    "No run history yet",
                    "Save a valid PRD and run a story to build persisted attempt history."
            );
        }

        Map<String, LocalMetadataStorage.RunMetadataRecord> runMetadataByRunId = new LinkedHashMap<>();
        localMetadataStorage.projectRecordForRepository(activeProject.repositoryPath())
                .ifPresent(projectRecord -> localMetadataStorage.runMetadataForProject(projectRecord.projectId())
                        .forEach(runMetadataRecord -> runMetadataByRunId.put(runMetadataRecord.runId(), runMetadataRecord)));

        List<RunHistoryEntry> entries = prdTaskState.tasks().stream()
                .flatMap(task -> task.attempts().stream()
                        .map(attempt -> toRunHistoryEntry(task, attempt, runMetadataByRunId.get(attempt.runId()))))
                .sorted(Comparator.comparing(this::runHistorySortKey).reversed()
                        .thenComparing(RunHistoryEntry::runId, Comparator.reverseOrder()))
                .toList();
        if (entries.isEmpty()) {
            return RunHistoryReport.empty(
                    "No run history yet",
                    "Start a story to build persisted attempt history for this project."
            );
        }

        long distinctStories = entries.stream()
                .map(RunHistoryEntry::storyId)
                .distinct()
                .count();
        RunHistoryEntry latestEntry = entries.getFirst();
        return RunHistoryReport.available(
                entries.size()
                        + (entries.size() == 1 ? " persisted attempt" : " persisted attempts"),
                "Across "
                        + distinctStories
                        + (distinctStories == 1 ? " story" : " stories")
                        + ". Latest: "
                        + latestEntry.storyId()
                        + " | "
                        + latestEntry.result()
                        + " | "
                        + firstNonBlank(latestEntry.endedAt(), latestEntry.startedAt(), latestEntry.queuedAt(), "No timestamp"),
                entries
        );
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

    public synchronized Optional<PrdPlanningSession> prdPlanningSession() {
        return Optional.ofNullable(prdPlanningSession);
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
                    SingleStorySessionState.BLOCKED,
                    "No active project",
                    "Open a repository before starting a single story session."
            );
        }

        BuiltInPreset preset = presetForSingleStorySession(presetUseCase);
        if (preset == null) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    "Preset unavailable",
                    "Select Story Implementation or Retry/Fix before starting a single story session."
            );
        }

        PrdExecutionGate executionGate = prdExecutionGate();
        if (executionGate.executionBlocked()) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    executionGate.summary(),
                    executionGate.detail()
            );
        }

        SingleStorySessionAvailability preflightAvailability = preflightAvailability(executionProfile()
                .orElse(ExecutionProfile.nativePowerShell()));
        if (!preflightAvailability.startable()) {
            return preflightAvailability;
        }

        if (prdTaskState == null || prdTaskState.tasks().isEmpty()) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    "No task state",
                    "Save a valid PRD so Ralphy can create queued story records before starting execution."
            );
        }

        Optional<PrdTaskRecord> runningTask = prdTaskState.tasks().stream()
                .filter(task -> task.status() == PrdTaskStatus.RUNNING)
                .findFirst();
        if (runningTask.isPresent()) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    "Story already running",
                    runningTask.get().taskId() + " is already marked RUNNING and must be reviewed before another story starts."
            );
        }

        StorySelection storySelection = selectStoryForPreset(presetUseCase);
        if (storySelection.state() == SingleStorySessionState.NO_ELIGIBLE_STORY) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.NO_ELIGIBLE_STORY,
                    presetUseCase == PresetUseCase.RETRY_FIX
                            ? "No failed story available"
                            : "No eligible story available",
                    formatNoEligibleStoryDetail(presetUseCase, storySelection.skippedStories()),
                    storySelection.skippedStories()
            );
        }
        if (storySelection.state() != SingleStorySessionState.READY) {
            return SingleStorySessionAvailability.unavailable(
                    storySelection.state(),
                    storySelection.summary(),
                    prependSkippedStoriesDetail(storySelection.detail(), storySelection.skippedStories()),
                    storySelection.skippedStories()
            );
        }

        PrdTaskRecord task = storySelection.story();
        String action = presetUseCase == PresetUseCase.RETRY_FIX ? "retry" : "play";
        return SingleStorySessionAvailability.ready(
                "Ready to " + action + " " + task.taskId(),
                prependSkippedStoriesDetail(
                        task.taskId() + ": " + task.title() + " will run with " + preset.displayName() + ".",
                        storySelection.skippedStories()
                ),
                task,
                preset,
                storySelection.skippedStories()
        );
    }

    public SingleStoryStartResult startEligibleSingleStory(PresetUseCase presetUseCase) {
        return startEligibleSingleStory(
                presetUseCase,
                ExecutionAgentSelection.codexDefault(),
                CodexLauncherService.RunOutputListener.noop()
        );
    }

    public SingleStoryStartResult startEligibleSingleStory(PresetUseCase presetUseCase,
                                                           CodexLauncherService.RunOutputListener runOutputListener) {
        return startEligibleSingleStory(
                presetUseCase,
                ExecutionAgentSelection.codexDefault(),
                runOutputListener
        );
    }

    public SingleStoryStartResult startEligibleSingleStory(PresetUseCase presetUseCase,
                                                           ExecutionAgentSelection executionAgentSelection,
                                                           CodexLauncherService.RunOutputListener runOutputListener) {
        ExecutionAgentSelection resolvedAgentSelection = executionAgentSelection == null
                ? ExecutionAgentSelection.codexDefault()
                : executionAgentSelection;
        if (!resolvedAgentSelection.provider().executionSupported()) {
            return SingleStoryStartResult.failure(
                    "Provider not supported",
                    resolvedAgentSelection.provider().displayName()
                            + " execution is not implemented yet. Switch provider to Codex."
            );
        }

        synchronized (storySessionLock) {
            SingleStorySessionAvailability availability;
            ExecutionProfile executionProfile;
            synchronized (this) {
                availability = singleStorySessionAvailability(presetUseCase);
                if (!availability.startable()) {
                    return SingleStoryStartResult.failure(availability.summary(), availability.detail());
                }
                executionProfile = executionProfile().orElse(ExecutionProfile.nativePowerShell());
            }

            String storyId = availability.story().taskId();
            SingleStoryStartResult latestResult = null;
            String initialFailureDetail = "";

            for (int attemptNumber = 1; attemptNumber <= MAX_AUTOMATIC_ATTEMPTS_PER_STORY; attemptNumber++) {
                boolean automaticRetry = attemptNumber > 1;
                latestResult = executeStoryAttempt(
                        storyId,
                        availability.preset(),
                        executionProfile,
                        resolvedAgentSelection,
                        runOutputListener,
                        automaticRetry
                );
                if (!latestResult.successful()) {
                    return latestResult;
                }
                if (latestResult.finalStatus() == PrdTaskStatus.COMPLETED) {
                    return automaticRetry
                            ? rewriteStoryAttemptResult(
                            latestResult,
                            automaticRetrySuccessDetail(initialFailureDetail, latestResult.detail())
                    )
                            : latestResult;
                }
                if (!automaticRetry) {
                    initialFailureDetail = latestResult.detail();
                }
            }

            return rewriteStoryAttemptResult(
                    latestResult,
                    automaticRetryFailureDetail(initialFailureDetail, latestResult == null ? "" : latestResult.detail())
            );
        }
    }

    private SingleStoryStartResult executeStoryAttempt(String storyId,
                                                       BuiltInPreset preset,
                                                       ExecutionProfile executionProfile,
                                                       ExecutionAgentSelection executionAgentSelection,
                                                       CodexLauncherService.RunOutputListener runOutputListener,
                                                       boolean automaticRetry) {
        ActiveProject activeProjectSnapshot;
        String activePrdMarkdownSnapshot;
        PrdTaskState workingTaskState;
        synchronized (this) {
            activeProjectSnapshot = activeProject;
            activePrdMarkdownSnapshot = activePrdMarkdown;
            workingTaskState = prdTaskState;
        }
        if (activeProjectSnapshot == null) {
            return SingleStoryStartResult.failure(
                    "No active project",
                    "Open or restore a repository before starting a story."
            );
        }
        if (workingTaskState == null) {
            return SingleStoryStartResult.failure(
                    "No synced story state",
                    "Save or sync the active PRD before starting a story."
            );
        }

        PrdTaskRecord task = workingTaskState.taskById(storyId)
                .orElseThrow(() -> new IllegalStateException("Missing story state for " + storyId + "."));
        List<String> qualityGates = List.copyOf(workingTaskState.qualityGates());

        CodexLauncherService.CodexLaunchPlan launchPlan;
        GitFeatureBranchService.BranchSelectionResult branchSelection = gitFeatureBranchService.ensureBranch(
                activeProjectSnapshot,
                ralphPrdJsonMapper.branchName(activeProjectSnapshot, activePrdMarkdownSnapshot)
        );
        if (!branchSelection.successful()) {
            return SingleStoryStartResult.failure(
                    "Unable to prepare feature branch",
                    branchSelection.message()
            );
        }
        try {
            launchPlan = codexLauncherService.buildLaunch(
                    buildLaunchRequest(
                            task,
                            preset,
                            executionProfile,
                            executionAgentSelection,
                            branchSelection,
                            activeProjectSnapshot,
                            workingTaskState
                    )
            );
        } catch (IllegalArgumentException exception) {
            return SingleStoryStartResult.failure(
                    "Unable to start story",
                    "The story launch could not be prepared: " + exception.getMessage()
            );
        }

        try {
            String queuedAt = Instant.now().toString();
            PrdTaskRecord queuedTask = task.queueAttempt(
                    launchPlan.runId(),
                    preset,
                    queuedAt,
                    queueMessage(preset, storyId, automaticRetry)
            );
            workingTaskState = workingTaskState.replaceTask(queuedTask, queuedAt);
            persistPrdTaskState(activeProjectSnapshot, workingTaskState);

            String startedAt = Instant.now().toString();
            PrdTaskRecord runningTask = workingTaskState.taskById(storyId)
                    .orElseThrow()
                    .startAttempt(
                            launchPlan.runId(),
                            startedAt,
                            startMessage(preset, storyId, automaticRetry)
                    );
            workingTaskState = workingTaskState.replaceTask(runningTask, startedAt);
            persistPrdTaskState(activeProjectSnapshot, workingTaskState);
        } catch (IOException exception) {
            return SingleStoryStartResult.failure(
                "Unable to persist story state",
                    "The queued or running story state could not be stored: " + exception.getMessage()
            );
        }

        CodexLauncherService.CodexLaunchResult launchResult = null;
        StoryCompletionService.StoryCompletionResult storyCompletionResult = null;
        PrdTaskStatus finalStatus = PrdTaskStatus.FAILED;
        String finalMessage;
        String finishedAt = Instant.now().toString();
        try {
            launchResult = codexLauncherService.launch(launchPlan, runOutputListener);
            finishedAt = launchResult.endedAt();
            if (launchResult.successful()) {
                storyCompletionResult = storyCompletionService.validateAndCommit(
                        activeProjectSnapshot,
                        task,
                        qualityGates
                );
                finalStatus = storyCompletionResult.successful() ? PrdTaskStatus.COMPLETED : PrdTaskStatus.FAILED;
                finalMessage = storyCompletionResult.detail();
                finishedAt = Instant.now().toString();
            } else {
                finalStatus = PrdTaskStatus.FAILED;
                finalMessage = attemptCompletionMessage(launchResult, automaticRetry);
            }
        } catch (RuntimeException exception) {
            finalMessage = automaticRetry
                    ? "Automatic retry launch failed: " + exception.getMessage()
                    : "Story validation or launch failed: " + exception.getMessage();
        }

        try {
            PrdTaskRecord finalizedTask = workingTaskState.taskById(storyId)
                    .orElseThrow()
                    .finishAttempt(
                            launchPlan.runId(),
                            finalStatus,
                            finishedAt,
                            finalMessage,
                            storyCompletionResult == null ? "" : storyCompletionResult.commitHash(),
                            storyCompletionResult == null ? "" : storyCompletionResult.commitMessage()
                    );
            workingTaskState = workingTaskState.replaceTask(finalizedTask, finalizedTask.updatedAt());
            persistPrdTaskState(activeProjectSnapshot, workingTaskState);
            return SingleStoryStartResult.success(
                    storyId,
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
                        SingleStorySessionState.BLOCKED,
                        "WSL preflight not run",
                        "Run WSL preflight before starting a WSL story session."
                );
            }
            if (!latestWslPreflightReport.passed()) {
                return SingleStorySessionAvailability.unavailable(
                        SingleStorySessionState.BLOCKED,
                        "WSL preflight blocked",
                        "WSL execution stays blocked until every WSL preflight check passes."
                );
            }
            return SingleStorySessionAvailability.ready("", "", null, null, List.of());
        }

        if (latestNativeWindowsPreflightReport == null) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    "Native preflight not run",
                    "Run native preflight before starting a PowerShell story session."
            );
        }
        if (!latestNativeWindowsPreflightReport.passed()) {
            return SingleStorySessionAvailability.unavailable(
                    SingleStorySessionState.BLOCKED,
                    "Native preflight blocked",
                    "Native PowerShell execution stays blocked until every native preflight check passes."
            );
        }
        return SingleStorySessionAvailability.ready("", "", null, null, List.of());
    }

    private BuiltInPreset presetForSingleStorySession(PresetUseCase presetUseCase) {
        if (presetUseCase != PresetUseCase.STORY_IMPLEMENTATION && presetUseCase != PresetUseCase.RETRY_FIX) {
            return null;
        }
        return presetCatalogService.defaultPreset(presetUseCase);
    }

    private StorySelection selectStoryForPreset(PresetUseCase presetUseCase) {
        if (prdTaskState == null) {
            return StorySelection.none(List.of());
        }

        List<StorySkip> skippedStories = new ArrayList<>();
        for (PrdTaskRecord task : prdTaskState.tasks()) {
            StorySkip invalidStorySkip = invalidStorySkip(task);
            if (invalidStorySkip != null) {
                skippedStories.add(invalidStorySkip);
                continue;
            }

            if (presetUseCase == PresetUseCase.RETRY_FIX) {
                if (task.status() == PrdTaskStatus.FAILED) {
                    return StorySelection.eligible(task, skippedStories);
                }
                continue;
            }

            if (presetUseCase != PresetUseCase.STORY_IMPLEMENTATION) {
                return StorySelection.none(skippedStories);
            }

            switch (task.status()) {
                case COMPLETED -> {
                    // Already done; continue scanning for the next actionable story.
                }
                case BLOCKED -> skippedStories.add(new StorySkip(task.taskId(), task.title(), "status is BLOCKED"));
                case READY -> {
                    return StorySelection.eligible(task, skippedStories);
                }
                case FAILED -> {
                    return StorySelection.reviewRequired(task, skippedStories);
                }
                case RUNNING -> {
                    return StorySelection.blocked(
                            "Story already running",
                            task.taskId() + " is already marked RUNNING and must be reviewed before another story starts.",
                            skippedStories
                    );
                }
            }
        }

        return StorySelection.none(skippedStories);
    }

    private StorySkip invalidStorySkip(PrdTaskRecord task) {
        if (task == null || task.status() == PrdTaskStatus.COMPLETED) {
            return null;
        }
        if (!hasText(task.taskId())) {
            return new StorySkip("(missing story ID)", "", "story ID is missing");
        }
        if (!hasText(task.title())) {
            return new StorySkip(task.taskId(), "", "story title is missing");
        }
        if (!hasText(task.outcome())) {
            return new StorySkip(task.taskId(), task.title(), "story outcome is missing");
        }
        return null;
    }

    private String formatNoEligibleStoryDetail(PresetUseCase presetUseCase, List<StorySkip> skippedStories) {
        if (presetUseCase == PresetUseCase.RETRY_FIX) {
            return "Select a story with a failed attempt before running Retry/Fix.";
        }
        if (skippedStories == null || skippedStories.isEmpty()) {
            return "All synced stories are either passed, blocked, or already running.";
        }
        return prependSkippedStoriesDetail(
                "No additional eligible stories remain.",
                skippedStories
        );
    }

    private String prependSkippedStoriesDetail(String detail, List<StorySkip> skippedStories) {
        if (skippedStories == null || skippedStories.isEmpty()) {
            return detail;
        }

        List<String> skippedDetails = new ArrayList<>(skippedStories.size());
        for (StorySkip skippedStory : skippedStories) {
            skippedDetails.add(skippedStory.taskId() + " (" + skippedStory.reason() + ")");
        }
        String skippedPrefix = "Blocked or invalid stories will be skipped: " + String.join(", ", skippedDetails) + ".";
        if (!hasText(detail)) {
            return skippedPrefix;
        }
        return skippedPrefix + " " + detail;
    }

    private SingleStoryStartResult rewriteStoryAttemptResult(SingleStoryStartResult result, String detail) {
        if (result == null) {
            return SingleStoryStartResult.failure("Story failed", detail);
        }
        return new SingleStoryStartResult(
                result.successful(),
                result.summary(),
                detail,
                result.storyId(),
                result.finalStatus(),
                result.attempt(),
                result.launchResult()
        );
    }

    private String queueMessage(BuiltInPreset preset, String storyId, boolean automaticRetry) {
        if (automaticRetry) {
            return "Queued automatic retry with " + preset.displayName() + " for " + storyId + ".";
        }
        return "Queued " + preset.displayName() + " for " + storyId + ".";
    }

    private String startMessage(BuiltInPreset preset, String storyId, boolean automaticRetry) {
        if (automaticRetry) {
            return "Started automatic retry with " + preset.displayName() + " for " + storyId + ".";
        }
        return "Started " + preset.displayName() + " for " + storyId + ".";
    }

    private String attemptCompletionMessage(CodexLauncherService.CodexLaunchResult launchResult, boolean automaticRetry) {
        if (launchResult.successful()) {
            return automaticRetry ? "Automatic retry passed." : "Story attempt passed.";
        }

        String launchMessage = firstNonBlank(launchResult.message(), "Story attempt failed.");
        if (automaticRetry) {
            return "Automatic retry failed: " + launchMessage;
        }
        return launchMessage;
    }

    private String automaticRetrySuccessDetail(String firstFailureDetail, String finalDetail) {
        return "Automatic retry passed after an initial failure. "
                + "First attempt: " + firstNonBlank(firstFailureDetail, "Story attempt failed.") + " "
                + "Final attempt: " + firstNonBlank(finalDetail, "Automatic retry passed.");
    }

    private String automaticRetryFailureDetail(String firstFailureDetail, String finalDetail) {
        return "Automatic retry failed and the story remains resumable. "
                + "First attempt: " + firstNonBlank(firstFailureDetail, "Story attempt failed.") + " "
                + "Final attempt: " + firstNonBlank(finalDetail, "Automatic retry failed.");
    }

    private CodexLauncherService.CodexLaunchRequest buildLaunchRequest(PrdTaskRecord task,
                                                                       BuiltInPreset preset,
                                                                       ExecutionProfile executionProfile,
                                                                       ExecutionAgentSelection executionAgentSelection,
                                                                       GitFeatureBranchService.BranchSelectionResult branchSelection,
                                                                       ActiveProject activeProject,
                                                                       PrdTaskState taskState) {
        List<CodexLauncherService.PromptInput> promptInputs = new ArrayList<>();
        promptInputs.add(new CodexLauncherService.PromptInput("Story", task.taskId() + ": " + task.title()));
        promptInputs.add(new CodexLauncherService.PromptInput("Outcome", task.outcome()));
        if (taskState != null && !taskState.qualityGates().isEmpty()) {
            promptInputs.add(new CodexLauncherService.PromptInput(
                    "Quality Gates",
                    String.join(System.lineSeparator(), taskState.qualityGates())
            ));
        }

        List<String> codexOptions = new ArrayList<>();
        codexOptions.add("--json");
        if (executionAgentSelection != null
                && executionAgentSelection.provider() == ExecutionAgentProvider.CODEX
                && hasText(executionAgentSelection.modelId())) {
            codexOptions.add("--model");
            codexOptions.add(executionAgentSelection.modelId());
        }

        return new CodexLauncherService.CodexLaunchRequest(
                task.taskId(),
                activeProject,
                executionProfile,
                preset,
                promptInputs,
                "",
                codexOptions,
                branchSelection.branchName(),
                branchSelection.branchAction()
        );
    }

    private synchronized void persistPrdTaskState(ActiveProject targetProject, PrdTaskState replacementState)
            throws IOException {
        Objects.requireNonNull(replacementState, "replacementState must not be null");
        Objects.requireNonNull(targetProject, "targetProject must not be null");
        prdTaskStateStore.write(targetProject, replacementState);
        if (activeProject != null && activeProject.repositoryPath().equals(targetProject.repositoryPath())) {
            prdTaskState = replacementState;
        }
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

    public synchronized PrdPlanningSessionSaveResult savePrdPlanningSession(PrdPlanningSession replacementSession) {
        Objects.requireNonNull(replacementSession, "replacementSession must not be null");
        if (activeProject == null) {
            return PrdPlanningSessionSaveResult.failure(
                    "Open or create a repository before saving a PRD planning session."
            );
        }

        try {
            projectMetadataInitializer.writePrdPlanningSession(activeProject, replacementSession);
            prdPlanningSession = replacementSession;
            return PrdPlanningSessionSaveResult.success(replacementSession);
        } catch (IOException exception) {
            return PrdPlanningSessionSaveResult.failure(
                    replacementSession,
                    "Unable to store the PRD planning session: " + exception.getMessage()
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
        refreshPrdPlanningSession();
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

    private void refreshPrdPlanningSession() {
        prdPlanningSession = readStoredPrdPlanningSession().orElse(null);
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

    private Optional<PrdPlanningSession> readStoredPrdPlanningSession() {
        if (activeProject == null) {
            return Optional.empty();
        }

        try {
            return projectMetadataInitializer.readPrdPlanningSession(activeProject);
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

    private RunHistoryEntry toRunHistoryEntry(PrdTaskRecord task,
                                              PrdStoryAttemptRecord attempt,
                                              LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        return new RunHistoryEntry(
                task.taskId(),
                task.title(),
                attempt.runId(),
                attempt.queuedAt(),
                attempt.startedAt(),
                attempt.endedAt(),
                runMetadataRecord == null ? "" : runMetadataRecord.branchName(),
                runMetadataRecord == null ? "" : runMetadataRecord.branchAction(),
                attempt.presetId(),
                attempt.presetName(),
                attempt.presetVersion(),
                runHistoryResult(attempt, runMetadataRecord),
                attempt.detail(),
                attempt.commitHash(),
                attempt.commitMessage(),
                runMetadataRecord == null
                        ? LocalMetadataStorage.RunArtifactPaths.empty()
                        : runMetadataRecord.artifactPaths()
        );
    }

    private String runHistorySortKey(RunHistoryEntry entry) {
        return latestTimestamp(entry.queuedAt(), entry.startedAt(), entry.endedAt());
    }

    private String runHistoryResult(PrdStoryAttemptRecord attempt,
                                    LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        if (runMetadataRecord != null && hasText(runMetadataRecord.status())) {
            return switch (normalizeStatus(runMetadataRecord.status())) {
                case "SUCCEEDED", "PASSED", "COMPLETED" -> "PASSED";
                case "FAILED" -> "FAILED";
                case "RUNNING" -> "RUNNING";
                default -> displayStatus(runMetadataRecord.status());
            };
        }

        return switch (attempt.outcome()) {
            case READY -> "QUEUED";
            case RUNNING -> "RUNNING";
            case BLOCKED -> "BLOCKED";
            case COMPLETED -> "PASSED";
            case FAILED -> "FAILED";
        };
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

    public record RunHistoryReport(boolean available,
                                   String summary,
                                   String detail,
                                   List<RunHistoryEntry> entries) {
        public RunHistoryReport {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        private static RunHistoryReport unavailable(String summary, String detail) {
            return new RunHistoryReport(false, summary, detail, List.of());
        }

        private static RunHistoryReport empty(String summary, String detail) {
            return new RunHistoryReport(true, summary, detail, List.of());
        }

        private static RunHistoryReport available(String summary, String detail, List<RunHistoryEntry> entries) {
            return new RunHistoryReport(true, summary, detail, entries);
        }
    }

    public record RunHistoryEntry(String storyId,
                                  String storyTitle,
                                  String runId,
                                  String queuedAt,
                                  String startedAt,
                                  String endedAt,
                                  String branchName,
                                  String branchAction,
                                  String presetId,
                                  String presetName,
                                  String presetVersion,
                                  String result,
                                  String detail,
                                  String commitHash,
                                  String commitMessage,
                                  LocalMetadataStorage.RunArtifactPaths artifactPaths) {
        public RunHistoryEntry {
            storyTitle = storyTitle == null ? "" : storyTitle.trim();
            queuedAt = queuedAt == null ? "" : queuedAt.trim();
            startedAt = startedAt == null ? "" : startedAt.trim();
            endedAt = endedAt == null ? "" : endedAt.trim();
            branchName = branchName == null ? "" : branchName.trim();
            branchAction = branchAction == null ? "" : branchAction.trim();
            presetId = presetId == null ? "" : presetId.trim();
            presetName = presetName == null ? "" : presetName.trim();
            presetVersion = presetVersion == null ? "" : presetVersion.trim();
            result = result == null ? "" : result.trim();
            detail = detail == null ? "" : detail.trim();
            commitHash = commitHash == null ? "" : commitHash.trim();
            commitMessage = commitMessage == null ? "" : commitMessage.trim();
            artifactPaths = artifactPaths == null ? LocalMetadataStorage.RunArtifactPaths.empty() : artifactPaths;
        }
    }

    public record SingleStorySessionAvailability(boolean startable,
                                                 SingleStorySessionState state,
                                                 String summary,
                                                 String detail,
                                                 PrdTaskRecord story,
                                                 BuiltInPreset preset,
                                                 List<StorySkip> skippedStories) {
        public SingleStorySessionAvailability {
            skippedStories = skippedStories == null ? List.of() : List.copyOf(skippedStories);
        }

        private static SingleStorySessionAvailability ready(String summary,
                                                            String detail,
                                                            PrdTaskRecord story,
                                                            BuiltInPreset preset,
                                                            List<StorySkip> skippedStories) {
            return new SingleStorySessionAvailability(
                    true,
                    SingleStorySessionState.READY,
                    summary,
                    detail,
                    story,
                    preset,
                    skippedStories
            );
        }

        private static SingleStorySessionAvailability unavailable(SingleStorySessionState state,
                                                                  String summary,
                                                                  String detail) {
            return unavailable(state, summary, detail, List.of());
        }

        private static SingleStorySessionAvailability unavailable(SingleStorySessionState state,
                                                                  String summary,
                                                                  String detail,
                                                                  List<StorySkip> skippedStories) {
            return new SingleStorySessionAvailability(false, state, summary, detail, null, null, skippedStories);
        }
    }

    public record StorySkip(String taskId, String title, String reason) {
    }

    private record StorySelection(SingleStorySessionState state,
                                  PrdTaskRecord story,
                                  List<StorySkip> skippedStories,
                                  String summary,
                                  String detail) {
        private StorySelection {
            skippedStories = skippedStories == null ? List.of() : List.copyOf(skippedStories);
        }

        private static StorySelection eligible(PrdTaskRecord story, List<StorySkip> skippedStories) {
            return new StorySelection(SingleStorySessionState.READY, story, skippedStories, "", "");
        }

        private static StorySelection none(List<StorySkip> skippedStories) {
            return new StorySelection(SingleStorySessionState.NO_ELIGIBLE_STORY, null, skippedStories, "", "");
        }

        private static StorySelection blocked(String summary, String detail, List<StorySkip> skippedStories) {
            return new StorySelection(SingleStorySessionState.BLOCKED, null, skippedStories, summary, detail);
        }

        private static StorySelection reviewRequired(PrdTaskRecord story, List<StorySkip> skippedStories) {
            return new StorySelection(
                    SingleStorySessionState.REVIEW_REQUIRED,
                    null,
                    skippedStories,
                    "Execution needs review",
                    story.taskId() + " failed and must be retried before Play can continue."
            );
        }
    }

    public enum SingleStorySessionState {
        READY,
        BLOCKED,
        NO_ELIGIBLE_STORY,
        REVIEW_REQUIRED
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

    public record PrdPlanningSessionSaveResult(boolean successful, PrdPlanningSession session, String message) {
        private static PrdPlanningSessionSaveResult success(PrdPlanningSession session) {
            return new PrdPlanningSessionSaveResult(true, session, "");
        }

        private static PrdPlanningSessionSaveResult failure(String message) {
            return new PrdPlanningSessionSaveResult(false, null, message);
        }

        private static PrdPlanningSessionSaveResult failure(PrdPlanningSession session, String message) {
            return new PrdPlanningSessionSaveResult(false, session, message);
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
