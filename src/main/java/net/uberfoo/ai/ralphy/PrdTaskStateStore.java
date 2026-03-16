package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
public class PrdTaskStateStore {
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public Optional<PrdTaskState> read(ActiveProject activeProject) throws IOException {
        Path taskStatePath = activeProject.activePrdJsonPath();
        if (!Files.exists(taskStatePath)) {
            return Optional.empty();
        }

        return Optional.of(objectMapper.readValue(taskStatePath.toFile(), PrdTaskState.class));
    }

    public void write(ActiveProject activeProject, PrdTaskState taskState) throws IOException {
        Files.createDirectories(activeProject.prdJsonDirectoryPath());
        Path taskStatePath = activeProject.activePrdJsonPath();
        Path temporaryTaskStatePath = taskStatePath.resolveSibling(taskStatePath.getFileName() + ".tmp");
        objectMapper.writeValue(temporaryTaskStatePath.toFile(), taskState);
        moveIntoPlace(temporaryTaskStatePath, taskStatePath);
    }

    private void moveIntoPlace(Path temporaryPath, Path destinationPath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    destinationPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
