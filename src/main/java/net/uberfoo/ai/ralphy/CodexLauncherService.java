package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Component
public class CodexLauncherService {
    private final LocalMetadataStorage localMetadataStorage;
    private final Clock clock;
    private final Supplier<String> runIdGenerator;
    private final ProcessExecutor processExecutor;
    private final ObjectMapper objectMapper;

    @Autowired
    public CodexLauncherService(LocalMetadataStorage localMetadataStorage) {
        this(localMetadataStorage, Clock.systemUTC(), () -> UUID.randomUUID().toString(), new SystemProcessExecutor());
    }

    CodexLauncherService(LocalMetadataStorage localMetadataStorage,
                         Clock clock,
                         Supplier<String> runIdGenerator,
                         ProcessExecutor processExecutor) {
        this.localMetadataStorage = Objects.requireNonNull(localMetadataStorage, "localMetadataStorage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.runIdGenerator = Objects.requireNonNull(runIdGenerator, "runIdGenerator must not be null");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public CodexLaunchPlan buildLaunch(CodexLaunchRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String runId = normalizeRunId(runIdGenerator.get());
        String promptText = renderPrompt(request);
        List<String> codexCommandTokens = codexCommandTokens(request.codexOptions());

        if (request.executionProfile().type() == ExecutionProfile.ProfileType.POWERSHELL) {
            return new CodexLaunchPlan(
                    runId,
                    request.storyId(),
                    request.activeProject(),
                    request.executionProfile(),
                    request.preset(),
                    List.of(
                            "powershell.exe",
                            "-NoLogo",
                            "-NoProfile",
                            "-Command",
                            buildPowerShellCommand(codexCommandTokens)
                    ),
                    request.activeProject().repositoryPath(),
                    request.activeProject().repositoryPath().toString(),
                    promptText
            );
        }

        WslPathMapper.PathMappingResult pathMappingResult =
                WslPathMapper.mapRepositoryPath(request.executionProfile(), request.activeProject().repositoryPath());
        if (!pathMappingResult.successful()) {
            throw new IllegalArgumentException(pathMappingResult.message());
        }

        return new CodexLaunchPlan(
                runId,
                request.storyId(),
                request.activeProject(),
                request.executionProfile(),
                request.preset(),
                List.of(
                        "wsl.exe",
                        "--distribution",
                        request.executionProfile().wslDistribution(),
                        "--cd",
                        pathMappingResult.wslPath(),
                        "--exec",
                        "/bin/sh",
                        "-lc",
                        buildShCommand(codexCommandTokens)
                ),
                null,
                pathMappingResult.wslPath(),
                promptText
        );
    }

    public CodexLaunchResult launch(CodexLaunchRequest request) {
        return launch(buildLaunch(request));
    }

    CodexLaunchResult launch(CodexLaunchPlan launchPlan) {
        Objects.requireNonNull(launchPlan, "launchPlan must not be null");

        String startedAt = Instant.now(clock).toString();
        persistRunningMetadata(launchPlan, startedAt);
        ProcessExecution processExecution = processExecutor.execute(launchPlan);
        String endedAt = Instant.now(clock).toString();
        boolean successful = processExecution.successful();
        String status = successful ? "SUCCEEDED" : "FAILED";
        String message = launchMessage(processExecution);
        RunArtifacts artifacts = persistRunArtifacts(
                launchPlan,
                status,
                startedAt,
                endedAt,
                processExecution.processId(),
                processExecution.exitCode(),
                processExecution.stdout(),
                processExecution.stderr(),
                message
        );

        CodexLaunchResult launchResult = new CodexLaunchResult(
                successful,
                launchPlan,
                status,
                startedAt,
                endedAt,
                processExecution.processId(),
                processExecution.exitCode(),
                processExecution.stdout(),
                processExecution.stderr(),
                message,
                artifacts
        );
        persistRunMetadata(launchResult);
        return launchResult;
    }

    private void persistRunningMetadata(CodexLaunchPlan launchPlan, String startedAt) {
        localMetadataStorage.projectRecordForRepository(launchPlan.activeProject().repositoryPath())
                .ifPresent(projectRecord -> localMetadataStorage.saveRunMetadata(new LocalMetadataStorage.RunMetadataRecord(
                        launchPlan.runId(),
                        projectRecord.projectId(),
                        launchPlan.storyId(),
                        "RUNNING",
                        startedAt,
                        null,
                        launchPlan.executionProfile().type().storageValue(),
                        launchPlan.executionWorkingDirectory(),
                        null,
                        null,
                        launchPlan.command(),
                        LocalMetadataStorage.RunArtifactPaths.empty()
                )));
    }

    private void persistRunMetadata(CodexLaunchResult launchResult) {
        localMetadataStorage.projectRecordForRepository(launchResult.launchPlan().activeProject().repositoryPath())
                .ifPresent(projectRecord -> localMetadataStorage.saveRunMetadata(new LocalMetadataStorage.RunMetadataRecord(
                        launchResult.launchPlan().runId(),
                        projectRecord.projectId(),
                        launchResult.launchPlan().storyId(),
                        launchResult.status(),
                        launchResult.startedAt(),
                        launchResult.endedAt(),
                        launchResult.launchPlan().executionProfile().type().storageValue(),
                        launchResult.launchPlan().executionWorkingDirectory(),
                        launchResult.processId(),
                        launchResult.exitCode(),
                        launchResult.launchPlan().command(),
                        new LocalMetadataStorage.RunArtifactPaths(
                                launchResult.artifacts().promptPath().toString(),
                                launchResult.artifacts().stdoutPath().toString(),
                                launchResult.artifacts().stderrPath().toString(),
                                launchResult.artifacts().structuredEventsPath() == null
                                        ? null
                                        : launchResult.artifacts().structuredEventsPath().toString(),
                                launchResult.artifacts().summaryPath().toString()
                        )
                )));
    }

    private RunArtifacts persistRunArtifacts(CodexLaunchPlan launchPlan,
                                             String status,
                                             String startedAt,
                                             String endedAt,
                                             Long processId,
                                             Integer exitCode,
                                             String stdout,
                                             String stderr,
                                             String message) {
        try {
            Path promptDirectory = attemptDirectory(
                    launchPlan.activeProject().promptsDirectoryPath(),
                    launchPlan.storyId(),
                    launchPlan.runId()
            );
            Path logDirectory = attemptDirectory(
                    launchPlan.activeProject().logsDirectoryPath(),
                    launchPlan.storyId(),
                    launchPlan.runId()
            );
            Path artifactDirectory = attemptDirectory(
                    launchPlan.activeProject().artifactsDirectoryPath(),
                    launchPlan.storyId(),
                    launchPlan.runId()
            );
            Files.createDirectories(promptDirectory);
            Files.createDirectories(logDirectory);
            Files.createDirectories(artifactDirectory);

            Path promptPath = promptDirectory.resolve("prompt.txt");
            Path stdoutPath = logDirectory.resolve("stdout.log");
            Path stderrPath = logDirectory.resolve("stderr.log");
            writeTextArtifact(promptPath, launchPlan.promptText());
            writeTextArtifact(stdoutPath, stdout);
            writeTextArtifact(stderrPath, stderr);

            Path structuredEventsPath = null;
            String structuredEvents = extractStructuredEvents(stdout);
            if (hasText(structuredEvents)) {
                structuredEventsPath = logDirectory.resolve("structured-events.jsonl");
                writeTextArtifact(structuredEventsPath, structuredEvents);
            }

            Path summaryPath = artifactDirectory.resolve("attempt-summary.json");
            RunArtifacts artifacts = new RunArtifacts(promptPath, stdoutPath, stderrPath, structuredEventsPath, summaryPath);
            writeJsonArtifact(summaryPath, new AttemptSummaryArtifact(
                    launchPlan.runId(),
                    launchPlan.storyId(),
                    status,
                    startedAt,
                    endedAt,
                    launchPlan.executionProfile().type().storageValue(),
                    launchPlan.executionWorkingDirectory(),
                    processId,
                    exitCode,
                    launchPlan.command(),
                    message,
                    new LocalMetadataStorage.RunArtifactPaths(
                            promptPath.toString(),
                            stdoutPath.toString(),
                            stderrPath.toString(),
                            structuredEventsPath == null ? null : structuredEventsPath.toString(),
                            summaryPath.toString()
                    )
            ));
            return artifacts;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to store Codex run artifacts for story " + launchPlan.storyId()
                            + " and run " + launchPlan.runId(),
                    exception
            );
        }
    }

    private Path attemptDirectory(Path rootDirectory, String storyId, String runId) {
        return rootDirectory
                .resolve(sanitizePathSegment(storyId))
                .resolve(sanitizePathSegment(runId));
    }

    private String sanitizePathSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String extractStructuredEvents(String stdout) {
        if (!hasText(stdout)) {
            return "";
        }

        List<String> structuredEventLines = stdout.lines()
                .map(String::trim)
                .filter(this::hasText)
                .filter(this::isStructuredEventLine)
                .toList();
        if (structuredEventLines.isEmpty()) {
            return "";
        }

        return String.join(System.lineSeparator(), structuredEventLines);
    }

    private boolean isStructuredEventLine(String line) {
        try {
            return objectMapper.readTree(line).isContainerNode();
        } catch (IOException ignored) {
            return false;
        }
    }

    private void writeTextArtifact(Path targetPath, String content) throws IOException {
        Path temporaryPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        Files.writeString(temporaryPath, content == null ? "" : content, StandardCharsets.UTF_8);
        moveIntoPlace(temporaryPath, targetPath);
    }

    private void writeJsonArtifact(Path targetPath, Object artifact) throws IOException {
        Path temporaryPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        objectMapper.writeValue(temporaryPath.toFile(), artifact);
        moveIntoPlace(temporaryPath, targetPath);
    }

    private void moveIntoPlace(Path temporaryPath, Path targetPath) throws IOException {
        try {
            Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String renderPrompt(CodexLaunchRequest request) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Preset: ")
                .append(request.preset().displayName())
                .append(" (")
                .append(request.preset().presetId())
                .append(", ")
                .append(request.preset().version())
                .append(')')
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(request.preset().promptPreview().trim());

        List<PromptInput> populatedInputs = request.presetInputs().stream()
                .filter(input -> hasText(input.value()))
                .toList();
        if (!populatedInputs.isEmpty()) {
            promptBuilder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("Preset Inputs:")
                    .append(System.lineSeparator());
            for (PromptInput input : populatedInputs) {
                promptBuilder.append("- ")
                        .append(input.label())
                        .append(": ")
                        .append(input.value().trim())
                        .append(System.lineSeparator());
            }
        }

        if (hasText(request.additionalInstructions())) {
            promptBuilder.append(System.lineSeparator())
                    .append("Additional Instructions:")
                    .append(System.lineSeparator())
                    .append(request.additionalInstructions().trim());
        }

        return promptBuilder.toString().trim();
    }

    private List<String> codexCommandTokens(List<String> codexOptions) {
        List<String> commandTokens = new ArrayList<>();
        commandTokens.add("codex");
        commandTokens.add("exec");
        commandTokens.addAll(codexOptions);
        commandTokens.add("-");
        return List.copyOf(commandTokens);
    }

    private String buildPowerShellCommand(List<String> commandTokens) {
        StringBuilder scriptBuilder = new StringBuilder("&");
        for (String commandToken : commandTokens) {
            scriptBuilder.append(' ').append(quoteForPowerShell(commandToken));
        }
        return scriptBuilder.toString();
    }

    private String buildShCommand(List<String> commandTokens) {
        return commandTokens.stream()
                .map(this::quoteForSh)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private String quoteForPowerShell(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue.replace("'", "''") + "'";
    }

    private String quoteForSh(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue.replace("'", "'\"'\"'") + "'";
    }

    private String launchMessage(ProcessExecution processExecution) {
        if (hasText(processExecution.failureMessage())) {
            return processExecution.failureMessage();
        }
        if (processExecution.exitCode() != null && processExecution.exitCode() != 0) {
            return "Codex exited with code " + processExecution.exitCode() + ".";
        }
        return "";
    }

    private String normalizeRunId(String runId) {
        if (!hasText(runId)) {
            throw new IllegalStateException("runIdGenerator returned a blank run id.");
        }
        return runId.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CodexLaunchRequest(String storyId,
                                     ActiveProject activeProject,
                                     ExecutionProfile executionProfile,
                                     BuiltInPreset preset,
                                     List<PromptInput> presetInputs,
                                     String additionalInstructions,
                                     List<String> codexOptions) {
        public CodexLaunchRequest {
            if (storyId == null || storyId.isBlank()) {
                throw new IllegalArgumentException("storyId must not be blank");
            }
            Objects.requireNonNull(activeProject, "activeProject must not be null");
            Objects.requireNonNull(executionProfile, "executionProfile must not be null");
            Objects.requireNonNull(preset, "preset must not be null");
            presetInputs = List.copyOf(presetInputs == null ? List.of() : presetInputs);
            codexOptions = List.copyOf(codexOptions == null ? List.of() : codexOptions);
            additionalInstructions = additionalInstructions == null ? "" : additionalInstructions;
        }
    }

    public record PromptInput(String label, String value) {
        public PromptInput {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("label must not be blank");
            }
            value = value == null ? "" : value;
        }
    }

    public record CodexLaunchPlan(String runId,
                                  String storyId,
                                  ActiveProject activeProject,
                                  ExecutionProfile executionProfile,
                                  BuiltInPreset preset,
                                  List<String> command,
                                  Path processWorkingDirectory,
                                  String executionWorkingDirectory,
                                  String promptText) {
        public CodexLaunchPlan {
            if (runId == null || runId.isBlank()) {
                throw new IllegalArgumentException("runId must not be blank");
            }
            if (storyId == null || storyId.isBlank()) {
                throw new IllegalArgumentException("storyId must not be blank");
            }
            Objects.requireNonNull(activeProject, "activeProject must not be null");
            Objects.requireNonNull(executionProfile, "executionProfile must not be null");
            Objects.requireNonNull(preset, "preset must not be null");
            command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
            if (executionWorkingDirectory == null || executionWorkingDirectory.isBlank()) {
                throw new IllegalArgumentException("executionWorkingDirectory must not be blank");
            }
            Objects.requireNonNull(promptText, "promptText must not be null");
        }
    }

    public record CodexLaunchResult(boolean successful,
                                    CodexLaunchPlan launchPlan,
                                    String status,
                                    String startedAt,
                                    String endedAt,
                                    Long processId,
                                    Integer exitCode,
                                    String stdout,
                                    String stderr,
                                    String message,
                                    RunArtifacts artifacts) {
        public CodexLaunchResult {
            Objects.requireNonNull(launchPlan, "launchPlan must not be null");
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status must not be blank");
            }
            if (startedAt == null || startedAt.isBlank()) {
                throw new IllegalArgumentException("startedAt must not be blank");
            }
            if (endedAt == null || endedAt.isBlank()) {
                throw new IllegalArgumentException("endedAt must not be blank");
            }
            stdout = stdout == null ? "" : stdout;
            stderr = stderr == null ? "" : stderr;
            message = message == null ? "" : message;
            Objects.requireNonNull(artifacts, "artifacts must not be null");
        }
    }

    public record RunArtifacts(Path promptPath,
                               Path stdoutPath,
                               Path stderrPath,
                               Path structuredEventsPath,
                               Path summaryPath) {
        public RunArtifacts {
            Objects.requireNonNull(promptPath, "promptPath must not be null");
            Objects.requireNonNull(stdoutPath, "stdoutPath must not be null");
            Objects.requireNonNull(stderrPath, "stderrPath must not be null");
            Objects.requireNonNull(summaryPath, "summaryPath must not be null");
        }
    }

    private record AttemptSummaryArtifact(String runId,
                                          String storyId,
                                          String status,
                                          String startedAt,
                                          String endedAt,
                                          String profileType,
                                          String executionWorkingDirectory,
                                          Long processId,
                                          Integer exitCode,
                                          List<String> command,
                                          String message,
                                          LocalMetadataStorage.RunArtifactPaths artifactPaths) {
    }

    interface ProcessExecutor {
        ProcessExecution execute(CodexLaunchPlan launchPlan);
    }

    record ProcessExecution(Long processId,
                            Integer exitCode,
                            String stdout,
                            String stderr,
                            String failureMessage) {
        static ProcessExecution completed(long processId, int exitCode, String stdout, String stderr) {
            return new ProcessExecution(processId, exitCode, stdout, stderr, "");
        }

        static ProcessExecution failure(String failureMessage) {
            return new ProcessExecution(null, null, "", "", failureMessage);
        }

        boolean successful() {
            return !hasText(failureMessage) && exitCode != null && exitCode == 0;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    private static final class SystemProcessExecutor implements ProcessExecutor {
        @Override
        public ProcessExecution execute(CodexLaunchPlan launchPlan) {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            if (launchPlan.processWorkingDirectory() != null) {
                processBuilder.directory(launchPlan.processWorkingDirectory().toFile());
            }

            try {
                Process process = processBuilder.start();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> readFully(process.getInputStream())
                );
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> readFully(process.getErrorStream())
                );

                writePrompt(process, launchPlan.promptText());
                int exitCode = process.waitFor();

                return ProcessExecution.completed(
                        process.pid(),
                        exitCode,
                        stdoutFuture.join(),
                        stderrFuture.join()
                );
            } catch (IOException exception) {
                return ProcessExecution.failure(exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ProcessExecution.failure("Codex launch was interrupted.");
            }
        }

        private static void writePrompt(Process process, String promptText) throws IOException {
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(promptText.getBytes(StandardCharsets.UTF_8));
            }
        }

        private static String readFully(InputStream inputStream) {
            try (InputStream stream = inputStream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return "";
            }
        }
    }
}
