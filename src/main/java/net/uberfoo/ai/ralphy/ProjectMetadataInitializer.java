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

@Component
public class ProjectMetadataInitializer {
    private static final int SCHEMA_VERSION = 5;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void writeMetadata(ActiveProject activeProject) throws IOException {
        Files.createDirectories(activeProject.ralphyDirectoryPath());

        Path metadataPath = activeProject.projectMetadataPath();
        String timestamp = Instant.now().toString();
        ProjectMetadataDocument existingMetadata = readExistingMetadata(metadataPath);
        ProjectMetadataDocument metadataDocument = new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                metadataPath.toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        );
        persistMetadata(metadataPath, metadataDocument);
    }

    public void writeNativeWindowsPreflight(ActiveProject activeProject,
                                            NativeWindowsPreflightReport nativeWindowsPreflight) throws IOException {
        Files.createDirectories(activeProject.ralphyDirectoryPath());

        Path metadataPath = activeProject.projectMetadataPath();
        String timestamp = Instant.now().toString();
        ProjectMetadataDocument existingMetadata = readExistingMetadata(metadataPath);
        ProjectMetadataDocument metadataDocument = new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                metadataPath.toString(),
                activeProject.storagePaths(),
                nativeWindowsPreflight,
                existingMetadata == null ? null : existingMetadata.wslPreflight(),
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        );
        persistMetadata(metadataPath, metadataDocument);
    }

    public void writeWslPreflight(ActiveProject activeProject,
                                  WslPreflightReport wslPreflight) throws IOException {
        Files.createDirectories(activeProject.ralphyDirectoryPath());

        Path metadataPath = activeProject.projectMetadataPath();
        String timestamp = Instant.now().toString();
        ProjectMetadataDocument existingMetadata = readExistingMetadata(metadataPath);
        ProjectMetadataDocument metadataDocument = new ProjectMetadataDocument(
                SCHEMA_VERSION,
                activeProject.displayName(),
                activeProject.displayPath(),
                metadataPath.toString(),
                activeProject.storagePaths(),
                existingMetadata == null ? null : existingMetadata.nativeWindowsPreflight(),
                wslPreflight,
                existingMetadata == null || existingMetadata.createdAt() == null
                        ? timestamp
                        : existingMetadata.createdAt(),
                timestamp
        );
        persistMetadata(metadataPath, metadataDocument);
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

    private ProjectMetadataDocument readExistingMetadata(Path metadataPath) throws IOException {
        if (!Files.exists(metadataPath)) {
            return null;
        }

        return objectMapper.readValue(metadataPath.toFile(), ProjectMetadataDocument.class);
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
                                           String createdAt,
                                           String updatedAt) {
    }
}
