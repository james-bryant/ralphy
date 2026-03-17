package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalMetadataStorage {
    private static final int SCHEMA_VERSION = 6;
    private static final String STORAGE_FILE_NAME = "metadata-store.json";
    private static final String DEFAULT_PROFILE_TYPE = ExecutionProfile.ProfileType.POWERSHELL.storageValue();

    private final ObjectMapper objectMapper;
    private final Path storageFilePath;
    private LocalMetadataSnapshot state;
    private String currentSessionId;

    @Autowired
    public LocalMetadataStorage(Environment environment) {
        this(resolveStorageDirectory(environment.getProperty("ralphy.storage.directory")));
    }

    static LocalMetadataStorage forTest(Path storageDirectory) {
        return new LocalMetadataStorage(storageDirectory);
    }

    private LocalMetadataStorage(Path storageDirectory) {
        objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        storageFilePath = storageDirectory.toAbsolutePath().normalize().resolve(STORAGE_FILE_NAME);
        state = loadState();
    }

    public synchronized void startSession() {
        if (currentSessionId != null) {
            return;
        }

        String timestamp = Instant.now().toString();
        currentSessionId = UUID.randomUUID().toString();
        SessionRecord sessionRecord = new SessionRecord(
                currentSessionId,
                "OPEN",
                null,
                null,
                timestamp,
                timestamp,
                null
        );
        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                state.projects(),
                appendSession(state.sessions(), sessionRecord),
                state.profiles(),
                state.runMetadata()
        );
        persistState();
    }

    public synchronized void recordProjectActivation(ActiveProject activeProject) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        startSession();

        String timestamp = Instant.now().toString();
        ProjectRecord existingProject = findProjectByRepositoryPath(activeProject.repositoryPath()).orElse(null);
        String projectId = existingProject == null ? UUID.randomUUID().toString() : existingProject.projectId();
        ProjectRecord projectRecord = new ProjectRecord(
                projectId,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingProject == null ? timestamp : existingProject.createdAt(),
                timestamp
        );

        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                upsertProjectRecord(state.projects(), projectRecord),
                updateCurrentSession(
                        state.sessions(),
                        projectId,
                        SessionStoragePaths.from(activeProject),
                        timestamp,
                        false
                ),
                ensureProfileRecord(state.profiles(), projectId, timestamp),
                state.runMetadata()
        );
        persistState();
    }

    public synchronized Optional<ProjectRecord> lastActiveProjectRecord() {
        for (int index = state.sessions().size() - 1; index >= 0; index--) {
            SessionRecord sessionRecord = state.sessions().get(index);
            if (!isPopulated(sessionRecord.activeProjectId())) {
                continue;
            }

            Optional<ProjectRecord> projectRecord = findProjectById(sessionRecord.activeProjectId());
            if (projectRecord.isPresent()) {
                return projectRecord;
            }
        }

        return Optional.empty();
    }

    public synchronized Optional<ProjectRecord> projectRecordForRepository(Path repositoryPath) {
        return findProjectByRepositoryPath(repositoryPath);
    }

    public synchronized Optional<ExecutionProfile> executionProfileForRepository(Path repositoryPath) {
        Optional<ProjectRecord> projectRecord = findProjectByRepositoryPath(repositoryPath);
        if (projectRecord.isEmpty()) {
            return Optional.empty();
        }

        return findProfileByProjectId(projectRecord.get().projectId())
                .map(this::toExecutionProfile)
                .or(() -> Optional.of(ExecutionProfile.nativePowerShell()));
    }

    public synchronized ExecutionProfile saveExecutionProfile(String projectId, ExecutionProfile executionProfile) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");

        String timestamp = Instant.now().toString();
        ProfileRecord existingProfileRecord = findProfileByProjectId(projectId).orElse(null);
        ProfileRecord replacementRecord = new ProfileRecord(
                existingProfileRecord == null ? UUID.randomUUID().toString() : existingProfileRecord.profileId(),
                projectId,
                executionProfile.type().storageValue(),
                executionProfile.wslDistribution(),
                executionProfile.windowsPathPrefix(),
                executionProfile.wslPathPrefix(),
                existingProfileRecord == null ? timestamp : existingProfileRecord.createdAt(),
                timestamp
        );

        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                state.projects(),
                state.sessions(),
                upsertProfileRecord(state.profiles(), replacementRecord),
                state.runMetadata()
        );
        persistState();
        return executionProfile;
    }

    public synchronized Optional<RunMetadataRecord> latestRunMetadataForProject(String projectId) {
        if (!isPopulated(projectId)) {
            return Optional.empty();
        }

        return state.runMetadata().stream()
                .filter(runMetadataRecord -> projectId.equals(runMetadataRecord.projectId()))
                .max(Comparator.comparing(this::runSortKey));
    }

    public synchronized List<RunMetadataRecord> runMetadataForProject(String projectId) {
        if (!isPopulated(projectId)) {
            return List.of();
        }

        return state.runMetadata().stream()
                .filter(runMetadataRecord -> projectId.equals(runMetadataRecord.projectId()))
                .sorted(Comparator.comparing(this::runSortKey).reversed()
                        .thenComparing(RunMetadataRecord::runId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public synchronized RunMetadataRecord saveRunMetadata(RunMetadataRecord runMetadataRecord) {
        Objects.requireNonNull(runMetadataRecord, "runMetadataRecord must not be null");

        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                state.projects(),
                state.sessions(),
                state.profiles(),
                upsertRunMetadataRecord(state.runMetadata(), runMetadataRecord)
        );
        persistState();
        return runMetadataRecord;
    }

    public synchronized LocalMetadataSnapshot snapshot() {
        return state;
    }

    public synchronized Path storageFilePath() {
        return storageFilePath;
    }

    public synchronized void finishSession() {
        if (currentSessionId == null || state == null) {
            return;
        }

        String timestamp = Instant.now().toString();
        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                state.projects(),
                updateCurrentSession(state.sessions(), currentActiveProjectId(), null, timestamp, true),
                state.profiles(),
                state.runMetadata()
        );
        persistState();
        currentSessionId = null;
    }

    synchronized void replaceRunMetadataForTest(List<RunMetadataRecord> runMetadata) {
        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                state.projects(),
                state.sessions(),
                state.profiles(),
                List.copyOf(Objects.requireNonNull(runMetadata, "runMetadata must not be null"))
        );
        persistState();
    }

    private Optional<ProjectRecord> findProjectByRepositoryPath(Path repositoryPath) {
        String normalizedRepositoryPath = repositoryPath.toAbsolutePath().normalize().toString();
        return state.projects().stream()
                .filter(projectRecord -> projectRecord.repositoryPath().equals(normalizedRepositoryPath))
                .findFirst();
    }

    private Optional<ProjectRecord> findProjectById(String projectId) {
        return state.projects().stream()
                .filter(projectRecord -> projectRecord.projectId().equals(projectId))
                .findFirst();
    }

    private Optional<ProfileRecord> findProfileByProjectId(String projectId) {
        return state.profiles().stream()
                .filter(profileRecord -> profileRecord.projectId().equals(projectId))
                .findFirst();
    }

    private String currentActiveProjectId() {
        if (currentSessionId == null || state == null || state.sessions() == null) {
            return null;
        }

        return state.sessions().stream()
                .filter(sessionRecord -> sessionRecord.sessionId().equals(currentSessionId))
                .map(SessionRecord::activeProjectId)
                .findFirst()
                .orElse(null);
    }

    private List<ProjectRecord> upsertProjectRecord(List<ProjectRecord> projectRecords, ProjectRecord replacementRecord) {
        List<ProjectRecord> updatedProjectRecords = new ArrayList<>(projectRecords.size() + 1);
        boolean replaced = false;
        for (ProjectRecord projectRecord : projectRecords) {
            if (projectRecord.projectId().equals(replacementRecord.projectId())) {
                updatedProjectRecords.add(replacementRecord);
                replaced = true;
                continue;
            }

            updatedProjectRecords.add(projectRecord);
        }

        if (!replaced) {
            updatedProjectRecords.add(replacementRecord);
        }

        return List.copyOf(updatedProjectRecords);
    }

    private List<SessionRecord> appendSession(List<SessionRecord> sessionRecords, SessionRecord sessionRecord) {
        List<SessionRecord> updatedSessionRecords = new ArrayList<>(sessionRecords.size() + 1);
        updatedSessionRecords.addAll(sessionRecords);
        updatedSessionRecords.add(sessionRecord);
        return List.copyOf(updatedSessionRecords);
    }

    private List<SessionRecord> updateCurrentSession(List<SessionRecord> sessionRecords,
                                                     String activeProjectId,
                                                     SessionStoragePaths sessionStoragePaths,
                                                     String timestamp,
                                                     boolean closingSession) {
        if (sessionRecords == null) {
            return List.of();
        }

        List<SessionRecord> updatedSessionRecords = new ArrayList<>(sessionRecords.size());
        for (SessionRecord sessionRecord : sessionRecords) {
            if (!sessionRecord.sessionId().equals(currentSessionId)) {
                updatedSessionRecords.add(sessionRecord);
                continue;
            }

            updatedSessionRecords.add(new SessionRecord(
                    sessionRecord.sessionId(),
                    closingSession ? "CLOSED" : sessionRecord.status(),
                    activeProjectId,
                    sessionStoragePaths == null ? sessionRecord.storagePaths() : sessionStoragePaths,
                    sessionRecord.startedAt(),
                    timestamp,
                    closingSession ? timestamp : sessionRecord.endedAt()
            ));
        }

        return List.copyOf(updatedSessionRecords);
    }

    private List<ProfileRecord> ensureProfileRecord(List<ProfileRecord> profileRecords,
                                                    String projectId,
                                                    String timestamp) {
        boolean alreadyTracked = profileRecords.stream()
                .anyMatch(profileRecord -> profileRecord.projectId().equals(projectId));
        if (alreadyTracked) {
            return profileRecords;
        }

        List<ProfileRecord> updatedProfileRecords = new ArrayList<>(profileRecords.size() + 1);
        updatedProfileRecords.addAll(profileRecords);
        updatedProfileRecords.add(defaultProfileRecord(projectId, timestamp));
        return List.copyOf(updatedProfileRecords);
    }

    private List<ProfileRecord> upsertProfileRecord(List<ProfileRecord> profileRecords, ProfileRecord replacementRecord) {
        List<ProfileRecord> updatedProfileRecords = new ArrayList<>(profileRecords.size() + 1);
        boolean replaced = false;
        for (ProfileRecord profileRecord : profileRecords) {
            if (profileRecord.projectId().equals(replacementRecord.projectId())) {
                updatedProfileRecords.add(replacementRecord);
                replaced = true;
                continue;
            }

            updatedProfileRecords.add(profileRecord);
        }

        if (!replaced) {
            updatedProfileRecords.add(replacementRecord);
        }

        return List.copyOf(updatedProfileRecords);
    }

    private List<RunMetadataRecord> upsertRunMetadataRecord(List<RunMetadataRecord> runMetadataRecords,
                                                            RunMetadataRecord replacementRecord) {
        List<RunMetadataRecord> updatedRunMetadataRecords = new ArrayList<>(runMetadataRecords.size() + 1);
        boolean replaced = false;
        for (RunMetadataRecord runMetadataRecord : runMetadataRecords) {
            if (runMetadataRecord.runId().equals(replacementRecord.runId())) {
                updatedRunMetadataRecords.add(replacementRecord);
                replaced = true;
                continue;
            }

            updatedRunMetadataRecords.add(runMetadataRecord);
        }

        if (!replaced) {
            updatedRunMetadataRecords.add(replacementRecord);
        }

        return List.copyOf(updatedRunMetadataRecords);
    }

    private LocalMetadataSnapshot loadState() {
        if (!Files.exists(storageFilePath)) {
            return emptyState();
        }

        try {
            LocalMetadataSnapshot loadedState =
                    objectMapper.readValue(storageFilePath.toFile(), LocalMetadataSnapshot.class);
            return normalizeState(loadedState);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read local metadata store at " + storageFilePath, exception);
        }
    }

    private LocalMetadataSnapshot normalizeState(LocalMetadataSnapshot loadedState) {
        if (loadedState == null) {
            return emptyState();
        }

        if (loadedState.schemaVersion() < 1 || loadedState.schemaVersion() > SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unsupported local metadata schema version: " + loadedState.schemaVersion()
            );
        }

        List<ProjectRecord> normalizedProjects = List.copyOf((loadedState.projects() == null ? List.<ProjectRecord>of()
                : loadedState.projects()).stream().map(this::normalizeProjectRecord).toList());
        List<SessionRecord> normalizedSessions = List.copyOf((loadedState.sessions() == null ? List.<SessionRecord>of()
                : loadedState.sessions()).stream()
                .map(sessionRecord -> normalizeSessionRecord(sessionRecord, normalizedProjects))
                .toList());
        List<ProfileRecord> normalizedProfiles = normalizeProfileRecords(loadedState.profiles(), normalizedProjects);
        List<RunMetadataRecord> normalizedRunMetadata = List.copyOf((loadedState.runMetadata() == null
                ? List.<RunMetadataRecord>of()
                : loadedState.runMetadata()).stream().map(this::normalizeRunMetadataRecord).toList());

        return new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                normalizedProjects,
                normalizedSessions,
                normalizedProfiles,
                normalizedRunMetadata
        );
    }

    private ProjectRecord normalizeProjectRecord(ProjectRecord projectRecord) {
        ActiveProject activeProject = new ActiveProject(Path.of(projectRecord.repositoryPath()));
        ActiveProject.ProjectStoragePaths storagePaths = hasCompleteProjectStoragePaths(projectRecord.storagePaths())
                ? projectRecord.storagePaths()
                : activeProject.storagePaths();

        return new ProjectRecord(
                projectRecord.projectId(),
                projectRecord.displayName(),
                projectRecord.repositoryPath(),
                projectRecord.projectMetadataPath(),
                storagePaths,
                projectRecord.createdAt(),
                projectRecord.lastOpenedAt()
        );
    }

    private SessionRecord normalizeSessionRecord(SessionRecord sessionRecord, List<ProjectRecord> projectRecords) {
        SessionStoragePaths sessionStoragePaths = sessionRecord.storagePaths();
        if (!hasCompleteSessionStoragePaths(sessionStoragePaths)) {
            sessionStoragePaths = projectRecords.stream()
                    .filter(projectRecord -> projectRecord.projectId().equals(sessionRecord.activeProjectId()))
                    .findFirst()
                    .map(ProjectRecord::storagePaths)
                    .map(SessionStoragePaths::from)
                    .orElse(sessionStoragePaths);
        }

        return new SessionRecord(
                sessionRecord.sessionId(),
                sessionRecord.status(),
                sessionRecord.activeProjectId(),
                sessionStoragePaths,
                sessionRecord.startedAt(),
                sessionRecord.updatedAt(),
                sessionRecord.endedAt()
        );
    }

    private List<ProfileRecord> normalizeProfileRecords(List<ProfileRecord> profileRecords,
                                                        List<ProjectRecord> projectRecords) {
        Set<String> knownProjectIds = new LinkedHashSet<>();
        for (ProjectRecord projectRecord : projectRecords) {
            knownProjectIds.add(projectRecord.projectId());
        }

        List<ProfileRecord> normalizedProfiles = new ArrayList<>();
        Set<String> seenProjectIds = new LinkedHashSet<>();
        if (profileRecords != null) {
            for (ProfileRecord profileRecord : profileRecords) {
                if (!isPopulated(profileRecord.projectId())
                        || !knownProjectIds.contains(profileRecord.projectId())
                        || !seenProjectIds.add(profileRecord.projectId())) {
                    continue;
                }

                normalizedProfiles.add(normalizeProfileRecord(profileRecord));
            }
        }

        for (ProjectRecord projectRecord : projectRecords) {
            if (seenProjectIds.add(projectRecord.projectId())) {
                normalizedProfiles.add(defaultProfileRecord(projectRecord.projectId(), defaultProfileTimestamp(projectRecord)));
            }
        }

        return List.copyOf(normalizedProfiles);
    }

    private ProfileRecord normalizeProfileRecord(ProfileRecord profileRecord) {
        ExecutionProfile executionProfile = toExecutionProfile(profileRecord);
        String createdAt = firstPopulated(profileRecord.createdAt(), profileRecord.updatedAt(), Instant.now().toString());
        String updatedAt = firstPopulated(profileRecord.updatedAt(), createdAt);
        return new ProfileRecord(
                isPopulated(profileRecord.profileId()) ? profileRecord.profileId() : UUID.randomUUID().toString(),
                profileRecord.projectId(),
                executionProfile.type().storageValue(),
                executionProfile.wslDistribution(),
                executionProfile.windowsPathPrefix(),
                executionProfile.wslPathPrefix(),
                createdAt,
                updatedAt
        );
    }

    private RunMetadataRecord normalizeRunMetadataRecord(RunMetadataRecord runMetadataRecord) {
        return new RunMetadataRecord(
                runMetadataRecord.runId(),
                runMetadataRecord.projectId(),
                runMetadataRecord.storyId(),
                runMetadataRecord.status(),
                runMetadataRecord.startedAt(),
                runMetadataRecord.endedAt(),
                runMetadataRecord.profileType(),
                runMetadataRecord.workingDirectory(),
                runMetadataRecord.processId(),
                runMetadataRecord.exitCode(),
                runMetadataRecord.command(),
                runMetadataRecord.branchName(),
                runMetadataRecord.branchAction(),
                runMetadataRecord.artifactPaths()
        );
    }

    private ProfileRecord defaultProfileRecord(String projectId, String timestamp) {
        return new ProfileRecord(
                UUID.randomUUID().toString(),
                projectId,
                DEFAULT_PROFILE_TYPE,
                null,
                null,
                null,
                timestamp,
                timestamp
        );
    }

    private String defaultProfileTimestamp(ProjectRecord projectRecord) {
        return firstPopulated(projectRecord.lastOpenedAt(), projectRecord.createdAt(), Instant.now().toString());
    }

    private String firstPopulated(String... values) {
        for (String value : values) {
            if (isPopulated(value)) {
                return value;
            }
        }

        return "";
    }

    private ExecutionProfile toExecutionProfile(ProfileRecord profileRecord) {
        return new ExecutionProfile(
                ExecutionProfile.ProfileType.fromStorageValue(profileRecord.profileType()),
                profileRecord.wslDistribution(),
                profileRecord.windowsPathPrefix(),
                profileRecord.wslPathPrefix()
        );
    }

    private boolean hasCompleteProjectStoragePaths(ActiveProject.ProjectStoragePaths storagePaths) {
        return storagePaths != null
                && isPopulated(storagePaths.ralphyDirectoryPath())
                && isPopulated(storagePaths.prdsDirectoryPath())
                && isPopulated(storagePaths.activePrdPath())
                && isPopulated(storagePaths.prdJsonDirectoryPath())
                && isPopulated(storagePaths.activePrdJsonPath())
                && isPopulated(storagePaths.promptsDirectoryPath())
                && isPopulated(storagePaths.logsDirectoryPath())
                && isPopulated(storagePaths.artifactsDirectoryPath());
    }

    private boolean hasCompleteSessionStoragePaths(SessionStoragePaths storagePaths) {
        return storagePaths != null
                && isPopulated(storagePaths.promptsDirectoryPath())
                && isPopulated(storagePaths.logsDirectoryPath())
                && isPopulated(storagePaths.artifactsDirectoryPath());
    }

    private boolean isPopulated(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeOptionalValue(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String runSortKey(RunMetadataRecord runMetadataRecord) {
        if (isPopulated(runMetadataRecord.endedAt())) {
            return runMetadataRecord.endedAt();
        }

        if (isPopulated(runMetadataRecord.startedAt())) {
            return runMetadataRecord.startedAt();
        }

        return "";
    }

    private LocalMetadataSnapshot emptyState() {
        return new LocalMetadataSnapshot(SCHEMA_VERSION, List.of(), List.of(), List.of(), List.of());
    }

    private void persistState() {
        try {
            Path parentDirectory = storageFilePath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            Path temporaryFilePath = storageFilePath.resolveSibling(storageFilePath.getFileName() + ".tmp");
            objectMapper.writeValue(temporaryFilePath.toFile(), state);
            moveIntoPlace(temporaryFilePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist local metadata store at " + storageFilePath, exception);
        }
    }

    private void moveIntoPlace(Path temporaryFilePath) throws IOException {
        try {
            Files.move(
                    temporaryFilePath,
                    storageFilePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFilePath, storageFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path resolveStorageDirectory(String configuredStorageDirectory) {
        if (configuredStorageDirectory != null && !configuredStorageDirectory.isBlank()) {
            return Path.of(configuredStorageDirectory);
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Ralphy");
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome, ".ralphy");
        }

        return Path.of(".ralphy");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalMetadataSnapshot(int schemaVersion,
                                        List<ProjectRecord> projects,
                                        List<SessionRecord> sessions,
                                        List<ProfileRecord> profiles,
                                        List<RunMetadataRecord> runMetadata) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectRecord(String projectId,
                                String displayName,
                                String repositoryPath,
                                String projectMetadataPath,
                                ActiveProject.ProjectStoragePaths storagePaths,
                                String createdAt,
                                String lastOpenedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionRecord(String sessionId,
                                String status,
                                String activeProjectId,
                                SessionStoragePaths storagePaths,
                                String startedAt,
                                String updatedAt,
                                String endedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionStoragePaths(String promptsDirectoryPath,
                                      String logsDirectoryPath,
                                      String artifactsDirectoryPath) {
        private static SessionStoragePaths from(ActiveProject activeProject) {
            return new SessionStoragePaths(
                    activeProject.promptsDirectoryPath().toString(),
                    activeProject.logsDirectoryPath().toString(),
                    activeProject.artifactsDirectoryPath().toString()
            );
        }

        private static SessionStoragePaths from(ActiveProject.ProjectStoragePaths projectStoragePaths) {
            return new SessionStoragePaths(
                    projectStoragePaths.promptsDirectoryPath(),
                    projectStoragePaths.logsDirectoryPath(),
                    projectStoragePaths.artifactsDirectoryPath()
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileRecord(String profileId,
                                String projectId,
                                String profileType,
                                String wslDistribution,
                                String windowsPathPrefix,
                                String wslPathPrefix,
                                String createdAt,
                                String updatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunArtifactPaths(String promptPath,
                                   String stdoutPath,
                                   String stderrPath,
                                   String structuredEventsPath,
                                   String summaryPath,
                                   String assistantSummaryPath) {
        public RunArtifactPaths {
            promptPath = normalizeOptionalValue(promptPath);
            stdoutPath = normalizeOptionalValue(stdoutPath);
            stderrPath = normalizeOptionalValue(stderrPath);
            structuredEventsPath = normalizeOptionalValue(structuredEventsPath);
            summaryPath = normalizeOptionalValue(summaryPath);
            assistantSummaryPath = normalizeOptionalValue(assistantSummaryPath);
        }

        public static RunArtifactPaths empty() {
            return new RunArtifactPaths(null, null, null, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunMetadataRecord(String runId,
                                    String projectId,
                                    String storyId,
                                    String status,
                                    String startedAt,
                                    String endedAt,
                                    String profileType,
                                    String workingDirectory,
                                    Long processId,
                                    Integer exitCode,
                                    List<String> command,
                                    String branchName,
                                    String branchAction,
                                    RunArtifactPaths artifactPaths) {
        public RunMetadataRecord {
            command = List.copyOf(command == null ? List.of() : command);
            branchName = normalizeOptionalValue(branchName);
            branchAction = normalizeOptionalValue(branchAction);
            artifactPaths = artifactPaths == null ? RunArtifactPaths.empty() : artifactPaths;
        }

        public RunMetadataRecord(String runId,
                                String projectId,
                                 String storyId,
                                 String status,
                                 String startedAt,
                                 String endedAt,
                                 String profileType,
                                String workingDirectory,
                                Long processId,
                                Integer exitCode,
                                List<String> command) {
            this(runId,
                    projectId,
                    storyId,
                    status,
                    startedAt,
                    endedAt,
                    profileType,
                    workingDirectory,
                    processId,
                    exitCode,
                    command,
                    null,
                    null,
                    RunArtifactPaths.empty());
        }

        public RunMetadataRecord(String runId,
                                 String projectId,
                                 String storyId,
                                 String status,
                                 String startedAt,
                                 String endedAt,
                                 String profileType,
                                 String workingDirectory,
                                 Long processId,
                                 Integer exitCode,
                                 List<String> command,
                                 RunArtifactPaths artifactPaths) {
            this(runId,
                    projectId,
                    storyId,
                    status,
                    startedAt,
                    endedAt,
                    profileType,
                    workingDirectory,
                    processId,
                    exitCode,
                    command,
                    null,
                    null,
                    artifactPaths);
        }

        public RunMetadataRecord(String runId,
                                 String projectId,
                                 String storyId,
                                 String status,
                                 String startedAt,
                                 String endedAt) {
            this(runId, projectId, storyId, status, startedAt, endedAt, null, null, null, null, List.of(),
                    null, null,
                    RunArtifactPaths.empty());
        }
    }
}
