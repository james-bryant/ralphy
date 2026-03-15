package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class ActiveProjectService {
    private final GitRepositoryInitializer gitRepositoryInitializer;
    private final LocalMetadataStorage localMetadataStorage;
    private final ProjectMetadataInitializer projectMetadataInitializer;
    private final ProjectStorageInitializer projectStorageInitializer;
    private ActiveProject activeProject;

    public ActiveProjectService(GitRepositoryInitializer gitRepositoryInitializer,
                                ProjectMetadataInitializer projectMetadataInitializer,
                                ProjectStorageInitializer projectStorageInitializer,
                                LocalMetadataStorage localMetadataStorage) {
        this.gitRepositoryInitializer = gitRepositoryInitializer;
        this.projectMetadataInitializer = projectMetadataInitializer;
        this.projectStorageInitializer = projectStorageInitializer;
        this.localMetadataStorage = localMetadataStorage;
        this.localMetadataStorage.startSession();
    }

    public synchronized Optional<ActiveProject> activeProject() {
        return Optional.ofNullable(activeProject);
    }

    public synchronized ProjectActivationResult openRepository(Path selectedDirectory) {
        Path normalizedDirectory = selectedDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDirectory)) {
            return ProjectActivationResult.failure("Selected path is not a folder: " + normalizedDirectory);
        }

        if (!Files.exists(normalizedDirectory.resolve(".git"))) {
            return ProjectActivationResult.failure("Selected folder is not a Git repository: " + normalizedDirectory);
        }

        return activateProject(normalizedDirectory);
    }

    public synchronized ProjectActivationResult createRepository(Path parentDirectory, String projectDirectoryName) {
        if (parentDirectory == null) {
            return ProjectActivationResult.failure("Select a parent folder for the new repository.");
        }

        Path normalizedParentDirectory = parentDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedParentDirectory)) {
            return ProjectActivationResult.failure("Selected parent path is not a folder: " + normalizedParentDirectory);
        }

        String normalizedProjectDirectoryName = normalizeProjectDirectoryName(projectDirectoryName);
        if (normalizedProjectDirectoryName.isBlank()) {
            return ProjectActivationResult.failure("Enter a project folder name.");
        }

        if (!isSingleDirectoryName(normalizedProjectDirectoryName)) {
            return ProjectActivationResult.failure("Project folder name must be a single folder name.");
        }

        Path repositoryDirectory = normalizedParentDirectory.resolve(normalizedProjectDirectoryName).normalize();
        if (!normalizedParentDirectory.equals(repositoryDirectory.getParent())) {
            return ProjectActivationResult.failure("Project folder name must stay within the selected parent folder.");
        }

        if (Files.exists(repositoryDirectory)) {
            return ProjectActivationResult.failure("Project folder already exists: " + repositoryDirectory);
        }

        try {
            Files.createDirectory(repositoryDirectory);
        } catch (IOException exception) {
            return ProjectActivationResult.failure("Unable to create project folder: " + repositoryDirectory
                    + " (" + exception.getMessage() + ")");
        }

        GitRepositoryInitializer.InitializationResult initializationResult =
                gitRepositoryInitializer.initializeRepository(repositoryDirectory);
        if (!initializationResult.successful()) {
            return ProjectActivationResult.failure(rollbackNewRepository(
                    repositoryDirectory,
                    initializationResult.message()
            ));
        }

        ProjectActivationResult activationResult = activateProject(new ActiveProject(repositoryDirectory));
        if (!activationResult.successful()) {
            return ProjectActivationResult.failure(rollbackNewRepository(
                    repositoryDirectory,
                    activationResult.message()
            ));
        }

        return activationResult;
    }

    private ProjectActivationResult activateProject(Path repositoryDirectory) {
        return activateProject(new ActiveProject(repositoryDirectory));
    }

    private ProjectActivationResult activateProject(ActiveProject candidateProject) {
        try {
            projectStorageInitializer.ensureStorageDirectories(candidateProject);
            projectMetadataInitializer.writeMetadata(candidateProject);
        } catch (IOException exception) {
            return ProjectActivationResult.failure("Unable to prepare project storage: " + exception.getMessage());
        }

        activeProject = candidateProject;
        localMetadataStorage.recordProjectActivation(candidateProject);
        return ProjectActivationResult.success(candidateProject);
    }

    private boolean isSingleDirectoryName(String projectDirectoryName) {
        try {
            Path path = Path.of(projectDirectoryName).normalize();
            return !path.isAbsolute()
                    && path.getNameCount() == 1
                    && !projectDirectoryName.equals(".")
                    && !projectDirectoryName.equals("..");
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private String normalizeProjectDirectoryName(String projectDirectoryName) {
        return projectDirectoryName == null ? "" : projectDirectoryName.trim();
    }

    private String rollbackNewRepository(Path repositoryDirectory, String failureMessage) {
        try {
            deleteRecursively(repositoryDirectory);
            return failureMessage;
        } catch (IOException rollbackException) {
            return failureMessage + " Cleanup also failed for " + repositoryDirectory + ": "
                    + rollbackException.getMessage();
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException exception) {
                            throw new RecursiveDeletionException(exception);
                        }
                    });
        } catch (RecursiveDeletionException exception) {
            throw exception.ioException();
        }
    }

    public record ProjectActivationResult(boolean successful, ActiveProject activeProject, String message) {
        private static ProjectActivationResult success(ActiveProject activeProject) {
            return new ProjectActivationResult(true, activeProject, "");
        }

        private static ProjectActivationResult failure(String message) {
            return new ProjectActivationResult(false, null, message);
        }
    }

    private static final class RecursiveDeletionException extends RuntimeException {
        private final IOException ioException;

        private RecursiveDeletionException(IOException ioException) {
            this.ioException = ioException;
        }

        private IOException ioException() {
            return ioException;
        }
    }
}
