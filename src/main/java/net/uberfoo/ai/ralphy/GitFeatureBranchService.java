package net.uberfoo.ai.ralphy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Component
public class GitFeatureBranchService {
    private final CommandExecutor commandExecutor;
    private final String gitCommand;

    @Autowired
    public GitFeatureBranchService(@Value("${ralphy.git.command:git}") String gitCommand) {
        this(new SystemCommandExecutor(), gitCommand);
    }

    GitFeatureBranchService(CommandExecutor commandExecutor) {
        this(commandExecutor, "git");
    }

    GitFeatureBranchService(CommandExecutor commandExecutor, String gitCommand) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
        this.gitCommand = Objects.requireNonNull(gitCommand, "gitCommand must not be null");
    }

    public BranchSelectionResult ensureBranch(ActiveProject activeProject, String branchName) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        if (branchName == null || branchName.isBlank()) {
            return BranchSelectionResult.failure("Feature branch name must not be blank.");
        }

        String normalizedBranchName = branchName.trim();
        CurrentBranchResolution currentBranchResolution = resolveCurrentBranch(activeProject.repositoryPath());
        if (!currentBranchResolution.successful()) {
            return BranchSelectionResult.failure(
                    "Unable to determine the current Git branch: " + currentBranchResolution.message()
            );
        }

        String currentBranch = currentBranchResolution.branchName();
        if (normalizedBranchName.equals(currentBranch)) {
            return BranchSelectionResult.success(normalizedBranchName, "ALREADY_ACTIVE");
        }

        CommandResult branchLookupResult = commandExecutor.execute(
                activeProject.repositoryPath(),
                List.of(gitCommand, "show-ref", "--verify", "--quiet", "refs/heads/" + normalizedBranchName)
        );
        if (hasText(branchLookupResult.failureMessage())
                || (branchLookupResult.exitCode() != null && branchLookupResult.exitCode() != 0
                && branchLookupResult.exitCode() != 1)) {
            return BranchSelectionResult.failure(
                    "Unable to inspect feature branch `" + normalizedBranchName + "`: "
                            + commandMessage(branchLookupResult)
            );
        }

        boolean branchExists = branchLookupResult.exitCode() != null && branchLookupResult.exitCode() == 0;
        CommandResult switchResult = commandExecutor.execute(
                activeProject.repositoryPath(),
                branchExists
                        ? List.of(gitCommand, "switch", normalizedBranchName)
                        : List.of(gitCommand, "switch", "-c", normalizedBranchName)
        );
        if (!switchResult.successful()) {
            return BranchSelectionResult.failure(
                    (branchExists
                            ? "Unable to switch to feature branch `"
                            : "Unable to create feature branch `")
                            + normalizedBranchName
                            + "`: "
                            + commandMessage(switchResult)
            );
        }

        return BranchSelectionResult.success(normalizedBranchName, branchExists ? "SWITCHED" : "CREATED");
    }

    private CurrentBranchResolution resolveCurrentBranch(Path repositoryPath) {
        CommandResult revParseResult = commandExecutor.execute(
                repositoryPath,
                List.of(gitCommand, "rev-parse", "--abbrev-ref", "HEAD")
        );
        if (revParseResult.successful()) {
            return CurrentBranchResolution.success(normalizeOptionalValue(revParseResult.output()));
        }

        CommandResult symbolicRefResult = commandExecutor.execute(
                repositoryPath,
                List.of(gitCommand, "symbolic-ref", "--quiet", "--short", "HEAD")
        );
        if (symbolicRefResult.successful()) {
            return CurrentBranchResolution.success(normalizeOptionalValue(symbolicRefResult.output()));
        }

        return CurrentBranchResolution.failure(commandMessage(revParseResult));
    }

    private String commandMessage(CommandResult commandResult) {
        if (hasText(commandResult.failureMessage())) {
            return commandResult.failureMessage().trim();
        }
        if (hasText(commandResult.output())) {
            return commandResult.output().trim();
        }
        if (commandResult.exitCode() != null) {
            return "Git exited with code " + commandResult.exitCode() + ".";
        }
        return "Git command failed.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeOptionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    interface CommandExecutor {
        CommandResult execute(Path workingDirectory, List<String> command);
    }

    record CommandResult(Integer exitCode, String output, String failureMessage) {
        static CommandResult success(int exitCode, String output) {
            return new CommandResult(exitCode, output == null ? "" : output, "");
        }

        static CommandResult failure(String failureMessage) {
            return new CommandResult(null, "", failureMessage == null ? "" : failureMessage);
        }

        boolean successful() {
            return !hasText(failureMessage) && exitCode != null && exitCode == 0;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record BranchSelectionResult(boolean successful, String branchName, String branchAction, String message) {
        private static BranchSelectionResult success(String branchName, String branchAction) {
            return new BranchSelectionResult(true, branchName, branchAction, "");
        }

        private static BranchSelectionResult failure(String message) {
            return new BranchSelectionResult(false, null, null, message);
        }
    }

    private record CurrentBranchResolution(boolean successful, String branchName, String message) {
        private static CurrentBranchResolution success(String branchName) {
            return new CurrentBranchResolution(true, branchName, "");
        }

        private static CurrentBranchResolution failure(String message) {
            return new CurrentBranchResolution(false, null, message);
        }
    }

    private static final class SystemCommandExecutor implements CommandExecutor {
        @Override
        public CommandResult execute(Path workingDirectory, List<String> command) {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = process.waitFor();
                return CommandResult.success(exitCode, output);
            } catch (IOException exception) {
                return CommandResult.failure(exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return CommandResult.failure("Git command was interrupted.");
            }
        }
    }
}
