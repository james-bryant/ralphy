package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class GitRepositoryInitializer {
    private final Deque<InitializationResult> queuedResults = new ConcurrentLinkedDeque<>();

    public InitializationResult initializeRepository(Path repositoryPath) {
        InitializationResult queuedResult = queuedResults.pollFirst();
        if (queuedResult != null) {
            return applyQueuedResult(repositoryPath, queuedResult);
        }

        ProcessBuilder processBuilder = new ProcessBuilder("git", "init");
        processBuilder.directory(repositoryPath.toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return InitializationResult.failure(buildFailureMessage("Git initialization failed", processOutput));
            }
        } catch (IOException exception) {
            return InitializationResult.failure("Unable to launch Git: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return InitializationResult.failure("Git initialization was interrupted.");
        }

        if (!Files.exists(repositoryPath.resolve(".git"))) {
            return InitializationResult.failure("Git initialization did not create repository metadata in " + repositoryPath);
        }

        return InitializationResult.success();
    }

    void queueSuccessForTest() {
        queuedResults.addLast(InitializationResult.success());
    }

    void queueFailureForTest(String message) {
        queuedResults.addLast(InitializationResult.failure(message));
    }

    private InitializationResult applyQueuedResult(Path repositoryPath, InitializationResult queuedResult) {
        if (!queuedResult.successful()) {
            return queuedResult;
        }

        try {
            Files.createDirectories(repositoryPath.resolve(".git"));
        } catch (IOException exception) {
            return InitializationResult.failure("Unable to create test Git metadata: " + exception.getMessage());
        }

        return queuedResult;
    }

    private String buildFailureMessage(String prefix, String processOutput) {
        if (processOutput == null || processOutput.isBlank()) {
            return prefix + ".";
        }

        return prefix + ": " + processOutput;
    }

    public record InitializationResult(boolean successful, String message) {
        private static InitializationResult success() {
            return new InitializationResult(true, "");
        }

        private static InitializationResult failure(String message) {
            return new InitializationResult(false, message);
        }
    }
}
