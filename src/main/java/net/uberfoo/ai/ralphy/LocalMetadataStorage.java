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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class LocalMetadataStorage {
    private static final int SCHEMA_VERSION = 1;
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
                existingProject == null ? timestamp : existingProject.createdAt(),
                timestamp
        );

        state = new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                upsertProjectRecord(state.projects(), projectRecord),
                updateCurrentSession(state.sessions(), projectId, timestamp, false),
                ensureProfileRecord(state.profiles(), projectId, timestamp),
                state.runMetadata()
        );
        persistState();
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
                updateCurrentSession(state.sessions(), currentActiveProjectId(), timestamp, true),
                state.profiles(),
                state.runMetadata()
        );
        persistState();
        currentSessionId = null;
    }

    private Optional<ProjectRecord> findProjectByRepositoryPath(Path repositoryPath) {
        String normalizedRepositoryPath = repositoryPath.toAbsolutePath().normalize().toString();
        return state.projects().stream()
                .filter(projectRecord -> projectRecord.repositoryPath().equals(normalizedRepositoryPath))
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

        if (loadedState.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Unsupported local metadata schema version: " + loadedState.schemaVersion()
            );
        }

        return new LocalMetadataSnapshot(
                SCHEMA_VERSION,
                List.copyOf(loadedState.projects() == null ? List.of() : loadedState.projects()),
                List.copyOf(loadedState.sessions() == null ? List.of() : loadedState.sessions()),
                List.copyOf(loadedState.profiles() == null ? List.of() : loadedState.profiles()),
                List.copyOf(loadedState.runMetadata() == null ? List.of() : loadedState.runMetadata())
        );
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
                                String createdAt,
                                String lastOpenedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionRecord(String sessionId,
                                String status,
                                String activeProjectId,
                                String startedAt,
                                String updatedAt,
                                String endedAt) {
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
