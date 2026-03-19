package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Optional;

@Component
public class NativeWindowsPreflightService {
    static final String WINDOWS_QUALITY_GATE_COMMAND = ".\\mvnw.cmd clean verify jacoco:report";
    static final String WINDOWS_GRADLE_QUALITY_GATE_COMMAND = ".\\gradlew.bat test";
    static final String LINUX_QUALITY_GATE_COMMAND = "./mvnw clean verify jacoco:report";
    static final String LINUX_GRADLE_QUALITY_GATE_COMMAND = "./gradlew test";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> environmentVariables;
    private final Path codexHomeDirectory;
    private final CommandExecutor commandExecutor;
    private final String codexCommand;
    private final String copilotCommand;
    private final HostOperatingSystem hostOperatingSystem;

    public NativeWindowsPreflightService(
            @Value("${ralphy.codex.command:" + CodexCliSupport.DEFAULT_COMMAND + "}") String codexCommand,
            @Value("${ralphy.copilot.command:" + CopilotCliSupport.DEFAULT_COMMAND + "}") String copilotCommand) {
        this(
                codexCommand,
                copilotCommand,
                System.getenv(),
                resolveCodexHomeDirectory(System.getenv(), System.getProperty("user.home")),
                HostOperatingSystem.detectRuntime(),
                new SystemCommandExecutor()
        );
    }

    NativeWindowsPreflightService(String codexCommand,
                                  String copilotCommand,
                                  Map<String, String> environmentVariables,
                                  Path codexHomeDirectory,
                                  HostOperatingSystem hostOperatingSystem,
                                  CommandExecutor commandExecutor) {
        this.codexCommand = hasText(codexCommand) ? codexCommand.trim() : CodexCliSupport.DEFAULT_COMMAND;
        this.copilotCommand = hasText(copilotCommand) ? copilotCommand.trim() : CopilotCliSupport.DEFAULT_COMMAND;
        this.environmentVariables = Map.copyOf(Objects.requireNonNull(environmentVariables,
                "environmentVariables must not be null"));
        this.codexHomeDirectory = Objects.requireNonNull(codexHomeDirectory, "codexHomeDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        this.hostOperatingSystem = hostOperatingSystem == null ? HostOperatingSystem.detectRuntime() : hostOperatingSystem;
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
    }

    NativeWindowsPreflightService() {
        this(
                CodexCliSupport.DEFAULT_COMMAND,
                CopilotCliSupport.DEFAULT_COMMAND,
                System.getenv(),
                resolveCodexHomeDirectory(System.getenv(), System.getProperty("user.home")),
                HostOperatingSystem.detectRuntime(),
                new SystemCommandExecutor()
        );
    }

    NativeWindowsPreflightService(Map<String, String> environmentVariables,
                                  Path codexHomeDirectory,
                                  CommandExecutor commandExecutor) {
        this(
                CodexCliSupport.DEFAULT_COMMAND,
                CopilotCliSupport.DEFAULT_COMMAND,
                environmentVariables,
                codexHomeDirectory,
                HostOperatingSystem.detectRuntime(),
                commandExecutor
        );
    }

    NativeWindowsPreflightService(Map<String, String> environmentVariables,
                                  Path codexHomeDirectory,
                                  HostOperatingSystem hostOperatingSystem,
                                  CommandExecutor commandExecutor) {
        this(
                CodexCliSupport.DEFAULT_COMMAND,
                CopilotCliSupport.DEFAULT_COMMAND,
                environmentVariables,
                codexHomeDirectory,
                hostOperatingSystem,
                commandExecutor
        );
    }

    public NativeWindowsPreflightReport run(ActiveProject activeProject) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");

        List<NativeWindowsPreflightReport.CheckResult> checks = new ArrayList<>();
        checks.add(checkCodexAvailability());
        checks.add(checkCopilotAvailability());
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
        List<String> commandResultArguments = CodexCliSupport.buildNativeCommand(
                codexCommand,
                environmentVariables,
                hostOperatingSystem,
                List.of("--version")
        );
        CommandResult commandResult = commandExecutor.execute(null, commandResultArguments);
        if (!commandResult.successful()) {
            return fail(
                    "codex_cli",
                    "Codex CLI",
                    NativeWindowsPreflightReport.CheckCategory.TOOLING,
                    commandFailureDetail("Codex CLI is unavailable", commandResult),
                    codexCliRemediationCommands()
            );
        }

        return pass(
                "codex_cli",
                "Codex CLI",
                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                "Detected " + summarizeOutput(commandResult.output(), "the installed Codex CLI") + "."
        );
    }

    private NativeWindowsPreflightReport.CheckResult checkCopilotAvailability() {
        List<String> commandResultArguments = CopilotCliSupport.buildNativeCommand(
                copilotCommand,
                environmentVariables,
                hostOperatingSystem,
                List.of("--version")
        );
        CommandResult commandResult = commandExecutor.execute(null, commandResultArguments);
        if (!commandResult.successful()) {
            return fail(
                    "copilot_cli",
                    "GitHub Copilot CLI",
                    NativeWindowsPreflightReport.CheckCategory.TOOLING,
                    commandFailureDetail("GitHub Copilot CLI is unavailable", commandResult),
                    copilotCliRemediationCommands()
            );
        }

        return pass(
                "copilot_cli",
                "GitHub Copilot CLI",
                NativeWindowsPreflightReport.CheckCategory.TOOLING,
                "Detected " + summarizeOutput(commandResult.output(), "the installed GitHub Copilot CLI") + "."
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
                    "No OPENAI_API_KEY environment variable or Codex auth file was found at " + authFilePath + ".",
                    codexAuthRemediationCommands()
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
                    "Unable to read Codex auth state from " + authFilePath + ": " + exception.getMessage(),
                    codexAuthRemediationCommands()
            );
        }

        return fail(
                "codex_auth",
                "Codex Auth",
                NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION,
                "No stored Codex credentials were found in " + authFilePath + ".",
                codexAuthRemediationCommands()
        );
    }

    private NativeWindowsPreflightReport.CheckResult checkGitReadiness(ActiveProject activeProject) {
        if (!Files.exists(activeProject.repositoryPath().resolve(".git"))) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    NativeWindowsPreflightReport.CheckCategory.GIT,
                    "The active repository is missing .git metadata at " + activeProject.repositoryPath() + ".",
                    gitRemediationCommands(activeProject)
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
                    commandFailureDetail("Git is not ready for the active repository", commandResult),
                    gitRemediationCommands(activeProject)
            );
        }

        String normalizedOutput = normalize(commandResult.output());
        if (!"true".equalsIgnoreCase(normalizedOutput)) {
            return fail(
                    "git_ready",
                    "Git Readiness",
                    NativeWindowsPreflightReport.CheckCategory.GIT,
                    "Git responded with '" + normalizedOutput + "' instead of confirming the working tree.",
                    gitRemediationCommands(activeProject)
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
        Optional<String> detectedQualityGateCommand = detectNativeQualityGateCommand(activeProject.repositoryPath());
        if (detectedQualityGateCommand.isPresent()) {
            return pass(
                    "quality_gate",
                    "Quality Gate Command",
                    NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                    "Detected repository quality gate " + detectedQualityGateCommand.get() + "."
            );
        }
        return pass(
                "quality_gate",
                "Quality Gate Command",
                NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE,
                "No repository-owned native quality gate command was auto-detected. PRD-defined quality gates remain supported."
        );
    }

    private Optional<String> detectNativeQualityGateCommand(Path repositoryPath) {
        Path mvnwPath = hostOperatingSystem.isWindows()
                ? repositoryPath.resolve("mvnw.cmd")
                : repositoryPath.resolve("mvnw");
        Path pomPath = repositoryPath.resolve("pom.xml");
        if (Files.isRegularFile(mvnwPath) && Files.isRegularFile(pomPath)) {
            return Optional.of(mavenQualityGateCommand());
        }

        Path gradleWrapperPath = hostOperatingSystem.isWindows()
                ? repositoryPath.resolve("gradlew.bat")
                : repositoryPath.resolve("gradlew");
        Path buildGradlePath = repositoryPath.resolve("build.gradle");
        Path buildGradleKtsPath = repositoryPath.resolve("build.gradle.kts");
        if (Files.isRegularFile(gradleWrapperPath)
                && (Files.isRegularFile(buildGradlePath) || Files.isRegularFile(buildGradleKtsPath))) {
            return Optional.of(gradleQualityGateCommand());
        }

        return Optional.empty();
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
        return fail(id, label, category, detail, List.of());
    }

    private NativeWindowsPreflightReport.CheckResult fail(String id,
                                                          String label,
                                                          NativeWindowsPreflightReport.CheckCategory category,
                                                          String detail,
                                                          List<PreflightRemediationCommand> remediationCommands) {
        return new NativeWindowsPreflightReport.CheckResult(
                id,
                label,
                category,
                NativeWindowsPreflightReport.CheckStatus.FAIL,
                detail,
                remediationCommands
        );
    }

    private List<PreflightRemediationCommand> codexCliRemediationCommands() {
        return List.of(
                remediation("Install Codex CLI", CodexCliSupport.INSTALL_COMMAND),
                remediation("Verify Codex CLI is available", "codex --version")
        );
    }

    private List<PreflightRemediationCommand> codexAuthRemediationCommands() {
        return List.of(
                remediation("Authenticate Codex CLI", CodexCliSupport.LOGIN_COMMAND),
                remediation("Check Codex login status", CodexCliSupport.LOGIN_STATUS_COMMAND)
        );
    }

    private List<PreflightRemediationCommand> copilotCliRemediationCommands() {
        return List.of(
                remediation("Install GitHub Copilot CLI", CopilotCliSupport.INSTALL_COMMAND),
                remediation("Authenticate GitHub Copilot CLI", CopilotCliSupport.LOGIN_COMMAND),
                remediation("Verify GitHub Copilot CLI is available", "copilot --version")
        );
    }

    private List<PreflightRemediationCommand> gitRemediationCommands(ActiveProject activeProject) {
        String repositoryPath = quoteForNativeShell(activeProject.repositoryPath().toString());
        return List.of(
                remediation("Inspect Git status", "git -C " + repositoryPath + " status"),
                remediation("Initialize Git metadata if this is a fresh repository",
                        "git -C " + repositoryPath + " init")
        );
    }

    private PreflightRemediationCommand remediation(String label, String command) {
        return new PreflightRemediationCommand(label, command);
    }

    private String quoteForNativeShell(String value) {
        if (hostOperatingSystem.isWindows()) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        String safeValue = value == null ? "" : value;
        return "'" + safeValue.replace("'", "'\"'\"'") + "'";
    }

    private String mavenQualityGateCommand() {
        return hostOperatingSystem.isWindows() ? WINDOWS_QUALITY_GATE_COMMAND : LINUX_QUALITY_GATE_COMMAND;
    }

    private String gradleQualityGateCommand() {
        return hostOperatingSystem.isWindows() ? WINDOWS_GRADLE_QUALITY_GATE_COMMAND : LINUX_GRADLE_QUALITY_GATE_COMMAND;
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
