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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class ExecutionAgentModelCatalogService {
    private static final long CODEX_MODEL_DISCOVERY_TIMEOUT_MILLIS = 4_000L;

    private final String codexCommand;
    private final boolean stubModelCatalog;
    private final AppServerProcessLauncher appServerProcessLauncher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ExecutionAgentModelCatalogService(
            @Value("${ralphy.codex.command:" + CodexCliSupport.DEFAULT_COMMAND + "}") String codexCommand,
            @Value("${ralphy.execution.model.catalog.stub:false}") boolean stubModelCatalog) {
        this(codexCommand, stubModelCatalog, new SystemAppServerProcessLauncher());
    }

    ExecutionAgentModelCatalogService(String codexCommand, AppServerProcessLauncher appServerProcessLauncher) {
        this(codexCommand, false, appServerProcessLauncher);
    }

    ExecutionAgentModelCatalogService(String codexCommand,
                                     boolean stubModelCatalog,
                                     AppServerProcessLauncher appServerProcessLauncher) {
        this.codexCommand = Objects.requireNonNull(codexCommand, "codexCommand must not be null");
        this.stubModelCatalog = stubModelCatalog;
        this.appServerProcessLauncher = Objects.requireNonNull(
                appServerProcessLauncher,
                "appServerProcessLauncher must not be null"
        );
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

        if (provider != ExecutionAgentProvider.CODEX) {
            return new ModelCatalog(
                    provider,
                    List.of(),
                    false,
                    provider.displayName() + " model discovery is not implemented yet."
            );
        }

        try {
            List<ModelOption> modelOptions = requestCodexModels();
            return new ModelCatalog(
                    provider,
                    modelOptions,
                    true,
                    modelOptions.isEmpty()
                            ? "No models were returned. Codex CLI default model will be used."
                            : "Loaded " + modelOptions.size() + " model options from Codex."
            );
        } catch (IOException exception) {
            return new ModelCatalog(
                    provider,
                    List.of(),
                    false,
                    "Unable to load Codex models dynamically: " + exception.getMessage()
            );
        }
    }

    private List<ModelOption> requestCodexModels() throws IOException {
        List<String> command = CodexCliSupport.buildNativeCommand(
                codexCommand,
                System.getenv(),
                List.of("app-server", "--listen", "stdio://")
        );
        if (stubModelCatalog) {
            return STUB_MODEL_OPTIONS;
        }

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
                    modelNode.path("hidden").asBoolean(false)
            ));
        }
        return models.stream()
                .sorted(Comparator.comparing(ModelOption::defaultModel).reversed()
                        .thenComparing(ModelOption::displayName))
                .toList();
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
                              boolean hidden) {
        public ModelOption {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("modelId must not be blank");
            }
            displayName = displayName == null ? modelId : displayName;
            description = description == null ? "" : description;
        }
    }

    private static final List<ModelOption> STUB_MODEL_OPTIONS = List.of(
            new ModelOption("gpt-5.4", "GPT-5.4", "Frontier model", true, false),
            new ModelOption("gpt-5.4-mini", "GPT-5.4 Mini", "Fast model", false, false)
    );

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
}
