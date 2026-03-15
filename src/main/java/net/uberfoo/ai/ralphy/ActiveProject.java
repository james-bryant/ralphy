package net.uberfoo.ai.ralphy;

import java.nio.file.Path;
import java.util.Objects;

public record ActiveProject(Path repositoryPath) {
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
}
