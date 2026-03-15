package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Component
public class ProjectStorageInitializer {
    public void ensureStorageDirectories(ActiveProject activeProject) throws IOException {
        for (var directory : activeProject.managedDirectories()) {
            Files.createDirectories(directory);
        }
    }
}
