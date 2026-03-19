package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExecutionAgentModelCatalogService {
    private static final long CODEX_MODEL_DISCOVERY_TIMEOUT_MILLIS = 12_000L;
    private static final Pattern MODEL_TOKEN_PATTERN = Pattern.compile(
            "\\b(?:gpt(?:-[a-z0-9.]+)+|claude(?:-[a-z0-9.]+)+|gemini(?:-[a-z0-9.]+)+|o[1-9](?:-[a-z0-9.]+)*)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUOTED_CHOICE_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final List<String> DEFAULT_THINKING_LEVELS = List.of("low", "medium", "high", "xhigh");

    private final String codexCommand;
    private final String copilotCommand;
    private final boolean stubModelCatalog;
    private final AppServerProcessLauncher appServerProcessLauncher;
    private final TextCommandExecutor textCommandExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ExecutionAgentModelCatalogService(
            @Value("${ralphy.codex.command:" + CodexCliSupport.DEFAULT_COMMAND + "}") String codexCommand,
            @Value("${ralphy.copilot.command:" + CopilotCliSupport.DEFAULT_COMMAND + "}") String copilotCommand,
            @Value("${ralphy.execution.model.catalog.stub:false}") boolean stubModelCatalog) {
        this(codexCommand, copilotCommand, stubModelCatalog,
                new SystemAppServerProcessLauncher(), new SystemTextCommandExecutor());
    }

    ExecutionAgentModelCatalogService(String codexCommand, AppServerProcessLauncher appServerProcessLauncher) {
        this(codexCommand, CopilotCliSupport.DEFAULT_COMMAND, false,
                appServerProcessLauncher, new SystemTextCommandExecutor());
    }

    ExecutionAgentModelCatalogService(String codexCommand,
                                      boolean stubModelCatalog,
                                      AppServerProcessLauncher appServerProcessLauncher) {
        this(codexCommand, CopilotCliSupport.DEFAULT_COMMAND, stubModelCatalog,
                appServerProcessLauncher, new SystemTextCommandExecutor());
    }

    ExecutionAgentModelCatalogService(String codexCommand,
                                      String copilotCommand,
                                      AppServerProcessLauncher appServerProcessLauncher,
                                      TextCommandExecutor textCommandExecutor) {
        this(codexCommand, copilotCommand, false, appServerProcessLauncher, textCommandExecutor);
    }

    ExecutionAgentModelCatalogService(String codexCommand,
                                      String copilotCommand,
                                      boolean stubModelCatalog,
                                      AppServerProcessLauncher appServerProcessLauncher,
                                      TextCommandExecutor textCommandExecutor) {
        this.codexCommand = Objects.requireNonNull(codexCommand, "codexCommand must not be null");
        this.copilotCommand = Objects.requireNonNull(copilotCommand, "copilotCommand must not be null");
        this.stubModelCatalog = stubModelCatalog;
        this.appServerProcessLauncher = Objects.requireNonNull(
                appServerProcessLauncher,
                "appServerProcessLauncher must not be null"
        );
        this.textCommandExecutor = Objects.requireNonNull(textCommandExecutor, "textCommandExecutor must not be null");
    }

    public List<ProviderSupport> providers() {
        List<ProviderSupport> providers = new ArrayList<>();
        for (ExecutionAgentProvider provider : ExecutionAgentProvider.values()) {
            if (provider.executionSupported()) {
                providers.add(new ProviderSupport(provider, provider.displayName(), true, ""));
                continue;
            }
            providers.add(new ProviderSupport(
                    provider,
                    provider.displayName(),
                    false,
                    "Provider integration is planned but not implemented yet."
            ));
        }
        return List.copyOf(providers);
    }

    public ModelCatalog modelsFor(ExecutionAgentProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");

        if (stubModelCatalog && provider.executionSupported()) {
            return new ModelCatalog(
                    provider,
                    provider == ExecutionAgentProvider.GITHUB_COPILOT ? COPILOT_STUB_MODEL_OPTIONS : STUB_MODEL_OPTIONS,
                    true,
                    "Loaded stub model catalog for " + provider.displayName() + "."
            );
        }

        try {
            List<ModelOption> modelOptions = switch (provider) {
                case CODEX -> requestCodexModels();
                case GITHUB_COPILOT -> requestCopilotModels();
                default -> List.of();
            };
            return new ModelCatalog(
                    provider,
                    modelOptions,
                    provider.executionSupported(),
                    modelOptions.isEmpty()
                            ? emptyModelMessage(provider)
                            : "Loaded " + modelOptions.size() + " model options from " + provider.displayName() + "."
            );
        } catch (IOException exception) {
            return new ModelCatalog(
                    provider,
                    List.of(),
                    false,
                    "Unable to load " + provider.displayName() + " models dynamically: " + exception.getMessage()
            );
        }
    }

    private List<ModelOption> requestCodexModels() throws IOException {
        List<String> command = CodexCliSupport.buildNativeCommand(
                codexCommand,
                System.getenv(),
                List.of("app-server", "--listen", "stdio://")
        );
        try (AppServerProcess appServerProcess = appServerProcessLauncher.start(command)) {
            BufferedWriter stdinWriter = appServerProcess.stdinWriter();
            stdinWriter.write("""
                    {"jsonrpc":"2.0","id":"init-1","method":"initialize","params":{"clientInfo":{"name":"ralphy","version":"0.1.0"}}}
                    """.trim());
            stdinWriter.newLine();
            stdinWriter.write("""
                    {"jsonrpc":"2.0","method":"initialized"}
                    """.trim());
            stdinWriter.newLine();
            stdinWriter.write("""
                    {"jsonrpc":"2.0","id":"model-list-1","method":"model/list","params":{"includeHidden":false}}
                    """.trim());
            stdinWriter.newLine();
            stdinWriter.flush();

            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CODEX_MODEL_DISCOVERY_TIMEOUT_MILLIS);
            BufferedReader stdoutReader = appServerProcess.stdoutReader();
            while (System.nanoTime() < deadlineNanos) {
                if (!stdoutReader.ready()) {
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting for Codex model list.", exception);
                    }
                    continue;
                }
                String line = stdoutReader.readLine();
                List<ModelOption> parsedModels = parseModelListResponseLine(line);
                if (parsedModels != null) {
                    return parsedModels;
                }
            }
            throw new IOException("Timed out while waiting for Codex model list.");
        }
    }

    private List<ModelOption> requestCopilotModels() throws IOException {
        CommandOutput commandOutput = textCommandExecutor.execute(
                CopilotCliSupport.buildNativeCommand(copilotCommand, System.getenv(), List.of("--no-color", "help")),
                CODEX_MODEL_DISCOVERY_TIMEOUT_MILLIS
        );
        if (!commandOutput.successful()) {
            throw new IOException(firstPopulated(commandOutput.stderr(), commandOutput.stdout()));
        }
        return parseCopilotModelHelp(firstPopulated(commandOutput.stdout(), commandOutput.stderr()));
    }

    private List<ModelOption> parseModelListResponseLine(String line) {
        List<ModelOption> models = new ArrayList<>();
        String trimmedLine = line == null ? "" : line.trim();
        if (!hasText(trimmedLine) || !trimmedLine.startsWith("{")) {
            return null;
        }

        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree(trimmedLine);
        } catch (IOException ignored) {
            return null;
        }

        if (!"model-list-1".equals(responseNode.path("id").asText())) {
            return null;
        }

        JsonNode dataNode = responseNode.path("result").path("data");
        if (!dataNode.isArray()) {
            return List.of();
        }
        for (JsonNode modelNode : dataNode) {
            String model = modelNode.path("model").asText("");
            if (!hasText(model)) {
                model = modelNode.path("id").asText("");
            }
            if (!hasText(model)) {
                continue;
            }
            models.add(new ModelOption(
                    model,
                    firstPopulated(modelNode.path("displayName").asText(""), model),
                    modelNode.path("description").asText(""),
                    modelNode.path("isDefault").asBoolean(false),
                    modelNode.path("hidden").asBoolean(false),
                    thinkingLevelsForCodexModel(modelNode)
            ));
        }
        return models.stream()
                .sorted(Comparator.comparing(ModelOption::defaultModel).reversed()
                        .thenComparing(ModelOption::displayName))
                .toList();
    }

    private List<ModelOption> parseCopilotModelHelp(String helpText) {
        if (!hasText(helpText)) {
            return List.of();
        }

        List<String> thinkingLevels = parseOptionChoices(helpText, "--reasoning-effort <level>");
        List<String> quotedModelChoices = parseOptionChoices(helpText, "--model <model>");
        if (!quotedModelChoices.isEmpty()) {
            List<String> supportedThinkingLevels = normalizeThinkingLevels(thinkingLevels);
            List<ModelOption> modelOptions = new ArrayList<>();
            for (int index = 0; index < quotedModelChoices.size(); index++) {
                String modelId = quotedModelChoices.get(index);
                modelOptions.add(new ModelOption(
                        modelId,
                        modelId,
                        "",
                        index == 0,
                        false,
                        supportedThinkingLevels
                ));
            }
            return modelOptions;
        }

        LinkedHashSet<String> models = new LinkedHashSet<>();
        String defaultModel = "";
        for (String line : helpText.lines().toList()) {
            Matcher matcher = MODEL_TOKEN_PATTERN.matcher(line);
            List<String> lineModels = new ArrayList<>();
            while (matcher.find()) {
                lineModels.add(matcher.group().toLowerCase());
            }
            if (lineModels.isEmpty()) {
                continue;
            }
            models.addAll(lineModels);
            if (!hasText(defaultModel) && line.toLowerCase().contains("default")) {
                defaultModel = lineModels.getFirst();
            }
        }

        String detectedDefaultModel = defaultModel;
        List<String> supportedThinkingLevels = normalizeThinkingLevels(thinkingLevels);
        return models.stream()
                .map(modelId -> new ModelOption(
                        modelId,
                        modelId,
                        "",
                        modelId.equals(detectedDefaultModel),
                        false,
                        supportedThinkingLevels
                ))
                .sorted(Comparator.comparing(ModelOption::defaultModel).reversed()
                        .thenComparing(ModelOption::displayName))
                .toList();
    }

    private List<String> parseOptionChoices(String helpText, String optionSignature) {
        List<String> choices = new ArrayList<>();
        boolean capturing = false;
        StringBuilder capturedBlock = new StringBuilder();
        for (String line : helpText.lines().toList()) {
            String normalizedLine = line == null ? "" : line;
            if (!capturing && normalizedLine.contains(optionSignature)) {
                capturing = true;
            }
            if (!capturing) {
                continue;
            }
            capturedBlock.append(normalizedLine.trim()).append(' ');
            if (normalizedLine.contains(")")) {
                Matcher matcher = QUOTED_CHOICE_PATTERN.matcher(capturedBlock.toString());
                while (matcher.find()) {
                    choices.add(matcher.group(1).trim());
                }
                return choices.stream().filter(this::hasText).distinct().toList();
            }
        }
        return List.of();
    }

    private List<String> thinkingLevelsForCodexModel(JsonNode modelNode) {
        List<String> explicitLevels = new ArrayList<>();
        collectTextArray(explicitLevels, modelNode.path("reasoningEfforts"));
        collectTextArray(explicitLevels, modelNode.path("thinkingLevels"));
        collectTextArray(explicitLevels, modelNode.path("capabilities").path("reasoningEfforts"));
        collectTextArray(explicitLevels, modelNode.path("capabilities").path("thinkingLevels"));
        return normalizeThinkingLevels(explicitLevels);
    }

    private void collectTextArray(List<String> values, JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return;
        }
        for (JsonNode valueNode : arrayNode) {
            String value = valueNode.asText("");
            if (hasText(value)) {
                values.add(value.trim().toLowerCase());
            }
        }
    }

    private List<String> normalizeThinkingLevels(List<String> thinkingLevels) {
        List<String> sourceLevels = thinkingLevels == null || thinkingLevels.isEmpty()
                ? DEFAULT_THINKING_LEVELS
                : thinkingLevels;
        return sourceLevels.stream()
                .filter(this::hasText)
                .map(level -> level.trim().toLowerCase())
                .distinct()
                .toList();
    }

    private String emptyModelMessage(ExecutionAgentProvider provider) {
        if (!provider.executionSupported()) {
            return provider.displayName() + " model discovery is not implemented yet.";
        }
        return provider.displayName() + " did not report any models. The CLI default model will be used.";
    }

    private String firstPopulated(String primary, String fallback) {
        if (hasText(primary)) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProviderSupport(ExecutionAgentProvider provider,
                                  String displayName,
                                  boolean enabled,
                                  String detail) {
        public ProviderSupport {
            Objects.requireNonNull(provider, "provider must not be null");
            displayName = displayName == null ? provider.displayName() : displayName;
            detail = detail == null ? "" : detail;
        }
    }

    public record ModelCatalog(ExecutionAgentProvider provider,
                               List<ModelOption> models,
                               boolean successful,
                               String message) {
        public ModelCatalog {
            Objects.requireNonNull(provider, "provider must not be null");
            models = List.copyOf(models == null ? List.of() : models);
            message = message == null ? "" : message;
        }
    }

    public record ModelOption(String modelId,
                              String displayName,
                              String description,
                              boolean defaultModel,
                              boolean hidden,
                              List<String> thinkingLevels) {
        public ModelOption {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("modelId must not be blank");
            }
            displayName = displayName == null ? modelId : displayName;
            description = description == null ? "" : description;
            thinkingLevels = List.copyOf(thinkingLevels == null ? DEFAULT_THINKING_LEVELS : thinkingLevels);
        }
    }

    private static final List<ModelOption> STUB_MODEL_OPTIONS = List.of(
            new ModelOption("gpt-5.4", "GPT-5.4", "Frontier model", true, false, DEFAULT_THINKING_LEVELS),
            new ModelOption("gpt-5.4-mini", "GPT-5.4 Mini", "Fast model", false, false, DEFAULT_THINKING_LEVELS)
    );

    private static final List<ModelOption> COPILOT_STUB_MODEL_OPTIONS = List.of(
            new ModelOption("claude-sonnet-4.5", "Claude Sonnet 4.5", "Default Copilot model", true, false,
                    DEFAULT_THINKING_LEVELS),
            new ModelOption("gpt-5.4", "GPT-5.4", "OpenAI GPT model", false, false, DEFAULT_THINKING_LEVELS)
    );

    interface TextCommandExecutor {
        CommandOutput execute(List<String> command, long timeoutMillis) throws IOException;
    }

    record CommandOutput(boolean successful, String stdout, String stderr) {
    }

    interface AppServerProcess extends AutoCloseable {
        BufferedWriter stdinWriter();

        BufferedReader stdoutReader();

        BufferedReader stderrReader();

        @Override
        void close() throws IOException;
    }

    interface AppServerProcessLauncher {
        AppServerProcess start(List<String> command) throws IOException;
    }

    private static final class SystemAppServerProcessLauncher implements AppServerProcessLauncher {
        @Override
        public AppServerProcess start(List<String> command) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            CodexCliSupport.prependNativePathEntries(processBuilder.environment());
            Process process = processBuilder.start();
            return new SystemAppServerProcess(process);
        }
    }

    private static final class SystemAppServerProcess implements AppServerProcess {
        private final Process process;
        private final BufferedWriter stdinWriter;
        private final BufferedReader stdoutReader;
        private final BufferedReader stderrReader;

        private SystemAppServerProcess(Process process) {
            this.process = Objects.requireNonNull(process, "process must not be null");
            this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        }

        @Override
        public BufferedWriter stdinWriter() {
            return stdinWriter;
        }

        @Override
        public BufferedReader stdoutReader() {
            return stdoutReader;
        }

        @Override
        public BufferedReader stderrReader() {
            return stderrReader;
        }

        @Override
        public void close() throws IOException {
            try {
                stdinWriter.close();
            } catch (IOException ignored) {
                // Best-effort close.
            }
            try {
                stdoutReader.close();
            } catch (IOException ignored) {
                // Best-effort close.
            }
            try {
                stderrReader.close();
            } catch (IOException ignored) {
                // Best-effort close.
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static final class SystemTextCommandExecutor implements TextCommandExecutor {
        @Override
        public CommandOutput execute(List<String> command, long timeoutMillis) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            CodexCliSupport.prependNativePathEntries(processBuilder.environment());
            Process process = processBuilder.start();
            try {
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                    // The command does not need stdin for discovery.
                }
                java.util.concurrent.CompletableFuture<String> stdoutFuture = java.util.concurrent.CompletableFuture
                        .supplyAsync(() -> readAll(process.getInputStream()));
                java.util.concurrent.CompletableFuture<String> stderrFuture = java.util.concurrent.CompletableFuture
                        .supplyAsync(() -> readAll(process.getErrorStream()));
                boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                    throw new IOException("Timed out while waiting for command output.");
                }
                String stdout;
                String stderr;
                try {
                    stdout = stdoutFuture.join();
                    stderr = stderrFuture.join();
                } catch (java.util.concurrent.CompletionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    throw new IOException("Unable to read command output.", cause);
                }
                return new CommandOutput(process.exitValue() == 0, stdout, stderr);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for command output.", exception);
            }
        }

        private String readAll(java.io.InputStream inputStream) {
            try (java.io.InputStream stream = inputStream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }
    }
}
