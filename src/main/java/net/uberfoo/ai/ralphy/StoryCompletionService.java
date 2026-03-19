package net.uberfoo.ai.ralphy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class StoryCompletionService {
    private final CommandExecutor commandExecutor;
    private final String gitCommand;
    private final HostOperatingSystem hostOperatingSystem;

    @Autowired
    public StoryCompletionService(@Value("${ralphy.git.command:git}") String gitCommand) {
        this(new SystemCommandExecutor(), gitCommand, HostOperatingSystem.detectRuntime());
    }

    StoryCompletionService(CommandExecutor commandExecutor) {
        this(commandExecutor, "git", HostOperatingSystem.detectRuntime());
    }

    StoryCompletionService(CommandExecutor commandExecutor, String gitCommand) {
        this(commandExecutor, gitCommand, HostOperatingSystem.detectRuntime());
    }

    StoryCompletionService(CommandExecutor commandExecutor,
                           String gitCommand,
                           HostOperatingSystem hostOperatingSystem) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
        this.gitCommand = Objects.requireNonNull(gitCommand, "gitCommand must not be null");
        this.hostOperatingSystem = hostOperatingSystem == null ? HostOperatingSystem.detectRuntime() : hostOperatingSystem;
    }

    public StoryCompletionResult validateAndCommit(ActiveProject activeProject,
                                                   PrdTaskRecord story,
                                                   List<String> qualityGates) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(story, "story must not be null");

        Path repositoryPath = activeProject.repositoryPath();
        List<String> automatedQualityGates = automatedQualityGates(qualityGates);
        for (String qualityGate : automatedQualityGates) {
            CommandResult validationResult = commandExecutor.execute(
                    repositoryPath,
                    qualityGateCommand(qualityGate)
            );
            if (!validationResult.successful()) {
                return StoryCompletionResult.failure(
                        "Validation failed for `" + qualityGate + "`: " + commandMessage(validationResult)
                );
            }
        }

        CommandResult addResult = commandExecutor.execute(repositoryPath, List.of(gitCommand, "add", "--all"));
        if (!addResult.successful()) {
            return StoryCompletionResult.failure(
                    "Validation passed, but `git add --all` failed: " + commandMessage(addResult)
            );
        }

        CommandResult statusResult = commandExecutor.execute(repositoryPath, List.of(gitCommand, "status", "--porcelain"));
        if (!statusResult.successful()) {
            return StoryCompletionResult.failure(
                    "Validation passed, but Git status could not be inspected: " + commandMessage(statusResult)
            );
        }
        if (!hasText(statusResult.output())) {
            return StoryCompletionResult.failure(
                    "Validation passed, but no repository changes were available to commit for " + story.taskId() + "."
            );
        }

        String commitMessage = story.taskId() + ": " + story.title();
        CommandResult commitResult = commandExecutor.execute(
                repositoryPath,
                List.of(gitCommand, "commit", "-m", commitMessage)
        );
        if (!commitResult.successful()) {
            return StoryCompletionResult.failure(
                    "Validation passed, but the story commit could not be created: " + commandMessage(commitResult)
            );
        }

        CommandResult hashResult = commandExecutor.execute(repositoryPath, List.of(gitCommand, "rev-parse", "HEAD"));
        if (!hashResult.successful() || !hasText(hashResult.output())) {
            return StoryCompletionResult.failure(
                    "The story commit was created, but the commit hash could not be read: " + commandMessage(hashResult)
            );
        }

        String commitHash = hashResult.output().trim();
        String validationMessage = automatedQualityGates.isEmpty()
                ? "No automated validation commands were configured."
                : "Validation passed: " + String.join("; ", automatedQualityGates) + ".";
        return StoryCompletionResult.success(
                commitHash,
                commitMessage,
                validationMessage + " Created commit " + commitHash + " with message `" + commitMessage + "`."
        );
    }

    private List<String> automatedQualityGates(List<String> qualityGates) {
        if (qualityGates == null || qualityGates.isEmpty()) {
            return List.of();
        }

        List<String> automatedQualityGates = new ArrayList<>();
        for (String qualityGate : qualityGates) {
            String normalizedQualityGate = normalizeAutomatedQualityGate(qualityGate);
            if (looksExecutable(normalizedQualityGate)) {
                automatedQualityGates.add(normalizedQualityGate.trim());
            }
        }
        return List.copyOf(automatedQualityGates);
    }

    private String normalizeAutomatedQualityGate(String value) {
        if (!hasText(value)) {
            return "";
        }

        String trimmedValue = value.trim();
        String normalizedValue = trimmedValue.toLowerCase(Locale.ROOT);
        for (String supportedMavenWrapperCommand : List.of(
                ".\\mvnw.cmd clean verify jacoco:report",
                "./mvnw clean verify jacoco:report")) {
            int mavenWrapperIndex = normalizedValue.indexOf(supportedMavenWrapperCommand);
            if (mavenWrapperIndex >= 0) {
                return trimmedValue.substring(
                        mavenWrapperIndex,
                        mavenWrapperIndex + supportedMavenWrapperCommand.length()
                );
            }
        }
        return trimmedValue;
    }

    private List<String> qualityGateCommand(String qualityGate) {
        if (hostOperatingSystem.isWindows()) {
            return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-Command", qualityGate);
        }
        return List.of("/bin/sh", "-lc", qualityGate);
    }

    private boolean looksExecutable(String value) {
        if (!hasText(value)) {
            return false;
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        return normalizedValue.startsWith(".\\")
                || normalizedValue.startsWith("./")
                || normalizedValue.startsWith("git ")
                || normalizedValue.startsWith("mvn ")
                || normalizedValue.startsWith(".\\mvnw")
                || normalizedValue.startsWith("./mvnw")
                || normalizedValue.startsWith("gradle ")
                || normalizedValue.startsWith(".\\gradlew")
                || normalizedValue.startsWith("./gradlew")
                || normalizedValue.startsWith("npm ")
                || normalizedValue.startsWith("pnpm ")
                || normalizedValue.startsWith("yarn ")
                || normalizedValue.startsWith("pytest")
                || normalizedValue.startsWith("python ")
                || normalizedValue.startsWith("powershell ")
                || normalizedValue.startsWith("pwsh ");
    }

    private String commandMessage(CommandResult commandResult) {
        if (hasText(commandResult.failureMessage())) {
            return commandResult.failureMessage().trim();
        }
        if (hasText(commandResult.output())) {
            return commandResult.output().trim();
        }
        if (commandResult.exitCode() != null) {
            return "Command exited with code " + commandResult.exitCode() + ".";
        }
        return "Command failed.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    public record StoryCompletionResult(boolean successful,
                                        String detail,
                                        String commitHash,
                                        String commitMessage) {
        private static StoryCompletionResult success(String commitHash, String commitMessage, String detail) {
            return new StoryCompletionResult(true, detail, commitHash, commitMessage);
        }

        private static StoryCompletionResult failure(String detail) {
            return new StoryCompletionResult(false, detail, "", "");
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
                return CommandResult.failure("Command was interrupted.");
            }
        }
    }
}
