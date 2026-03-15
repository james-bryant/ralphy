package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

        assertEquals(1, restartedSnapshot.schemaVersion());
        assertEquals(1, restartedSnapshot.projects().size());
        assertEquals(1, restartedSnapshot.sessions().size());
        assertEquals(1, restartedSnapshot.profiles().size());
        assertTrue(restartedSnapshot.runMetadata().isEmpty());

        LocalMetadataStorage.ProjectRecord projectRecord = restartedSnapshot.projects().getFirst();
        LocalMetadataStorage.SessionRecord sessionRecord = restartedSnapshot.sessions().getFirst();
        LocalMetadataStorage.ProfileRecord profileRecord = restartedSnapshot.profiles().getFirst();

        assertEquals(repositoryPath.toAbsolutePath().normalize().toString(), projectRecord.repositoryPath());
        assertEquals(projectRecord.projectId(), sessionRecord.activeProjectId());
        assertEquals(projectRecord.projectId(), profileRecord.projectId());
        assertEquals("UNCONFIGURED", profileRecord.profileType());
        assertEquals("CLOSED", sessionRecord.status());
    }
}
