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
public class MarkdownPrdFileChooser {
    private final Deque<Optional<Path>> queuedSelections = new ConcurrentLinkedDeque<>();

    public Optional<Path> chooseImportFile(Window ownerWindow, Path initialPath) {
        return chooseFile(ownerWindow, initialPath, "Import Markdown PRD", true);
    }

    public Optional<Path> chooseExportFile(Window ownerWindow, Path initialPath) {
        return chooseFile(ownerWindow, initialPath, "Export Markdown PRD", false);
    }

    void queueCancellationForTest() {
        queuedSelections.addLast(Optional.empty());
    }

    void queueSelectionForTest(Path selectedPath) {
        queuedSelections.addLast(Optional.of(selectedPath.toAbsolutePath().normalize()));
    }

    private Optional<Path> chooseFile(Window ownerWindow, Path initialPath, String title, boolean openDialog) {
        Optional<Path> queuedSelection = queuedSelections.pollFirst();
        if (queuedSelection != null) {
            return queuedSelection.map(path -> path.toAbsolutePath().normalize());
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Markdown Files", "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File initialDirectory = resolveInitialDirectory(initialPath);
        if (initialDirectory != null) {
            fileChooser.setInitialDirectory(initialDirectory);
        }

        String initialFileName = resolveInitialFileName(initialPath);
        if (!openDialog && initialFileName != null && !initialFileName.isBlank()) {
            fileChooser.setInitialFileName(initialFileName);
        }

        File selectedFile = openDialog
                ? fileChooser.showOpenDialog(ownerWindow)
                : fileChooser.showSaveDialog(ownerWindow);
        if (selectedFile == null) {
            return Optional.empty();
        }

        return Optional.of(selectedFile.toPath().toAbsolutePath().normalize());
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
            return "active-prd.md";
        }

        Path fileName = requestedPath.getFileName();
        if (fileName == null) {
            return "active-prd.md";
        }

        String value = fileName.toString().trim();
        return value.isEmpty() ? "active-prd.md" : value;
    }

    private Path defaultBrowseDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome).toAbsolutePath().normalize();
        }

        return Path.of(".").toAbsolutePath().normalize();
    }
}
