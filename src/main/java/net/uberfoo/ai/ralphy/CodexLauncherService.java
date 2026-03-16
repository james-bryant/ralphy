package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    private final String codexCommand;

    @Autowired
    public CodexLauncherService(LocalMetadataStorage localMetadataStorage,
                                @Value("${ralphy.codex.command:codex}") String codexCommand) {
        this(localMetadataStorage,
                Clock.systemUTC(),
                () -> UUID.randomUUID().toString(),
                new SystemProcessExecutor(),
                codexCommand);
    }

    CodexLauncherService(LocalMetadataStorage localMetadataStorage,
                         Clock clock,
                         Supplier<String> runIdGenerator,
                         ProcessExecutor processExecutor) {
        this(localMetadataStorage, clock, runIdGenerator, processExecutor, "codex");
    }

    CodexLauncherService(LocalMetadataStorage localMetadataStorage,
                         Clock clock,
                         Supplier<String> runIdGenerator,
                         ProcessExecutor processExecutor,
                         String codexCommand) {
        this.localMetadataStorage = Objects.requireNonNull(localMetadataStorage, "localMetadataStorage must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.runIdGenerator = Objects.requireNonNull(runIdGenerator, "runIdGenerator must not be null");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.codexCommand = Objects.requireNonNull(codexCommand, "codexCommand must not be null");
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
        return launch(request, RunOutputListener.noop());
    }

    public CodexLaunchResult launch(CodexLaunchRequest request, RunOutputListener runOutputListener) {
        return launch(buildLaunch(request), runOutputListener);
    }

    CodexLaunchResult launch(CodexLaunchPlan launchPlan) {
        return launch(launchPlan, RunOutputListener.noop());
    }

    CodexLaunchResult launch(CodexLaunchPlan launchPlan, RunOutputListener runOutputListener) {
        Objects.requireNonNull(launchPlan, "launchPlan must not be null");
        RunOutputListener listener = runOutputListener == null ? RunOutputListener.noop() : runOutputListener;

        String startedAt = Instant.now(clock).toString();
        persistRunningMetadata(launchPlan, startedAt);
        safeNotifyLaunchStarted(listener, launchPlan);
        ProcessExecution processExecution = processExecutor.execute(launchPlan, listener);
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
        String assistantSummary = readTextArtifact(artifacts.assistantSummaryPath());

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
                assistantSummary,
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
                                launchResult.artifacts().summaryPath().toString(),
                                launchResult.artifacts().assistantSummaryPath().toString()
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
            Path assistantSummaryPath = artifactDirectory.resolve("assistant-summary.txt");
            writeTextArtifact(assistantSummaryPath, extractFinalAssistantSummary(stdout));
            RunArtifacts artifacts = new RunArtifacts(
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    structuredEventsPath,
                    summaryPath,
                    assistantSummaryPath
            );
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
                            summaryPath.toString(),
                            assistantSummaryPath.toString()
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

    private String extractFinalAssistantSummary(String stdout) {
        if (!hasText(stdout)) {
            return "";
        }

        List<String> completeCandidates = new ArrayList<>();
        StringBuilder deltaCandidate = new StringBuilder();
        for (String line : stdout.lines().toList()) {
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                continue;
            }

            JsonNode eventNode;
            try {
                eventNode = objectMapper.readTree(trimmedLine);
            } catch (IOException ignored) {
                continue;
            }

            String completeCandidate = extractCompleteAssistantText(eventNode);
            if (hasText(completeCandidate)) {
                completeCandidates.add(completeCandidate.trim());
            }

            String deltaText = extractAssistantDeltaText(eventNode);
            if (hasText(deltaText)) {
                deltaCandidate.append(deltaText);
            }
        }

        if (!completeCandidates.isEmpty()) {
            return normalizeSummaryText(completeCandidates.get(completeCandidates.size() - 1));
        }

        String deltaSummary = normalizeSummaryText(deltaCandidate.toString());
        if (hasText(deltaSummary)) {
            return deltaSummary;
        }

        return extractTrailingPlainTextBlock(stdout);
    }

    private String extractCompleteAssistantText(JsonNode rootNode) {
        List<String> candidates = new ArrayList<>();
        collectAssistantTextCandidates(rootNode, false, false, candidates, 0);
        return candidates.isEmpty() ? "" : candidates.get(candidates.size() - 1);
    }

    private String extractAssistantDeltaText(JsonNode rootNode) {
        List<String> deltaCandidates = new ArrayList<>();
        collectAssistantTextCandidates(rootNode, false, true, deltaCandidates, 0);
        if (deltaCandidates.isEmpty()) {
            return "";
        }
        return String.join("", deltaCandidates);
    }

    private void collectAssistantTextCandidates(JsonNode node,
                                                boolean assistantContext,
                                                boolean deltaOnly,
                                                List<String> candidates,
                                                int depth) {
        if (node == null || node.isNull() || depth > 8) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode childNode : node) {
                collectAssistantTextCandidates(childNode, assistantContext, deltaOnly, candidates, depth + 1);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        boolean currentAssistantContext = assistantContext || isAssistantContext(node);
        if (currentAssistantContext) {
            if (deltaOnly) {
                addCandidate(candidates, readPreferredText(node, List.of("delta", "text_delta", "output_text_delta")));
            } else {
                addCandidate(candidates, readPreferredText(node, List.of(
                        "assistant_summary",
                        "summary",
                        "final_summary",
                        "output_text",
                        "text",
                        "message"
                )));
                addCandidate(candidates, extractContentText(node.path("content")));
                addCandidate(candidates, extractContentText(node.path("output")));
            }
        }

        node.fields().forEachRemaining(entry ->
                collectAssistantTextCandidates(entry.getValue(), currentAssistantContext, deltaOnly, candidates, depth + 1)
        );
    }

    private boolean isAssistantContext(JsonNode node) {
        String role = node.path("role").asText("");
        if ("assistant".equalsIgnoreCase(role)) {
            return true;
        }

        String type = node.path("type").asText("");
        String event = node.path("event").asText("");
        String kind = (type + " " + event).toLowerCase();
        return kind.contains("assistant")
                || kind.contains("message")
                || kind.contains("response")
                || kind.contains("summary");
    }

    private String readPreferredText(JsonNode node, List<String> preferredFields) {
        for (String fieldName : preferredFields) {
            if (!node.has(fieldName)) {
                continue;
            }

            String extractedText = extractContentText(node.get(fieldName));
            if (hasText(extractedText)) {
                return extractedText;
            }
        }
        return "";
    }

    private String extractContentText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode childNode : node) {
                addCandidate(parts, extractContentText(childNode));
            }
            return String.join(System.lineSeparator(), parts);
        }

        if (!node.isObject()) {
            return "";
        }

        String directText = readPreferredText(node, List.of("text", "value", "message", "content", "output_text"));
        if (hasText(directText)) {
            return directText;
        }

        List<String> nestedParts = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey().toLowerCase();
            if ("type".equals(fieldName)
                    || "event".equals(fieldName)
                    || "role".equals(fieldName)
                    || "id".equals(fieldName)
                    || "status".equals(fieldName)) {
                return;
            }
            addCandidate(nestedParts, extractContentText(entry.getValue()));
        });
        return String.join(System.lineSeparator(), nestedParts);
    }

    private String normalizeSummaryText(String value) {
        if (!hasText(value)) {
            return "";
        }

        return value
                .replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String extractTrailingPlainTextBlock(String stdout) {
        List<String> trailingLines = new ArrayList<>();
        List<String> lines = stdout.lines().toList();
        for (int index = lines.size() - 1; index >= 0; index--) {
            String line = lines.get(index);
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                if (!trailingLines.isEmpty()) {
                    break;
                }
                continue;
            }

            if (isStructuredEventLine(trimmedLine)) {
                if (!trailingLines.isEmpty()) {
                    break;
                }
                continue;
            }

            trailingLines.add(0, line);
        }

        return normalizeSummaryText(String.join(System.lineSeparator(), trailingLines));
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

    private String readTextArtifact(Path targetPath) {
        try {
            return Files.readString(targetPath, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
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
        commandTokens.add(codexCommand);
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

    private void addCandidate(List<String> candidates, String value) {
        if (hasText(value)) {
            candidates.add(value.trim());
        }
    }

    private void safeNotifyLaunchStarted(RunOutputListener listener, CodexLaunchPlan launchPlan) {
        try {
            listener.onLaunchStarted(launchPlan);
        } catch (RuntimeException ignored) {
            // UI listeners should never break the launcher path.
        }
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
                                    String assistantSummary,
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
            assistantSummary = assistantSummary == null ? "" : assistantSummary;
            Objects.requireNonNull(artifacts, "artifacts must not be null");
        }
    }

    public record RunArtifacts(Path promptPath,
                               Path stdoutPath,
                               Path stderrPath,
                               Path structuredEventsPath,
                               Path summaryPath,
                               Path assistantSummaryPath) {
        public RunArtifacts {
            Objects.requireNonNull(promptPath, "promptPath must not be null");
            Objects.requireNonNull(stdoutPath, "stdoutPath must not be null");
            Objects.requireNonNull(stderrPath, "stderrPath must not be null");
            Objects.requireNonNull(summaryPath, "summaryPath must not be null");
            Objects.requireNonNull(assistantSummaryPath, "assistantSummaryPath must not be null");
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

        default ProcessExecution execute(CodexLaunchPlan launchPlan, RunOutputListener runOutputListener) {
            return execute(launchPlan);
        }
    }

    public interface RunOutputListener {
        RunOutputListener NO_OP = new RunOutputListener() {
        };

        default void onLaunchStarted(CodexLaunchPlan launchPlan) {
        }

        default void onStdout(String text) {
        }

        default void onStderr(String text) {
        }

        static RunOutputListener noop() {
            return NO_OP;
        }
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
            return execute(launchPlan, RunOutputListener.noop());
        }

        @Override
        public ProcessExecution execute(CodexLaunchPlan launchPlan, RunOutputListener runOutputListener) {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            if (launchPlan.processWorkingDirectory() != null) {
                processBuilder.directory(launchPlan.processWorkingDirectory().toFile());
            }

            try {
                Process process = processBuilder.start();
                StringBuilder stdoutBuffer = new StringBuilder();
                StringBuilder stderrBuffer = new StringBuilder();
                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> readFully(process.getInputStream(), stdoutBuffer, runOutputListener::onStdout)
                );
                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> readFully(process.getErrorStream(), stderrBuffer, runOutputListener::onStderr)
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

        private static String readFully(InputStream inputStream,
                                        StringBuilder buffer,
                                        java.util.function.Consumer<String> consumer) {
            try (InputStream stream = inputStream;
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                char[] chunk = new char[1024];
                int readCount;
                while ((readCount = reader.read(chunk)) >= 0) {
                    String textChunk = new String(chunk, 0, readCount);
                    synchronized (buffer) {
                        buffer.append(textChunk);
                    }
                    safeNotify(consumer, textChunk);
                }
                synchronized (buffer) {
                    return buffer.toString();
                }
            } catch (IOException ignored) {
                return "";
            }
        }

        private static void safeNotify(java.util.function.Consumer<String> consumer, String chunk) {
            try {
                consumer.accept(chunk);
            } catch (RuntimeException ignored) {
                // UI listeners should never break process output capture.
            }
        }
    }
}
