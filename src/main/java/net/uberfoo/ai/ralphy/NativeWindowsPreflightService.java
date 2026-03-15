package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class NativeWindowsPreflightService {
    static final String QUALITY_GATE_COMMAND = ".\\mvnw.cmd clean verify jacoco:report";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> environmentVariables;
    private final Path codexHomeDirectory;
    private final CommandExecutor commandExecutor;

    public NativeWindowsPreflightService() {
        this(
                System.getenv(),
                resolveCodexHomeDirectory(System.getenv(), System.getProperty("user.home")),
                new SystemCommandExecutor()
        );
    }

    NativeWindowsPreflightService(Map<String, String> environmentVariables,
                                  Path codexHomeDirectory,
                                  CommandExecutor commandExecutor) {
        this.environmentVariables = Map.copyOf(Objects.requireNonNull(environmentVariables,
                "environmentVariables must not be null"));
        this.codexHomeDirectory = Objects.requireNonNull(codexHomeDirectory, "codexHomeDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
    }

    public NativeWindowsPreflightReport run(ActiveProject activeProject) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");

        List<NativeWindowsPreflightReport.CheckResult> checks = new ArrayList<>();
        checks.add(checkCodexAvailability());
        checks.add(checkAuthentication());
        checks.add(checkGitReadiness(activeProject));
        checks.add(checkQualityGateCommand(activeProject));

        NativeWindowsPreflightReport.OverallStatus overallStatus = checks.stream()
                .allMatch(checkResult -> checkResult.status() == NativeWindowsPreflightReport.CheckStatus.PASS)
                ? NativeWindowsPreflightReport.OverallStatus.PASS
                : NativeWindowsPreflightReport.OverallStatus.FAIL;

        return new NativeWindowsPreflightReport(Instant.now().toString(), overallStatus, checks);
    }

    private NativeWindowsPreflightReport.CheckResult checkCodexAvailability() {
        CommandResult commandResult = commandExecutor.execute(null, List.of("codex", "--version"));
        if (!commandResult.successful()) {
            return fail(
                    "codex_cli",
                    "Codex CLI",
                    NativeWindowsPreflightReport.CheckCategory.TOOLING,
                    commandFailureDetail("Codex CLI is unavailable", commandResult)
            );
        }

        return pass(
                "codex_cli",
                "Codex CLI",
                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                "Detected " + summarizeOutput(commandResult.output(), "the installed Codex CLI") + "."
        );
    }

    private NativeWindowsPreflightReport.CheckResult checkAuthentication() {
        String apiKey = environmentVariables.get("OPENAI_API_KEY");
        if (hasText(apiKey)) {
            return pass(
                    "codex_auth",
                    "Codex Auth",
                    NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                    "Detected OPENAI_API_KEY in the current process environment."
            );
        }

        Path authFilePath = codexHomeDirectory.resolve("auth.json");
        if (!Files.exists(authFilePath)) {
            return fail(
                    "codex_auth",
                    "Codex Auth",
                    NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                    "No OPENAI_API_KEY environment variable or Codex auth file was found at " + authFilePath + "."
            );
        }

        try {
            JsonNode authDocument = objectMapper.readTree(authFilePath.toFile());
            if (hasText(authDocument.path("OPENAI_API_KEY").asText(null))) {
                return pass(
                        "codex_auth",
                        "Codex Auth",
                        NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                        "Detected a stored Codex API key in " + authFilePath + "."
                );
            }
            if (hasTokenData(authDocument.path("tokens"))) {
                return pass(
                        "codex_auth",
                        "Codex Auth",
                        NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                        "Detected stored Codex login tokens in " + authFilePath + "."
                );
            }
        } catch (IOException exception) {
            return fail(
                    "codex_auth",
                    "Codex Auth",
                    NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                    "Unable to read Codex auth state from " + authFilePath + ": " + exception.getMessage()
            );
        }

        return fail(
                "codex_auth",
                "Codex Auth",
                NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                "No stored Codex credentials were found in " + authFilePath + "."
        );
    }

    private NativeWindowsPreflightReport.CheckResult checkGitReadiness(ActiveProject activeProject) {
        if (!Files.exists(activeProject.repositoryPath().resolve(".git"))) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    NativeWindowsPreflightReport.CheckCategory.GIT,
                    "The active repository is missing .git metadata at " + activeProject.repositoryPath() + "."
            );
        }

        CommandResult commandResult = commandExecutor.execute(
                activeProject.repositoryPath(),
                List.of("git", "rev-parse", "--is-inside-work-tree")
        );
        if (!commandResult.successful()) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    NativeWindowsPreflightReport.CheckCategory.GIT,
                    commandFailureDetail("Git is not ready for the active repository", commandResult)
            );
        }

        String normalizedOutput = normalize(commandResult.output());
        if (!"true".equalsIgnoreCase(normalizedOutput)) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    NativeWindowsPreflightReport.CheckCategory.GIT,
                    "Git responded with '" + normalizedOutput + "' instead of confirming the working tree."
            );
        }

        return pass(
                "git_ready",
                "Git Readiness",
                NativeWindowsPreflightReport.CheckCategory.GIT,
                "Git can access the active repository at " + activeProject.repositoryPath() + "."
        );
    }

    private NativeWindowsPreflightReport.CheckResult checkQualityGateCommand(ActiveProject activeProject) {
        Path mvnwPath = activeProject.repositoryPath().resolve("mvnw.cmd");
        Path pomPath = activeProject.repositoryPath().resolve("pom.xml");
        if (!Files.isRegularFile(mvnwPath)) {
            return fail(
                    "quality_gate",
                    "Quality Gate Command",
                    NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                    "The quality-gate command " + QUALITY_GATE_COMMAND + " is unavailable because " + mvnwPath
                            + " is missing."
            );
        }
        if (!Files.isRegularFile(pomPath)) {
            return fail(
                    "quality_gate",
                    "Quality Gate Command",
                    NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                    "The quality-gate command " + QUALITY_GATE_COMMAND + " is unavailable because " + pomPath
                            + " is missing."
            );
        }

        return pass(
                "quality_gate",
                "Quality Gate Command",
                NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                "Found mvnw.cmd and pom.xml for " + QUALITY_GATE_COMMAND + "."
        );
    }

    private boolean hasTokenData(JsonNode tokensNode) {
        if (tokensNode == null || tokensNode.isMissingNode() || tokensNode.isNull()) {
            return false;
        }
        if (tokensNode.isContainerNode()) {
            return tokensNode.size() > 0;
        }
        return hasText(tokensNode.asText(null));
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
        return firstLine;
    }

    private NativeWindowsPreflightReport.CheckResult pass(String id,
                                                          String label,
                                                          NativeWindowsPreflightReport.CheckCategory category,
                                                          String detail) {
        return new NativeWindowsPreflightReport.CheckResult(
                id,
                label,
                category,
                NativeWindowsPreflightReport.CheckStatus.PASS,
                detail
        );
    }

    private NativeWindowsPreflightReport.CheckResult fail(String id,
                                                          String label,
                                                          NativeWindowsPreflightReport.CheckCategory category,
                                                          String detail) {
        return new NativeWindowsPreflightReport.CheckResult(
                id,
                label,
                category,
                NativeWindowsPreflightReport.CheckStatus.FAIL,
                detail
        );
    }

    private static Path resolveCodexHomeDirectory(Map<String, String> environmentVariables, String userHome) {
        String configuredCodexHome = environmentVariables.get("CODEX_HOME");
        if (hasText(configuredCodexHome)) {
            return Path.of(configuredCodexHome);
        }
        if (hasText(userHome)) {
            return Path.of(userHome, ".codex");
        }
        return Path.of(".codex");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
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
