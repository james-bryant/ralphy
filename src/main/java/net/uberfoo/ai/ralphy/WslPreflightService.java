package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class WslPreflightService {
    private final CommandExecutor commandExecutor;

    public WslPreflightService() {
        this(new SystemCommandExecutor());
    }

    WslPreflightService(CommandExecutor commandExecutor) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
    }

    public WslPreflightReport run(ActiveProject activeProject, ExecutionProfile executionProfile) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");

        List<WslPreflightReport.CheckResult> checks = new ArrayList<>();
        DistroAvailability distroAvailability = checkDistroAvailability(executionProfile);
        checks.add(distroAvailability.checkResult());

        WslPathMapper.PathMappingResult pathMappingResult =
                WslPathMapper.mapRepositoryPath(executionProfile, activeProject.repositoryPath());
        checks.add(checkPathMapping(executionProfile, pathMappingResult));

        checks.add(checkCodexAvailability(executionProfile, distroAvailability.available()));
        checks.add(checkAuthentication(executionProfile, distroAvailability.available()));
        checks.add(checkGitReadiness(executionProfile, distroAvailability.available(), pathMappingResult));

        WslPreflightReport.OverallStatus overallStatus = checks.stream()
                .allMatch(checkResult -> checkResult.status() == WslPreflightReport.CheckStatus.PASS)
                ? WslPreflightReport.OverallStatus.PASS
                : WslPreflightReport.OverallStatus.FAIL;

        return new WslPreflightReport(Instant.now().toString(), overallStatus, checks);
    }

    private DistroAvailability checkDistroAvailability(ExecutionProfile executionProfile) {
        CommandResult commandResult = commandExecutor.execute(null, List.of("wsl.exe", "--list", "--quiet"));
        if (!commandResult.successful()) {
            return DistroAvailability.unavailable(fail(
                    "wsl_distribution",
                    "WSL Distribution",
                    WslPreflightReport.CheckCategory.DISTRIBUTION,
                    commandFailureDetail("Unable to enumerate WSL distributions", commandResult)
            ));
        }

        String configuredDistribution = normalize(executionProfile.wslDistribution());
        List<String> configuredDistributions = normalize(commandResult.output()).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        boolean available = configuredDistributions.stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(configuredDistribution));
        if (!available) {
            String availableDistributions = configuredDistributions.isEmpty()
                    ? "none"
                    : String.join(", ", configuredDistributions);
            return DistroAvailability.unavailable(fail(
                    "wsl_distribution",
                    "WSL Distribution",
                    WslPreflightReport.CheckCategory.DISTRIBUTION,
                    "The configured WSL distribution '" + configuredDistribution
                            + "' is unavailable. Detected distributions: " + availableDistributions + "."
            ));
        }

        return DistroAvailability.available(pass(
                "wsl_distribution",
                "WSL Distribution",
                WslPreflightReport.CheckCategory.DISTRIBUTION,
                "Found the configured WSL distribution " + configuredDistribution + "."
        ));
    }

    private WslPreflightReport.CheckResult checkPathMapping(ExecutionProfile executionProfile,
                                                            WslPathMapper.PathMappingResult pathMappingResult) {
        if (!pathMappingResult.successful()) {
            return fail(
                    "path_mapping",
                    "Repository Path Mapping",
                    WslPreflightReport.CheckCategory.PATH_MAPPING,
                    pathMappingResult.message()
            );
        }

        CommandResult commandResult = executeInDistro(executionProfile.wslDistribution(),
                pathMappingResult.wslPath(), "pwd");
        if (!commandResult.successful()) {
            return fail(
                    "path_mapping",
                    "Repository Path Mapping",
                    WslPreflightReport.CheckCategory.PATH_MAPPING,
                    commandFailureDetail("WSL could not access the mapped repository path " + pathMappingResult.wslPath(),
                            commandResult)
            );
        }

        return pass(
                "path_mapping",
                "Repository Path Mapping",
                WslPreflightReport.CheckCategory.PATH_MAPPING,
                "Mapped the active repository to " + pathMappingResult.wslPath() + "."
        );
    }

    private WslPreflightReport.CheckResult checkCodexAvailability(ExecutionProfile executionProfile,
                                                                  boolean distroAvailable) {
        if (!distroAvailable) {
            return fail(
                    "codex_cli",
                    "Codex CLI",
                    WslPreflightReport.CheckCategory.TOOLING,
                    "Codex CLI could not be checked because the configured WSL distribution is unavailable."
            );
        }

        CommandResult commandResult = executeInDistro(executionProfile.wslDistribution(), null, "codex --version");
        if (!commandResult.successful()) {
            return fail(
                    "codex_cli",
                    "Codex CLI",
                    WslPreflightReport.CheckCategory.TOOLING,
                    commandFailureDetail("Codex CLI is unavailable inside the selected WSL distribution", commandResult)
            );
        }

        return pass(
                "codex_cli",
                "Codex CLI",
                WslPreflightReport.CheckCategory.TOOLING,
                "Detected " + summarizeOutput(commandResult.output(), "the installed Codex CLI") + "."
        );
    }

    private WslPreflightReport.CheckResult checkAuthentication(ExecutionProfile executionProfile,
                                                               boolean distroAvailable) {
        if (!distroAvailable) {
            return fail(
                    "codex_auth",
                    "Codex Auth",
                    WslPreflightReport.CheckCategory.AUTHENTICATION,
                    "Codex authentication could not be checked because the configured WSL distribution is unavailable."
            );
        }

        CommandResult commandResult = executeInDistro(executionProfile.wslDistribution(), null, """
                if [ -n "${OPENAI_API_KEY:-}" ]; then
                  printf 'Detected OPENAI_API_KEY in the WSL environment.'
                elif [ -f "$HOME/.codex/auth.json" ] && grep -Eq '"OPENAI_API_KEY"[[:space:]]*:[[:space:]]*"[^"]+' "$HOME/.codex/auth.json"; then
                  printf 'Detected a stored Codex API key in %s.' "$HOME/.codex/auth.json"
                elif [ -f "$HOME/.codex/auth.json" ] && grep -Eq '"tokens"[[:space:]]*:[[:space:]]*[{[]' "$HOME/.codex/auth.json"; then
                  printf 'Detected stored Codex login tokens in %s.' "$HOME/.codex/auth.json"
                else
                  printf 'No OPENAI_API_KEY environment variable or stored Codex credentials were found in %s.' "$HOME/.codex/auth.json"
                  exit 1
                fi
                """);
        if (!commandResult.successful()) {
            return fail(
                    "codex_auth",
                    "Codex Auth",
                    WslPreflightReport.CheckCategory.AUTHENTICATION,
                    summarizeOutput(commandResult.output(),
                            commandFailureDetail("Codex authentication is unavailable inside the selected WSL distribution",
                                    commandResult))
            );
        }

        return pass(
                "codex_auth",
                "Codex Auth",
                WslPreflightReport.CheckCategory.AUTHENTICATION,
                summarizeOutput(commandResult.output(), "Detected Codex authentication inside WSL.") + "."
        );
    }

    private WslPreflightReport.CheckResult checkGitReadiness(ExecutionProfile executionProfile,
                                                             boolean distroAvailable,
                                                             WslPathMapper.PathMappingResult pathMappingResult) {
        if (!distroAvailable) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    WslPreflightReport.CheckCategory.GIT,
                    "Git readiness could not be checked because the configured WSL distribution is unavailable."
            );
        }
        if (!pathMappingResult.successful()) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    WslPreflightReport.CheckCategory.GIT,
                    "Git readiness could not be checked because the repository path mapping is invalid."
            );
        }

        CommandResult commandResult = executeInDistro(
                executionProfile.wslDistribution(),
                pathMappingResult.wslPath(),
                "git rev-parse --is-inside-work-tree"
        );
        if (!commandResult.successful()) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    WslPreflightReport.CheckCategory.GIT,
                    commandFailureDetail("Git is not ready inside the selected WSL distribution", commandResult)
            );
        }

        String normalizedOutput = normalize(commandResult.output());
        if (!"true".equalsIgnoreCase(normalizedOutput)) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    WslPreflightReport.CheckCategory.GIT,
                    "Git responded with '" + normalizedOutput + "' instead of confirming the mapped working tree."
            );
        }

        return pass(
                "git_ready",
                "Git Readiness",
                WslPreflightReport.CheckCategory.GIT,
                "Git can access the active repository at " + pathMappingResult.wslPath() + "."
        );
    }

    private CommandResult executeInDistro(String distribution, String workingDirectory, String script) {
        List<String> command = new ArrayList<>();
        command.add("wsl.exe");
        command.add("--distribution");
        command.add(distribution);
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            command.add("--cd");
            command.add(workingDirectory);
        }
        command.add("--exec");
        command.add("/bin/sh");
        command.add("-lc");
        command.add(script);
        return commandExecutor.execute(null, List.copyOf(command));
    }

    private String commandFailureDetail(String prefix, CommandResult commandResult) {
        if (hasText(commandResult.failureMessage())) {
            return prefix + ": " + commandResult.failureMessage();
        }
        if (hasText(commandResult.output())) {
            return prefix + ": " + summarizeOutput(commandResult.output(), "command output");
        }
        if (commandResult.exitCode() != null) {
            return prefix + ": command exited with code " + commandResult.exitCode() + ".";
        }
        return prefix + ".";
    }

    private String summarizeOutput(String output, String fallback) {
        String normalizedOutput = normalize(output);
        if (!hasText(normalizedOutput)) {
            return fallback;
        }

        String firstLine = normalizedOutput.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);
        return firstLine.endsWith(".") ? firstLine.substring(0, firstLine.length() - 1) : firstLine;
    }

    private WslPreflightReport.CheckResult pass(String id,
                                                String label,
                                                WslPreflightReport.CheckCategory category,
                                                String detail) {
        return new WslPreflightReport.CheckResult(
                id,
                label,
                category,
                WslPreflightReport.CheckStatus.PASS,
                detail
        );
    }

    private WslPreflightReport.CheckResult fail(String id,
                                                String label,
                                                WslPreflightReport.CheckCategory category,
                                                String detail) {
        return new WslPreflightReport.CheckResult(
                id,
                label,
                category,
                WslPreflightReport.CheckStatus.FAIL,
                detail
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\u0000", "").trim();
    }

    interface CommandExecutor {
        CommandResult execute(Path workingDirectory, List<String> command);
    }

    record CommandResult(Integer exitCode, String output, String failureMessage) {
        static CommandResult success(int exitCode, String output) {
            return new CommandResult(exitCode, output, null);
        }

        static CommandResult failure(String failureMessage) {
            return new CommandResult(null, "", failureMessage);
        }

        boolean successful() {
            return failureMessage == null && exitCode != null && exitCode == 0;
        }
    }

    private record DistroAvailability(boolean available, WslPreflightReport.CheckResult checkResult) {
        private static DistroAvailability available(WslPreflightReport.CheckResult checkResult) {
            return new DistroAvailability(true, checkResult);
        }

        private static DistroAvailability unavailable(WslPreflightReport.CheckResult checkResult) {
            return new DistroAvailability(false, checkResult);
        }
    }

    private static final class SystemCommandExecutor implements CommandExecutor {
        @Override
        public CommandResult execute(Path workingDirectory, List<String> command) {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory.toFile());
            }
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();
                String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = process.waitFor();
                return CommandResult.success(exitCode, processOutput);
            } catch (IOException exception) {
                return CommandResult.failure(exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return CommandResult.failure("Command execution was interrupted.");
            }
        }
    }
}
