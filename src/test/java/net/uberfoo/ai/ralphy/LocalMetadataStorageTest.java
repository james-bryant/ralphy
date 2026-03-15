package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMetadataStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void persistedRecordsRemainReadableAfterRestart() throws IOException {
        Path storageDirectory = tempDir.resolve("local-storage");
        Path repositoryPath = Files.createDirectory(tempDir.resolve("starter-repo"));

        LocalMetadataStorage firstStorage = LocalMetadataStorage.forTest(storageDirectory);
        firstStorage.startSession();
        firstStorage.recordProjectActivation(new ActiveProject(repositoryPath));
        firstStorage.finishSession();

        LocalMetadataStorage.LocalMetadataSnapshot restartedSnapshot =
                LocalMetadataStorage.forTest(storageDirectory).snapshot();

        assertEquals(1, restartedSnapshot.projects().size());
        assertEquals(1, restartedSnapshot.sessions().size());
        assertEquals(1, restartedSnapshot.profiles().size());
        assertTrue(restartedSnapshot.runMetadata().isEmpty());

        LocalMetadataStorage.ProjectRecord projectRecord = restartedSnapshot.projects().getFirst();
        LocalMetadataStorage.SessionRecord sessionRecord = restartedSnapshot.sessions().getFirst();
        LocalMetadataStorage.ProfileRecord profileRecord = restartedSnapshot.profiles().getFirst();
        ActiveProject activeProject = new ActiveProject(repositoryPath);

        assertEquals(2, restartedSnapshot.schemaVersion());
        assertEquals(repositoryPath.toAbsolutePath().normalize().toString(), projectRecord.repositoryPath());
        assertEquals(activeProject.activePrdPath().toString(), projectRecord.storagePaths().activePrdPath());
        assertEquals(activeProject.prdJsonDirectoryPath().toString(), projectRecord.storagePaths().prdJsonDirectoryPath());
        assertEquals(projectRecord.projectId(), sessionRecord.activeProjectId());
        assertEquals(activeProject.promptsDirectoryPath().toString(), sessionRecord.storagePaths().promptsDirectoryPath());
        assertEquals(activeProject.logsDirectoryPath().toString(), sessionRecord.storagePaths().logsDirectoryPath());
        assertEquals(projectRecord.projectId(), profileRecord.projectId());
        assertEquals("UNCONFIGURED", profileRecord.profileType());
        assertEquals("CLOSED", sessionRecord.status());
    }

    @Test
    void versionOneMetadataStoreIsMigratedWithStoragePaths() throws IOException {
        Path storageDirectory = tempDir.resolve("legacy-storage");
        Files.createDirectories(storageDirectory);
        Path repositoryPath = Files.createDirectory(tempDir.resolve("legacy-repo"));
        Path storageFile = storageDirectory.resolve("metadata-store.json");
        String escapedRepositoryPath = escapeJson(repositoryPath.toAbsolutePath().normalize().toString());
        String escapedProjectMetadataPath = escapeJson(
                repositoryPath.resolve(".ralph-tui").resolve("project-metadata.json").toAbsolutePath().normalize().toString()
        );

        Files.writeString(storageFile, """
                {
                  "schemaVersion": 1,
                  "projects": [
                    {
                      "projectId": "project-1",
                      "displayName": "legacy-repo",
                      "repositoryPath": "%s",
                      "projectMetadataPath": "%s",
                      "createdAt": "2026-03-15T00:00:00Z",
                      "lastOpenedAt": "2026-03-15T00:00:00Z"
                    }
                  ],
                  "sessions": [
                    {
                      "sessionId": "session-1",
                      "status": "OPEN",
                      "activeProjectId": "project-1",
                      "startedAt": "2026-03-15T00:00:00Z",
                      "updatedAt": "2026-03-15T00:00:00Z",
                      "endedAt": null
                    }
                  ],
                  "profiles": [],
                  "runMetadata": []
                }
                """.formatted(
                escapedRepositoryPath,
                escapedProjectMetadataPath
        ), StandardCharsets.UTF_8);

        LocalMetadataStorage.LocalMetadataSnapshot migratedSnapshot =
                LocalMetadataStorage.forTest(storageDirectory).snapshot();
        ActiveProject activeProject = new ActiveProject(repositoryPath);

        assertEquals(2, migratedSnapshot.schemaVersion());
        assertEquals(activeProject.artifactsDirectoryPath().toString(),
                migratedSnapshot.projects().getFirst().storagePaths().artifactsDirectoryPath());
        assertEquals(activeProject.logsDirectoryPath().toString(),
                migratedSnapshot.sessions().getFirst().storagePaths().logsDirectoryPath());
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }
}
