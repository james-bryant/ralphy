package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveProjectServiceTest {
    private final ActiveProjectService activeProjectService = new ActiveProjectService();

    @TempDir
    Path tempDir;

    @Test
    void openRepositoryAcceptsFoldersWithGitMetadataDirectoryOrFile() throws IOException {
        Path gitDirectoryRepository = createGitDirectoryRepository("git-directory-repo");

        ActiveProjectService.SelectionResult gitDirectoryResult = activeProjectService.openRepository(gitDirectoryRepository);

        assertTrue(gitDirectoryResult.successful());
        assertEquals(gitDirectoryRepository.toAbsolutePath().normalize(), gitDirectoryResult.activeProject().repositoryPath());

        Path gitFileRepository = Files.createDirectory(tempDir.resolve("git-file-repo"));
        Files.writeString(gitFileRepository.resolve(".git"), "gitdir: C:/tmp/worktree");

        ActiveProjectService.SelectionResult gitFileResult = activeProjectService.openRepository(gitFileRepository);

        assertTrue(gitFileResult.successful());
        assertEquals(gitFileRepository.toAbsolutePath().normalize(), gitFileResult.activeProject().repositoryPath());
    }

    @Test
    void openRepositoryRejectsNonGitFoldersWithoutClearingExistingActiveProject() throws IOException {
        Path validRepository = createGitDirectoryRepository("valid-repo");
        activeProjectService.openRepository(validRepository);

        Path plainDirectory = Files.createDirectory(tempDir.resolve("plain-folder"));

        ActiveProjectService.SelectionResult selectionResult = activeProjectService.openRepository(plainDirectory);

        assertFalse(selectionResult.successful());
        assertEquals("Selected folder is not a Git repository: " + plainDirectory.toAbsolutePath().normalize(),
                selectionResult.message());
        assertEquals(validRepository.toAbsolutePath().normalize(),
                activeProjectService.activeProject().orElseThrow().repositoryPath());
    }

    private Path createGitDirectoryRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }
}
