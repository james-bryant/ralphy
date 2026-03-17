package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitFeatureBranchServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void ensureBranchCreatesMissingFeatureBranch() throws IOException {
        Path repository = createGitRepository("create-branch-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor("main");
        GitFeatureBranchService service = new GitFeatureBranchService(commandExecutor);

        GitFeatureBranchService.BranchSelectionResult selectionResult =
                service.ensureBranch(new ActiveProject(repository), "ralph/task-sync-plan");

        assertTrue(selectionResult.successful());
        assertEquals("ralph/task-sync-plan", selectionResult.branchName());
        assertEquals("CREATED", selectionResult.branchAction());
        assertEquals(
                List.of(
                        List.of("git", "rev-parse", "--abbrev-ref", "HEAD"),
                        List.of("git", "show-ref", "--verify", "--quiet", "refs/heads/ralph/task-sync-plan"),
                        List.of("git", "switch", "-c", "ralph/task-sync-plan")
                ),
                commandExecutor.commands()
        );
    }

    @Test
    void ensureBranchSwitchesToExistingFeatureBranch() throws IOException {
        Path repository = createGitRepository("switch-branch-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor("main");
        commandExecutor.addExistingBranch("ralph/task-sync-plan");
        GitFeatureBranchService service = new GitFeatureBranchService(commandExecutor);

        GitFeatureBranchService.BranchSelectionResult selectionResult =
                service.ensureBranch(new ActiveProject(repository), "ralph/task-sync-plan");

        assertTrue(selectionResult.successful());
        assertEquals("SWITCHED", selectionResult.branchAction());
        assertEquals(
                List.of(
                        List.of("git", "rev-parse", "--abbrev-ref", "HEAD"),
                        List.of("git", "show-ref", "--verify", "--quiet", "refs/heads/ralph/task-sync-plan"),
                        List.of("git", "switch", "ralph/task-sync-plan")
                ),
                commandExecutor.commands()
        );
    }

    @Test
    void ensureBranchLeavesAlreadyActiveFeatureBranchInPlace() throws IOException {
        Path repository = createGitRepository("already-active-repo");
        FakeCommandExecutor commandExecutor = new FakeCommandExecutor("ralph/task-sync-plan");
        GitFeatureBranchService service = new GitFeatureBranchService(commandExecutor);

        GitFeatureBranchService.BranchSelectionResult selectionResult =
                service.ensureBranch(new ActiveProject(repository), "ralph/task-sync-plan");

        assertTrue(selectionResult.successful());
        assertEquals("ALREADY_ACTIVE", selectionResult.branchAction());
        assertEquals(List.of(List.of("git", "rev-parse", "--abbrev-ref", "HEAD")), commandExecutor.commands());
    }

    @Test
    void ensureBranchFallsBackToSymbolicRefWhenHeadIsUnborn() throws IOException {
        Path repository = createGitRepository("unborn-head-repo");
        FakeCommandExecutor commandExecutor = FakeCommandExecutor.withUnbornHead("main");
        GitFeatureBranchService service = new GitFeatureBranchService(commandExecutor);

        GitFeatureBranchService.BranchSelectionResult selectionResult =
                service.ensureBranch(new ActiveProject(repository), "ralph/task-sync-plan");

        assertTrue(selectionResult.successful());
        assertEquals("ralph/task-sync-plan", selectionResult.branchName());
        assertEquals("CREATED", selectionResult.branchAction());
        assertEquals(
                List.of(
                        List.of("git", "rev-parse", "--abbrev-ref", "HEAD"),
                        List.of("git", "symbolic-ref", "--quiet", "--short", "HEAD"),
                        List.of("git", "show-ref", "--verify", "--quiet", "refs/heads/ralph/task-sync-plan"),
                        List.of("git", "switch", "-c", "ralph/task-sync-plan")
                ),
                commandExecutor.commands()
        );
    }

    private Path createGitRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }

    private static final class FakeCommandExecutor implements GitFeatureBranchService.CommandExecutor {
        private final List<List<String>> commands = new ArrayList<>();
        private final Set<String> existingBranches = new HashSet<>();
        private final boolean unbornHead;
        private String currentBranch;

        private FakeCommandExecutor(String currentBranch) {
            this(currentBranch, false);
        }

        private FakeCommandExecutor(String currentBranch, boolean unbornHead) {
            this.currentBranch = currentBranch;
            this.unbornHead = unbornHead;
        }

        private static FakeCommandExecutor withUnbornHead(String currentBranch) {
            return new FakeCommandExecutor(currentBranch, true);
        }

        @Override
        public GitFeatureBranchService.CommandResult execute(Path workingDirectory, List<String> command) {
            commands.add(List.copyOf(command));
            if (command.equals(List.of("git", "rev-parse", "--abbrev-ref", "HEAD"))) {
                if (unbornHead) {
                    return GitFeatureBranchService.CommandResult.success(
                            128,
                            """
                            fatal: ambiguous argument 'HEAD': unknown revision or path not in the working tree.
                            Use ' -- ' to separate paths from revisions, like this:
                            'git <command> [<revision> ... ] -- [<file> ... ]'
                            HEAD
                            """
                    );
                }
                return GitFeatureBranchService.CommandResult.success(0, currentBranch);
            }
            if (command.equals(List.of("git", "symbolic-ref", "--quiet", "--short", "HEAD"))) {
                return GitFeatureBranchService.CommandResult.success(0, currentBranch);
            }
            if (command.size() == 5
                    && command.get(0).equals("git")
                    && command.get(1).equals("show-ref")
                    && command.get(4).startsWith("refs/heads/")) {
                String branchName = command.get(4).substring("refs/heads/".length());
                return GitFeatureBranchService.CommandResult.success(existingBranches.contains(branchName) ? 0 : 1, "");
            }
            if (command.size() == 4
                    && command.get(0).equals("git")
                    && command.get(1).equals("switch")
                    && command.get(2).equals("-c")) {
                String branchName = command.get(3);
                existingBranches.add(branchName);
                currentBranch = branchName;
                return GitFeatureBranchService.CommandResult.success(0, "");
            }
            if (command.size() == 3
                    && command.get(0).equals("git")
                    && command.get(1).equals("switch")) {
                String branchName = command.get(2);
                currentBranch = branchName;
                return GitFeatureBranchService.CommandResult.success(0, "");
            }
            return GitFeatureBranchService.CommandResult.failure("Unexpected command");
        }

        private void addExistingBranch(String branchName) {
            existingBranches.add(branchName);
        }

        private List<List<String>> commands() {
            return List.copyOf(commands);
        }
    }
}
