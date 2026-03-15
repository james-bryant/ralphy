package net.uberfoo.ai.ralphy;

import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RepositoryDirectoryChooser {
    private final Deque<Optional<Path>> queuedSelections = new ConcurrentLinkedDeque<>();

    public Optional<Path> chooseRepository(Window ownerWindow, Path initialDirectory) {
        return chooseDirectory(ownerWindow, initialDirectory, "Open Existing Git Repository");
    }

    public Optional<Path> chooseParentDirectory(Window ownerWindow, Path initialDirectory) {
        return chooseDirectory(ownerWindow, initialDirectory, "Choose Parent Folder for New Git Repository");
    }

    private Optional<Path> chooseDirectory(Window ownerWindow, Path initialDirectory, String title) {
        Optional<Path> queuedSelection = queuedSelections.pollFirst();
        if (queuedSelection != null) {
            return queuedSelection.map(path -> path.toAbsolutePath().normalize());
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        File chooserInitialDirectory = resolveInitialDirectory(initialDirectory);
        if (chooserInitialDirectory != null) {
            directoryChooser.setInitialDirectory(chooserInitialDirectory);
        }

        File selectedDirectory = directoryChooser.showDialog(ownerWindow);
        if (selectedDirectory == null) {
            return Optional.empty();
        }

        return Optional.of(selectedDirectory.toPath().toAbsolutePath().normalize());
    }

    void queueCancellationForTest() {
        queuedSelections.addLast(Optional.empty());
    }

    void queueSelectionForTest(Path selectedDirectory) {
        queuedSelections.addLast(Optional.of(selectedDirectory.toAbsolutePath().normalize()));
    }

    private File resolveInitialDirectory(Path requestedDirectory) {
        Path fallbackDirectory = defaultBrowseDirectory();
        Path candidateDirectory = requestedDirectory == null
                ? fallbackDirectory
                : requestedDirectory.toAbsolutePath().normalize();

        if (Files.isDirectory(candidateDirectory)) {
            return candidateDirectory.toFile();
        }

        Path parentDirectory = candidateDirectory.getParent();
        if (parentDirectory != null && Files.isDirectory(parentDirectory)) {
            return parentDirectory.toFile();
        }

        return Files.isDirectory(fallbackDirectory) ? fallbackDirectory.toFile() : null;
    }

    private Path defaultBrowseDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome).toAbsolutePath().normalize();
        }

        return Path.of(".").toAbsolutePath().normalize();
    }
}
