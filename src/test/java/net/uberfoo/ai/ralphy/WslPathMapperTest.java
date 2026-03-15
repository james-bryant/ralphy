package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WslPathMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsRepositoriesWithinTheConfiguredWindowsPrefix() {
        Path windowsWorkspaceRoot = tempDir.resolve("workspaces").toAbsolutePath().normalize();
        Path repositoryPath = windowsWorkspaceRoot.resolve("sample-repo").resolve("nested");
        ExecutionProfile executionProfile = new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                windowsWorkspaceRoot.toString(),
                "/mnt/c/workspaces"
        );

        WslPathMapper.PathMappingResult mappingResult =
                WslPathMapper.mapRepositoryPath(executionProfile, repositoryPath);

        assertTrue(mappingResult.successful());
        assertEquals("/mnt/c/workspaces/sample-repo/nested", mappingResult.wslPath());
    }

    @Test
    void rejectsRepositoriesOutsideTheConfiguredWindowsPrefix() {
        Path windowsWorkspaceRoot = tempDir.resolve("workspaces").toAbsolutePath().normalize();
        Path repositoryPath = tempDir.resolve("other-root").resolve("sample-repo").toAbsolutePath().normalize();
        ExecutionProfile executionProfile = new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                windowsWorkspaceRoot.toString(),
                "/mnt/c/workspaces"
        );

        WslPathMapper.PathMappingResult mappingResult =
                WslPathMapper.mapRepositoryPath(executionProfile, repositoryPath);

        assertFalse(mappingResult.successful());
        assertTrue(mappingResult.message().contains("outside the configured Windows path prefix"));
    }
}
