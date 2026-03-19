package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryCompletionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void validateAndCommitRunsAutomatedQualityGatesAndStoresCommitMetadata() throws IOException {
        Path repository = createGitRepository("commit-success-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor();
        StoryCompletionService service = new StoryCompletionService(
                commandExecutor,
                "git",
                HostOperatingSystem.WINDOWS
        );

        StoryCompletionService.StoryCompletionResult result = service.validateAndCommit(
                new ActiveProject(repository),
                PrdTaskRecord.created(
                        "US-034",
                        "Commit Each Completed Story",
                        "Implementation history is traceable story by story.",
                        "2026-03-16T00:00:00Z"
                ),
                List.of(
                        ".\\mvnw.cmd clean verify jacoco:report and Windows smoke verification",
                        "Manual smoke verification on Windows"
                )
        );

        assertTrue(result.successful());
        assertEquals("abc123def456", result.commitHash());
        assertEquals("US-034: Commit Each Completed Story", result.commitMessage());
        assertTrue(result.detail().contains(".\\mvnw.cmd clean verify jacoco:report"));
        assertTrue(result.detail().contains("abc123def456"));
        assertEquals(
                List.of(
                        List.of("powershell.exe", "-NoLogo", "-NoProfile", "-Command", ".\\mvnw.cmd clean verify jacoco:report"),
                        List.of("git", "add", "--all"),
                        List.of("git", "status", "--porcelain"),
                        List.of("git", "commit", "-m", "US-034: Commit Each Completed Story"),
                        List.of("git", "rev-parse", "HEAD")
                ),
                commandExecutor.commands()
        );
    }

    @Test
    void validateAndCommitStopsBeforeGitCommitWhenValidationFails() throws IOException {
        Path repository = createGitRepository("commit-failure-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor() {
            @Override
            public StoryCompletionService.CommandResult execute(Path workingDirectory, List<String> command) {
                commands().add(List.copyOf(command));
                if (command.equals(List.of(
                        "powershell.exe",
                        "-NoLogo",
                        "-NoProfile",
                        "-Command",
                        ".\\mvnw.cmd clean verify jacoco:report"
                ))) {
                    return StoryCompletionService.CommandResult.success(1, "[ERROR] Tests failed");
                }
                return StoryCompletionService.CommandResult.failure("Unexpected command");
            }
        };
        StoryCompletionService service = new StoryCompletionService(
                commandExecutor,
                "git",
                HostOperatingSystem.WINDOWS
        );

        StoryCompletionService.StoryCompletionResult result = service.validateAndCommit(
                new ActiveProject(repository),
                PrdTaskRecord.created(
                        "US-034",
                        "Commit Each Completed Story",
                        "Implementation history is traceable story by story.",
                        "2026-03-16T00:00:00Z"
                ),
                List.of(".\\mvnw.cmd clean verify jacoco:report")
        );

        assertFalse(result.successful());
        assertEquals("", result.commitHash());
        assertEquals("", result.commitMessage());
        assertTrue(result.detail().contains("Validation failed"));
        assertEquals(
                List.of(List.of(
                        "powershell.exe",
                        "-NoLogo",
                        "-NoProfile",
                        "-Command",
                        ".\\mvnw.cmd clean verify jacoco:report"
                )),
                commandExecutor.commands()
        );
    }

    @Test
    void validateAndCommitUsesPosixShellForLinuxQualityGates() throws IOException {
        Path repository = createGitRepository("linux-commit-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor() {
            @Override
            public StoryCompletionService.CommandResult execute(Path workingDirectory, List<String> command) {
                commands().add(List.copyOf(command));
                if (command.equals(List.of("/bin/sh", "-lc", "./mvnw clean verify jacoco:report"))) {
                    return StoryCompletionService.CommandResult.success(0, "BUILD SUCCESS");
                }
                if (command.equals(List.of("git", "add", "--all"))) {
                    return StoryCompletionService.CommandResult.success(0, "");
                }
                if (command.equals(List.of("git", "status", "--porcelain"))) {
                    return StoryCompletionService.CommandResult.success(0, "M src/main/java/net/uberfoo/ai/ralphy/AppShellController.java");
                }
                if (command.equals(List.of("git", "commit", "-m", "US-034: Commit Each Completed Story"))) {
                    return StoryCompletionService.CommandResult.success(0, "");
                }
                if (command.equals(List.of("git", "rev-parse", "HEAD"))) {
                    return StoryCompletionService.CommandResult.success(0, "abc123def456");
                }
                return StoryCompletionService.CommandResult.failure("Unexpected command");
            }
        };
        StoryCompletionService service = new StoryCompletionService(
                commandExecutor,
                "git",
                HostOperatingSystem.LINUX
        );

        StoryCompletionService.StoryCompletionResult result = service.validateAndCommit(
                new ActiveProject(repository),
                PrdTaskRecord.created(
                        "US-034",
                        "Commit Each Completed Story",
                        "Implementation history is traceable story by story.",
                        "2026-03-16T00:00:00Z"
                ),
                List.of(".\\mvnw.cmd clean verify jacoco:report and Linux smoke verification")
        );

        assertTrue(result.successful());
        assertEquals(List.of("/bin/sh", "-lc", "./mvnw clean verify jacoco:report"),
                commandExecutor.commands().getFirst());
    }

    private Path createGitRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private static class FakeCommandExecutor implements StoryCompletionService.CommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();

        @Override
        public StoryCompletionService.CommandResult execute(Path workingDirectory, List<String> command) {
            commands.add(List.copyOf(command));
            if (command.equals(List.of(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-Command",
                    ".\\mvnw.cmd clean verify jacoco:report"
            ))) {
                return StoryCompletionService.CommandResult.success(0, "BUILD SUCCESS");
            }
            if (command.equals(List.of("git", "add", "--all"))) {
                return StoryCompletionService.CommandResult.success(0, "");
            }
            if (command.equals(List.of("git", "status", "--porcelain"))) {
                return StoryCompletionService.CommandResult.success(0, "M src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java");
            }
            if (command.equals(List.of("git", "commit", "-m", "US-034: Commit Each Completed Story"))) {
                return StoryCompletionService.CommandResult.success(0, "[feature abc123d] US-034: Commit Each Completed Story");
            }
            if (command.equals(List.of("git", "rev-parse", "HEAD"))) {
                return StoryCompletionService.CommandResult.success(0, "abc123def456");
            }
            return StoryCompletionService.CommandResult.failure("Unexpected command");
        }

        List<List<String>> commands() {
            return commands;
        }
    }
}
