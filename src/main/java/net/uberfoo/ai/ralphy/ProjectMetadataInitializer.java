package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.function.Function;

@Component
public class ProjectMetadataInitializer {
    private static final int SCHEMA_VERSION = 8;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeMetadata(ActiveProject activeProject) throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null ? null : existingMetadata.prdInterviewDraft(),
                existingMetadata == null ? null : existingMetadata.prdPlanningSession(),
                existingMetadata == null ? null : existingMetadata.markdownPrdExchangeLocations(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public void writeNativeWindowsPreflight(ActiveProject activeProject,
                                            NativeWindowsPreflightReport nativeWindowsPreflight) throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                nativeWindowsPreflight,
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null ? null : existingMetadata.prdInterviewDraft(),
                existingMetadata == null ? null : existingMetadata.prdPlanningSession(),
                existingMetadata == null ? null : existingMetadata.markdownPrdExchangeLocations(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public void writeWslPreflight(ActiveProject activeProject,
                                  WslPreflightReport wslPreflight) throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                wslPreflight,
                existingMetadata == null ? null : existingMetadata.prdInterviewDraft(),
                existingMetadata == null ? null : existingMetadata.prdPlanningSession(),
                existingMetadata == null ? null : existingMetadata.markdownPrdExchangeLocations(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public void writePrdInterviewDraft(ActiveProject activeProject,
                                       PrdInterviewDraft prdInterviewDraft) throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                prdInterviewDraft,
                existingMetadata == null ? null : existingMetadata.prdPlanningSession(),
                existingMetadata == null ? null : existingMetadata.markdownPrdExchangeLocations(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public void writePrdPlanningSession(ActiveProject activeProject,
                                        PrdPlanningSession prdPlanningSession) throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null ? null : existingMetadata.prdInterviewDraft(),
                prdPlanningSession,
                existingMetadata == null ? null : existingMetadata.markdownPrdExchangeLocations(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public void writeMarkdownPrdExchangeLocations(ActiveProject activeProject,
                                                  MarkdownPrdExchangeLocations markdownPrdExchangeLocations)
            throws IOException {
        String timestamp = Instant.now().toString();
        writeDocument(activeProject, existingMetadata -> new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                activeProject.projectMetadataPath().toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null ? null : existingMetadata.prdInterviewDraft(),
                existingMetadata == null ? null : existingMetadata.prdPlanningSession(),
                markdownPrdExchangeLocations,
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        ));
    }

    public java.util.Optional<NativeWindowsPreflightReport> readNativeWindowsPreflight(ActiveProject activeProject)
            throws IOException {
        ProjectMetadataDocument existingMetadata = readExistingMetadata(activeProject.projectMetadataPath());
        if (existingMetadata == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(existingMetadata.nativeWindowsPreflight());
    }

    public java.util.Optional<WslPreflightReport> readWslPreflight(ActiveProject activeProject) throws IOException {
        ProjectMetadataDocument existingMetadata = readExistingMetadata(activeProject.projectMetadataPath());
        if (existingMetadata == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(existingMetadata.wslPreflight());
    }

    public java.util.Optional<PrdInterviewDraft> readPrdInterviewDraft(ActiveProject activeProject) throws IOException {
        ProjectMetadataDocument existingMetadata = readExistingMetadata(activeProject.projectMetadataPath());
        if (existingMetadata == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(existingMetadata.prdInterviewDraft());
    }

    public java.util.Optional<PrdPlanningSession> readPrdPlanningSession(ActiveProject activeProject) throws IOException {
        ProjectMetadataDocument existingMetadata = readExistingMetadata(activeProject.projectMetadataPath());
        if (existingMetadata == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(existingMetadata.prdPlanningSession());
    }

    public java.util.Optional<MarkdownPrdExchangeLocations> readMarkdownPrdExchangeLocations(ActiveProject activeProject)
            throws IOException {
        ProjectMetadataDocument existingMetadata = readExistingMetadata(activeProject.projectMetadataPath());
        if (existingMetadata == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.ofNullable(existingMetadata.markdownPrdExchangeLocations());
    }

    private ProjectMetadataDocument readExistingMetadata(Path metadataPath) throws IOException {
        if (!Files.exists(metadataPath)) {
            return null;
        }

        return objectMapper.readValue(metadataPath.toFile(), ProjectMetadataDocument.class);
    }

    private void writeDocument(ActiveProject activeProject,
                               Function<ProjectMetadataDocument, ProjectMetadataDocument> documentFactory)
            throws IOException {
        Files.createDirectories(activeProject.ralphyDirectoryPath());

        Path metadataPath = activeProject.projectMetadataPath();
        ProjectMetadataDocument existingMetadata = readExistingMetadata(metadataPath);
        ProjectMetadataDocument metadataDocument = documentFactory.apply(existingMetadata);
        persistMetadata(metadataPath, metadataDocument);
    }

    private void persistMetadata(Path metadataPath, ProjectMetadataDocument metadataDocument) throws IOException {
        Path temporaryMetadataPath = metadataPath.resolveSibling(metadataPath.getFileName() + ".tmp");
        objectMapper.writeValue(temporaryMetadataPath.toFile(), metadataDocument);
        moveIntoPlace(temporaryMetadataPath, metadataPath);
    }

    private void moveIntoPlace(Path temporaryMetadataPath, Path metadataPath) throws IOException {
        try {
            Files.move(
                    temporaryMetadataPath,
                    metadataPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryMetadataPath, metadataPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectMetadataDocument(int schemaVersion,
                                           String projectName,
                                           String repositoryPath,
                                           String projectMetadataPath,
                                           ActiveProject.ProjectStoragePaths storage,
                                           NativeWindowsPreflightReport nativeWindowsPreflight,
                                           WslPreflightReport wslPreflight,
                                           PrdInterviewDraft prdInterviewDraft,
                                           PrdPlanningSession prdPlanningSession,
                                           MarkdownPrdExchangeLocations markdownPrdExchangeLocations,
                                           String createdAt,
                                           String updatedAt) {
    }
}
