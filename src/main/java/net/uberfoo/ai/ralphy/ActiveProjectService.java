package net.uberfoo.ai.ralphy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ActiveProjectService {
    private final GitRepositoryInitializer gitRepositoryInitializer;
    private final LocalMetadataStorage localMetadataStorage;
    private final NativeWindowsPreflightService nativeWindowsPreflightService;
    private final ProjectMetadataInitializer projectMetadataInitializer;
    private final ProjectStorageInitializer projectStorageInitializer;
    private final WslPreflightService wslPreflightService;
    private final boolean autoRunWslPreflight;
    private ActiveProject activeProject;
    private NativeWindowsPreflightReport latestNativeWindowsPreflightReport;
    private WslPreflightReport latestWslPreflightReport;
    private PrdInterviewDraft prdInterviewDraft;
    private String startupRecoveryMessage = "";

    @Autowired
    public ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                                ProjectMetadataInitializer projectMetadataInitializer,
                                ProjectStorageInitializer projectStorageInitializer,
                                LocalMetadataStorage localMetadataStorage,
                                NativeWindowsPreflightService nativeWindowsPreflightService,
                                WslPreflightService wslPreflightService,
                                @Value("${ralphy.preflight.wsl.auto-run:true}") boolean autoRunWslPreflight) {
        this.gitRepositoryInitializer = gitRepositoryInitializer;
        this.projectMetadataInitializer = projectMetadataInitializer;
        this.projectStorageInitializer = projectStorageInitializer;
        this.localMetadataStorage = localMetadataStorage;
        this.nativeWindowsPreflightService = nativeWindowsPreflightService;
        this.wslPreflightService = wslPreflightService;
        this.autoRunWslPreflight = autoRunWslPreflight;
        this.localMetadataStorage.startSession();
        restoreLastActiveProject();
    }

    ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                         ProjectMetadataInitializer projectMetadataInitializer,
                         ProjectStorageInitializer projectStorageInitializer,
                         LocalMetadataStorage localMetadataStorage,
                         NativeWindowsPreflightService nativeWindowsPreflightService,
                         WslPreflightService wslPreflightService) {
        this(
                gitRepositoryInitializer,
                projectMetadataInitializer,
                projectStorageInitializer,
                localMetadataStorage,
                nativeWindowsPreflightService,
                wslPreflightService,
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
        if (executionProfile.type() == ExecutionProfile.ProfileType.POWERSHELL) {
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
