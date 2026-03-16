package net.uberfoo.ai.ralphy;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class PrdJsonFileChooser {
    private final Deque<Optional<Path>> queuedSelections = new ConcurrentLinkedDeque<>();

    public Optional<Path> chooseImportFile(Window ownerWindow, Path initialPath) {
        Optional<Path> queuedSelection = queuedSelections.pollFirst();
        if (queuedSelection != null) {
            return queuedSelection.map(path -> path.toAbsolutePath().normalize());
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import prd.json");
        fileChooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File initialDirectory = resolveInitialDirectory(initialPath);
        if (initialDirectory != null) {
            fileChooser.setInitialDirectory(initialDirectory);
        }
        fileChooser.setInitialFileName(resolveInitialFileName(initialPath));

        File selectedFile = fileChooser.showOpenDialog(ownerWindow);
        if (selectedFile == null) {
            return Optional.empty();
        }

        return Optional.of(selectedFile.toPath().toAbsolutePath().normalize());
    }

    void queueCancellationForTest() {
        queuedSelections.addLast(Optional.empty());
    }

    void queueSelectionForTest(Path selectedPath) {
        queuedSelections.addLast(Optional.of(selectedPath.toAbsolutePath().normalize()));
    }

    private File resolveInitialDirectory(Path requestedPath) {
        Path fallbackDirectory = defaultBrowseDirectory();
        Path candidatePath = requestedPath == null
                ? fallbackDirectory
                : requestedPath.toAbsolutePath().normalize();

        if (Files.isDirectory(candidatePath)) {
            return candidatePath.toFile();
        }

        Path parentDirectory = candidatePath.getParent();
        if (parentDirectory != null && Files.isDirectory(parentDirectory)) {
            return parentDirectory.toFile();
        }

        return Files.isDirectory(fallbackDirectory) ? fallbackDirectory.toFile() : null;
    }

    private String resolveInitialFileName(Path requestedPath) {
        if (requestedPath == null || Files.isDirectory(requestedPath)) {
            return "prd.json";
        }

        Path fileName = requestedPath.getFileName();
        if (fileName == null) {
            return "prd.json";
        }

        String value = fileName.toString().trim();
        return value.isEmpty() ? "prd.json" : value;
    }

    private Path defaultBrowseDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome).toAbsolutePath().normalize();
        }

        return Path.of(".").toAbsolutePath().normalize();
    }
}
