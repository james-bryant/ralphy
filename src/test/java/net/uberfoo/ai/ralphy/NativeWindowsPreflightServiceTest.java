package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeWindowsPreflightServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void runPassesWhenCodexAuthGitAndQualityGateAreReady() throws IOException {
        Path codexHome = Files.createDirectories(tempDir.resolve("codex-home"));
        Path appDataDirectory = Files.createDirectories(tempDir.resolve("appdata"));
        Path npmDirectory = Files.createDirectories(appDataDirectory.resolve("npm"));
        Files.writeString(npmDirectory.resolve("codex.cmd"), "@echo off\r\n");
        Files.writeString(npmDirectory.resolve("copilot.cmd"), "@echo off\r\n");
        Files.writeString(codexHome.resolve("auth.json"), """
                {
                  "OPENAI_API_KEY": null,
                  "tokens": {
                    "access_token": "token"
                  }
                }
                """);
        Path repository = createRepository("ready-repo", true);

        NativeWindowsPreflightService service = new NativeWindowsPreflightService(
                Map.of("APPDATA", appDataDirectory.toString()),
                codexHome,
                HostOperatingSystem.WINDOWS,
                (workingDirectory, command) -> {
                    if (command.getFirst().endsWith("codex.cmd")) {
                        return NativeWindowsPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
                    }
                    if (command.getFirst().endsWith("copilot.cmd")) {
                        return NativeWindowsPreflightService.CommandResult.success(0, "copilot-cli 1.0.7");
                    }
                    if ("git".equals(command.getFirst())) {
                        return NativeWindowsPreflightService.CommandResult.success(0, "true");
                    }
                    return NativeWindowsPreflightService.CommandResult.failure("Unexpected command");
                }
        );

        NativeWindowsPreflightReport report = service.run(new ActiveProject(repository));

        assertTrue(report.passed());
        assertEquals(NativeWindowsPreflightReport.OverallStatus.PASS, report.status());
        assertEquals(5, report.checks().size());
        Map<String, NativeWindowsPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("codex_cli").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("copilot_cli").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("codex_auth").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("git_ready").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("quality_gate").status());
        assertTrue(checksById.get("quality_gate").detail().contains(
                NativeWindowsPreflightService.WINDOWS_QUALITY_GATE_COMMAND));
        assertTrue(checksById.get("quality_gate").remediationCommands().isEmpty());
    }

    @Test
    void runDoesNotFailQualityGateWhenTheRepositoryIsNotMavenBased() throws IOException {
        Path codexHome = Files.createDirectories(tempDir.resolve("codex-home"));
        Files.writeString(codexHome.resolve("auth.json"), """
                {
                  "OPENAI_API_KEY": null,
                  "tokens": {}
                }
                """);
        Path repository = createRepository("broken-repo", false);

        NativeWindowsPreflightService service = new NativeWindowsPreflightService(
                Map.of(),
                codexHome,
                HostOperatingSystem.WINDOWS,
                (workingDirectory, command) -> {
                    return switch (command.getFirst()) {
                        case "codex" -> NativeWindowsPreflightService.CommandResult.failure(
                                "The system cannot find the file specified.");
                        case "copilot" -> NativeWindowsPreflightService.CommandResult.failure(
                                "The system cannot find the file specified.");
                        case "git" -> NativeWindowsPreflightService.CommandResult.success(1, "fatal: not a git repository");
                        default -> NativeWindowsPreflightService.CommandResult.failure("Unexpected command");
                    };
                }
        );

        NativeWindowsPreflightReport report = service.run(new ActiveProject(repository));

        assertFalse(report.passed());
        assertEquals(NativeWindowsPreflightReport.OverallStatus.FAIL, report.status());
        Map<String, NativeWindowsPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(NativeWindowsPreflightReport.CheckCategory.TOOLING, checksById.get("codex_cli").category());
        assertEquals(NativeWindowsPreflightReport.CheckCategory.TOOLING, checksById.get("copilot_cli").category());
        assertEquals(NativeWindowsPreflightReport.CheckCategory.AUTHENTICATION, checksById.get("codex_auth").category());
        assertEquals(NativeWindowsPreflightReport.CheckCategory.GIT, checksById.get("git_ready").category());
        assertEquals(NativeWindowsPreflightReport.CheckCategory.QUALITY_GATE, checksById.get("quality_gate").category());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.FAIL, checksById.get("codex_cli").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.FAIL, checksById.get("copilot_cli").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.FAIL, checksById.get("codex_auth").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.FAIL, checksById.get("git_ready").status());
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("quality_gate").status());
        assertTrue(checksById.get("codex_auth").detail().contains("No stored Codex credentials"));
        assertTrue(checksById.get("quality_gate").detail().contains("No repository-owned native quality gate command was auto-detected"));
        assertEquals("npm install -g @openai/codex",
                checksById.get("codex_cli").remediationCommands().getFirst().command());
        assertEquals("npm install -g @github/copilot",
                checksById.get("copilot_cli").remediationCommands().getFirst().command());
        assertEquals("codex login",
                checksById.get("codex_auth").remediationCommands().getFirst().command());
        assertTrue(checksById.get("quality_gate").remediationCommands().isEmpty());
    }

    @Test
    void runDetectsGradleWrapperQualityGateWhenPresent() throws IOException {
        Path codexHome = Files.createDirectories(tempDir.resolve("codex-home"));
        Files.writeString(codexHome.resolve("auth.json"), """
                {
                  "OPENAI_API_KEY": "test-key",
                  "tokens": {}
                }
                """);
        Path repository = Files.createDirectory(tempDir.resolve("gradle-repo"));
        Files.createDirectory(repository.resolve(".git"));
        Files.writeString(repository.resolve("gradlew.bat"), "@echo off\r\n");
        Files.writeString(repository.resolve("build.gradle.kts"), "plugins {}\r\n");

        NativeWindowsPreflightService service = new NativeWindowsPreflightService(
                Map.of(),
                codexHome,
                HostOperatingSystem.WINDOWS,
                (workingDirectory, command) -> switch (command.getFirst()) {
                    case "codex" -> NativeWindowsPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
                    case "copilot" -> NativeWindowsPreflightService.CommandResult.success(0, "copilot-cli 1.0.7");
                    case "git" -> NativeWindowsPreflightService.CommandResult.success(0, "true");
                    default -> NativeWindowsPreflightService.CommandResult.failure("Unexpected command");
                }
        );

        NativeWindowsPreflightReport report = service.run(new ActiveProject(repository));

        Map<String, NativeWindowsPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("quality_gate").status());
        assertTrue(checksById.get("quality_gate").detail().contains(
                NativeWindowsPreflightService.WINDOWS_GRADLE_QUALITY_GATE_COMMAND));
    }

    @Test
    void runDetectsLinuxQualityGateWhenRunningOnLinux() throws IOException {
        Path codexHome = Files.createDirectories(tempDir.resolve("linux-codex-home"));
        Files.writeString(codexHome.resolve("auth.json"), """
                {
                  "OPENAI_API_KEY": "test-key",
                  "tokens": {}
                }
                """);
        Path repository = Files.createDirectory(tempDir.resolve("linux-repo"));
        Files.createDirectory(repository.resolve(".git"));
        Files.writeString(repository.resolve("mvnw"), "#!/bin/sh\n");
        Files.writeString(repository.resolve("pom.xml"), "<project/>");

        NativeWindowsPreflightService service = new NativeWindowsPreflightService(
                Map.of(),
                codexHome,
                HostOperatingSystem.LINUX,
                (workingDirectory, command) -> switch (command.getFirst()) {
                    case "codex" -> NativeWindowsPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
                    case "copilot" -> NativeWindowsPreflightService.CommandResult.success(0, "copilot-cli 1.0.7");
                    case "git" -> NativeWindowsPreflightService.CommandResult.success(0, "true");
                    default -> NativeWindowsPreflightService.CommandResult.failure("Unexpected command");
                }
        );

        NativeWindowsPreflightReport report = service.run(new ActiveProject(repository));

        Map<String, NativeWindowsPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(NativeWindowsPreflightReport.CheckStatus.PASS, checksById.get("quality_gate").status());
        assertTrue(checksById.get("quality_gate").detail().contains(NativeWindowsPreflightService.LINUX_QUALITY_GATE_COMMAND));
    }

    private Path createRepository(String name, boolean includeQualityGateFiles) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(name));
        Files.createDirectory(repository.resolve(".git"));
        if (includeQualityGateFiles) {
            Files.writeString(repository.resolve("mvnw.cmd"), "@echo off\r\n");
            Files.writeString(repository.resolve("pom.xml"), "<project/>");
        }
        return repository;
    }

    private Map<String, NativeWindowsPreflightReport.CheckResult> checksById(NativeWindowsPreflightReport report) {
        return report.checks().stream().collect(Collectors.toMap(
                NativeWindowsPreflightReport.CheckResult::id,
                Function.identity()
        ));
    }
}
