package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        assertEquals(6, restartedSnapshot.schemaVersion());
        assertEquals(repositoryPath.toAbsolutePath().normalize().toString(), projectRecord.repositoryPath());
        assertEquals(activeProject.activePrdPath().toString(), projectRecord.storagePaths().activePrdPath());
        assertEquals(activeProject.prdJsonDirectoryPath().toString(), projectRecord.storagePaths().prdJsonDirectoryPath());
        assertEquals(projectRecord.projectId(), sessionRecord.activeProjectId());
        assertEquals(activeProject.promptsDirectoryPath().toString(), sessionRecord.storagePaths().promptsDirectoryPath());
        assertEquals(activeProject.logsDirectoryPath().toString(), sessionRecord.storagePaths().logsDirectoryPath());
        assertEquals(projectRecord.projectId(), profileRecord.projectId());
        assertEquals("POWERSHELL", profileRecord.profileType());
        assertNull(profileRecord.wslDistribution());
        assertNull(profileRecord.windowsPathPrefix());
        assertNull(profileRecord.wslPathPrefix());
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

        assertEquals(6, migratedSnapshot.schemaVersion());
        assertEquals(activeProject.artifactsDirectoryPath().toString(),
                migratedSnapshot.projects().getFirst().storagePaths().artifactsDirectoryPath());
        assertEquals(activeProject.logsDirectoryPath().toString(),
                migratedSnapshot.sessions().getFirst().storagePaths().logsDirectoryPath());
        assertEquals(1, migratedSnapshot.profiles().size());
        assertEquals("POWERSHELL", migratedSnapshot.profiles().getFirst().profileType());
    }

    @Test
    void versionTwoMetadataStoreMigratesLegacyUnconfiguredProfilesToPowerShell() throws IOException {
        Path storageDirectory = tempDir.resolve("schema-two-storage");
        Files.createDirectories(storageDirectory);
        Path repositoryPath = Files.createDirectory(tempDir.resolve("schema-two-repo"));
        Path storageFile = storageDirectory.resolve("metadata-store.json");
        String escapedRepositoryPath = escapeJson(repositoryPath.toAbsolutePath().normalize().toString());
        String escapedProjectMetadataPath = escapeJson(
                repositoryPath.resolve(".ralph-tui").resolve("project-metadata.json").toAbsolutePath().normalize().toString()
        );

        Files.writeString(storageFile, """
                {
                  "schemaVersion": 2,
                  "projects": [
                    {
                      "projectId": "project-1",
                      "displayName": "schema-two-repo",
                      "repositoryPath": "%s",
                      "projectMetadataPath": "%s",
                      "storagePaths": {
                        "ralphyDirectoryPath": "%s",
                        "prdsDirectoryPath": "%s",
                        "activePrdPath": "%s",
                        "prdJsonDirectoryPath": "%s",
                        "activePrdJsonPath": "%s",
                        "promptsDirectoryPath": "%s",
                        "logsDirectoryPath": "%s",
                        "artifactsDirectoryPath": "%s"
                      },
                      "createdAt": "2026-03-15T00:00:00Z",
                      "lastOpenedAt": "2026-03-15T00:00:00Z"
                    }
                  ],
                  "sessions": [],
                  "profiles": [
                    {
                      "profileId": "profile-1",
                      "projectId": "project-1",
                      "profileType": "UNCONFIGURED",
                      "wslDistribution": "Ubuntu",
                      "windowsPathPrefix": "C:\\\\Users\\\\james",
                      "wslPathPrefix": "/mnt/c/Users/james",
                      "createdAt": "2026-03-15T00:00:00Z",
                      "updatedAt": "2026-03-15T00:00:00Z"
                    }
                  ],
                  "runMetadata": []
                }
                """.formatted(
                escapedRepositoryPath,
                escapedProjectMetadataPath,
                escapeJson(repositoryPath.resolve(".ralph-tui").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prds").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md")
                        .toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prd-json").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prd-json").resolve("prd.json")
                        .toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prompts").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("logs").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("artifacts").toAbsolutePath().normalize().toString())
        ), StandardCharsets.UTF_8);

        LocalMetadataStorage.ProfileRecord migratedProfile =
                LocalMetadataStorage.forTest(storageDirectory).snapshot().profiles().getFirst();

        assertEquals("POWERSHELL", migratedProfile.profileType());
        assertNull(migratedProfile.wslDistribution());
        assertNull(migratedProfile.windowsPathPrefix());
        assertNull(migratedProfile.wslPathPrefix());
    }

    @Test
    void versionThreeMetadataStoreMigratesRunMetadataWithoutArtifactPaths() throws IOException {
        Path storageDirectory = tempDir.resolve("schema-three-storage");
        Files.createDirectories(storageDirectory);
        Path repositoryPath = Files.createDirectory(tempDir.resolve("schema-three-repo"));
        Path storageFile = storageDirectory.resolve("metadata-store.json");
        String escapedRepositoryPath = escapeJson(repositoryPath.toAbsolutePath().normalize().toString());
        String escapedProjectMetadataPath = escapeJson(
                repositoryPath.resolve(".ralph-tui").resolve("project-metadata.json").toAbsolutePath().normalize().toString()
        );

        Files.writeString(storageFile, """
                {
                  "schemaVersion": 3,
                  "projects": [
                    {
                      "projectId": "project-1",
                      "displayName": "schema-three-repo",
                      "repositoryPath": "%s",
                      "projectMetadataPath": "%s",
                      "storagePaths": {
                        "ralphyDirectoryPath": "%s",
                        "prdsDirectoryPath": "%s",
                        "activePrdPath": "%s",
                        "prdJsonDirectoryPath": "%s",
                        "activePrdJsonPath": "%s",
                        "promptsDirectoryPath": "%s",
                        "logsDirectoryPath": "%s",
                        "artifactsDirectoryPath": "%s"
                      },
                      "createdAt": "2026-03-15T00:00:00Z",
                      "lastOpenedAt": "2026-03-15T00:00:00Z"
                    }
                  ],
                  "sessions": [],
                  "profiles": [],
                  "runMetadata": [
                    {
                      "runId": "run-legacy-1",
                      "projectId": "project-1",
                      "storyId": "US-025",
                      "status": "SUCCEEDED",
                      "startedAt": "2026-03-15T20:00:00Z",
                      "endedAt": "2026-03-15T20:05:00Z",
                      "profileType": "POWERSHELL",
                      "workingDirectory": "%s",
                      "processId": 1234,
                      "exitCode": 0,
                      "command": ["powershell.exe", "-NoLogo", "-NoProfile", "-Command", "& 'codex' 'exec' '-'"]
                    }
                  ]
                }
                """.formatted(
                escapedRepositoryPath,
                escapedProjectMetadataPath,
                escapeJson(repositoryPath.resolve(".ralph-tui").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prds").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prds").resolve("active-prd.md")
                        .toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prd-json").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prd-json").resolve("prd.json")
                        .toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("prompts").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("logs").toAbsolutePath().normalize().toString()),
                escapeJson(repositoryPath.resolve(".ralph-tui").resolve("artifacts").toAbsolutePath().normalize().toString()),
                escapedRepositoryPath
        ), StandardCharsets.UTF_8);

        LocalMetadataStorage.LocalMetadataSnapshot migratedSnapshot =
                LocalMetadataStorage.forTest(storageDirectory).snapshot();

        assertEquals(6, migratedSnapshot.schemaVersion());
        assertEquals(new LocalMetadataStorage.RunArtifactPaths(null, null, null, null, null, null),
                migratedSnapshot.runMetadata().getFirst().artifactPaths());
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }
}
