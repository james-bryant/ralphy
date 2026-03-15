package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ActiveProject(Path repositoryPath) {
    private static final String RALPHY_DIRECTORY_NAME = ".ralph-tui";
    private static final String PROJECT_METADATA_FILE_NAME = "project-metadata.json";
    private static final String PRDS_DIRECTORY_NAME = "prds";
    private static final String ACTIVE_PRD_FILE_NAME = "active-prd.md";
    private static final String PRD_JSON_DIRECTORY_NAME = "prd-json";
    private static final String ACTIVE_PRD_JSON_FILE_NAME = "prd.json";
    private static final String PROMPTS_DIRECTORY_NAME = "prompts";
    private static final String LOGS_DIRECTORY_NAME = "logs";
    private static final String ARTIFACTS_DIRECTORY_NAME = "artifacts";

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

    public Path prdsDirectoryPath() {
        return ralphyDirectoryPath().resolve(PRDS_DIRECTORY_NAME);
    }

    public Path activePrdPath() {
        return prdsDirectoryPath().resolve(ACTIVE_PRD_FILE_NAME);
    }

    public Path prdJsonDirectoryPath() {
        return ralphyDirectoryPath().resolve(PRD_JSON_DIRECTORY_NAME);
    }

    public Path activePrdJsonPath() {
        return prdJsonDirectoryPath().resolve(ACTIVE_PRD_JSON_FILE_NAME);
    }

    public Path promptsDirectoryPath() {
        return ralphyDirectoryPath().resolve(PROMPTS_DIRECTORY_NAME);
    }

    public Path logsDirectoryPath() {
        return ralphyDirectoryPath().resolve(LOGS_DIRECTORY_NAME);
    }

    public Path artifactsDirectoryPath() {
        return ralphyDirectoryPath().resolve(ARTIFACTS_DIRECTORY_NAME);
    }

    public List<Path> managedDirectories() {
        return List.of(
                ralphyDirectoryPath(),
                prdsDirectoryPath(),
                prdJsonDirectoryPath(),
                promptsDirectoryPath(),
                logsDirectoryPath(),
                artifactsDirectoryPath()
        );
    }

    public ProjectStoragePaths storagePaths() {
        return new ProjectStoragePaths(
                ralphyDirectoryPath().toString(),
                prdsDirectoryPath().toString(),
                activePrdPath().toString(),
                prdJsonDirectoryPath().toString(),
                activePrdJsonPath().toString(),
                promptsDirectoryPath().toString(),
                logsDirectoryPath().toString(),
                artifactsDirectoryPath().toString()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectStoragePaths(String ralphyDirectoryPath,
                                      String prdsDirectoryPath,
                                      String activePrdPath,
                                      String prdJsonDirectoryPath,
                                      String activePrdJsonPath,
                                      String promptsDirectoryPath,
                                      String logsDirectoryPath,
                                      String artifactsDirectoryPath) {
    }
}
