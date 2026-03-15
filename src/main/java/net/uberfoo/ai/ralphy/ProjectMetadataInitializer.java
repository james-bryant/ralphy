package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

@Component
public class ProjectMetadataInitializer {
    private static final int SCHEMA_VERSION = 1;

    public void initializeMetadata(ActiveProject activeProject) throws IOException {
        Files.createDirectories(activeProject.ralphyDirectoryPath());
        Files.writeString(
                activeProject.projectMetadataPath(),
                metadataDocument(activeProject),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
        );
    }

    private String metadataDocument(ActiveProject activeProject) {
        return """
                {
                  "schemaVersion": %d,
                  "projectName": "%s",
                  "repositoryPath": "%s",
                  "createdAt": "%s"
                }
                """.formatted(
                SCHEMA_VERSION,
                escapeJson(activeProject.displayName()),
                escapeJson(activeProject.displayPath()),
                Instant.now().toString()
        );
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
