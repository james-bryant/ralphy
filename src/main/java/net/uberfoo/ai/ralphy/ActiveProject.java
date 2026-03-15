package net.uberfoo.ai.ralphy;

import java.nio.file.Path;
import java.util.Objects;

public record ActiveProject(Path repositoryPath) {
    private static final String RALPHY_DIRECTORY_NAME = ".ralph-tui";
    private static final String PROJECT_METADATA_FILE_NAME = "project-metadata.json";

    public ActiveProject {
        Objects.requireNonNull(repositoryPath, "repositoryPath must not be null");
        repositoryPath = repositoryPath.toAbsolutePath().normalize();
    }

    public String displayName() {
        Path fileName = repositoryPath.getFileName();
        return fileName == null ? repositoryPath.toString() : fileName.toString();
    }

    public String displayPath() {
        return repositoryPath.toString();
    }

    public Path ralphyDirectoryPath() {
        return repositoryPath.resolve(RALPHY_DIRECTORY_NAME);
    }

    public Path projectMetadataPath() {
        return ralphyDirectoryPath().resolve(PROJECT_METADATA_FILE_NAME);
    }
}
