package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class ActiveProjectService {
    private ActiveProject activeProject;

    public synchronized Optional<ActiveProject> activeProject() {
        return Optional.ofNullable(activeProject);
    }

    public synchronized SelectionResult openRepository(Path selectedDirectory) {
        Path normalizedDirectory = selectedDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedDirectory)) {
            return SelectionResult.failure("Selected path is not a folder: " + normalizedDirectory);
        }

        if (!Files.exists(normalizedDirectory.resolve(".git"))) {
            return SelectionResult.failure("Selected folder is not a Git repository: " + normalizedDirectory);
        }

        activeProject = new ActiveProject(normalizedDirectory);
        return SelectionResult.success(activeProject);
    }

    public record SelectionResult(boolean successful, ActiveProject activeProject, String message) {
        private static SelectionResult success(ActiveProject activeProject) {
            return new SelectionResult(true, activeProject, "");
        }

        private static SelectionResult failure(String message) {
            return new SelectionResult(false, null, message);
        }
    }
}
