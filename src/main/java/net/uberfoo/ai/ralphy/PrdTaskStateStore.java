package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

@Component
public class PrdTaskStateStore {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final RalphPrdJsonMapper ralphPrdJsonMapper;
    private final RalphPrdJsonCompatibilityValidator compatibilityValidator;

    public PrdTaskStateStore() {
        this(new RalphPrdJsonMapper(), new RalphPrdJsonCompatibilityValidator());
    }

    PrdTaskStateStore(RalphPrdJsonMapper ralphPrdJsonMapper,
                      RalphPrdJsonCompatibilityValidator compatibilityValidator) {
        this.ralphPrdJsonMapper = ralphPrdJsonMapper;
        this.compatibilityValidator = compatibilityValidator;
    }

    public Optional<PrdTaskState> read(ActiveProject activeProject) throws IOException {
        Path taskStatePath = activeProject.activePrdJsonPath();
        if (!Files.exists(taskStatePath)) {
            return Optional.empty();
        }

        JsonNode rootNode = objectMapper.readTree(taskStatePath.toFile());
        if (rootNode.isObject() && rootNode.has("name") && rootNode.has("userStories")) {
            RalphPrdJsonDocument document = objectMapper.treeToValue(rootNode, RalphPrdJsonDocument.class);
            return Optional.of(ralphPrdJsonMapper.toTaskState(activeProject, document));
        }

        return Optional.of(objectMapper.treeToValue(rootNode, PrdTaskState.class));
    }

    public ImportedPrdJson readCompatibleImport(Path sourcePath, ActiveProject activeProject) throws IOException {
        Objects.requireNonNull(sourcePath, "sourcePath must not be null");
        Objects.requireNonNull(activeProject, "activeProject must not be null");

        Path normalizedSourcePath = sourcePath.toAbsolutePath().normalize();
        String rawJson = Files.readString(normalizedSourcePath, StandardCharsets.UTF_8);
        RalphPrdJsonCompatibilityValidator.ValidationReport validationReport = compatibilityValidator.validate(rawJson);
        if (!validationReport.valid()) {
            throw new IOException("Selected prd.json is incompatible: " + String.join(" | ", validationReport.errors()));
        }

        RalphPrdJsonDocument document = objectMapper.readValue(rawJson, RalphPrdJsonDocument.class);
        return new ImportedPrdJson(
                normalizedSourcePath,
                document,
                ralphPrdJsonMapper.toTaskState(activeProject, document)
        );
    }

    public void write(ActiveProject activeProject, PrdTaskState taskState) throws IOException {
        Files.createDirectories(activeProject.prdJsonDirectoryPath());
        Path taskStatePath = activeProject.activePrdJsonPath();
        Path temporaryTaskStatePath = taskStatePath.resolveSibling(taskStatePath.getFileName() + ".tmp");
        String activePrdMarkdown = Files.exists(activeProject.activePrdPath())
                ? Files.readString(activeProject.activePrdPath(), StandardCharsets.UTF_8)
                : "";
        RalphPrdJsonDocument exportDocument = ralphPrdJsonMapper.toDocument(activeProject, activePrdMarkdown, taskState);
        String exportJson = objectMapper.writeValueAsString(exportDocument);
        RalphPrdJsonCompatibilityValidator.ValidationReport validationReport = compatibilityValidator.validate(exportJson);
        if (!validationReport.valid()) {
            throw new IOException("Exported prd.json failed compatibility validation: "
                    + String.join(" | ", validationReport.errors()));
        }
        Files.writeString(temporaryTaskStatePath, exportJson, StandardCharsets.UTF_8);
        moveIntoPlace(temporaryTaskStatePath, taskStatePath);
    }

    public boolean isCompatibleExport(ActiveProject activeProject) {
        Path taskStatePath = activeProject.activePrdJsonPath();
        if (!Files.exists(taskStatePath)) {
            return false;
        }

        try {
            return compatibilityValidator.validate(Files.readString(taskStatePath, StandardCharsets.UTF_8)).valid();
        } catch (IOException exception) {
            return false;
        }
    }

    private void moveIntoPlace(Path temporaryPath, Path destinationPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    destinationPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record ImportedPrdJson(Path sourcePath, RalphPrdJsonDocument document, PrdTaskState taskState) {
        public ImportedPrdJson {
            Objects.requireNonNull(sourcePath, "sourcePath must not be null");
            Objects.requireNonNull(document, "document must not be null");
            Objects.requireNonNull(taskState, "taskState must not be null");
        }
    }
}
