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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class LocalMetadataStorage {
    private static final int SCHEMA_VERSION = 2;
    private static final String STORAGE_FILE_NAME = "metadata-store.json";
    private static final String DEFAULT_PROFILE_TYPE = "UNCONFIGURED";

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

    public synchronized Optional<RunMetadataRecord> latestRunMetadataForProject(String projectId) {
        if (!isPopulated(projectId)) {
            return Optional.empty();
        }

        return state.runMetadata().stream()
                .filter(runMetadataRecord -> projectId.equals(runMetadataRecord.projectId()))
                .max(Comparator.comparing(this::runSortKey));
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
        updatedProfileRecords.add(new ProfileRecord(
                UUID.randomUUID().toString(),
                projectId,
                DEFAULT_PROFILE_TYPE,
                null,
                null,
                null,
                timestamp,
                timestamp
        ));
        return List.copyOf(updatedProfileRecords);
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

        return new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                normalizedProjects,
                normalizedSessions,
                List.copyOf(loadedState.profiles() == null ? List.of() : loadedState.profiles()),
                List.copyOf(loadedState.runMetadata() == null ? List.of() : loadedState.runMetadata())
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
    public record RunMetadataRecord(String runId,
                                    String projectId,
                                    String storyId,
                                    String status,
                                    String startedAt,
                                    String endedAt) {
    }
}
