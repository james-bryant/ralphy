package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WslPreflightServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void runReportsPassingChecksWhenDistroAndMappedRepositoryAreReady() throws IOException {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspaces"));
        Path repository = Files.createDirectories(workspaceRoot.resolve("sample-repo"));
        ExecutionProfile executionProfile = new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                workspaceRoot.toString(),
                "/mnt/c/workspaces"
        );

        WslPreflightService service = new WslPreflightService((workingDirectory, command) -> {
            if (command.contains("--list")) {
                return WslPreflightService.CommandResult.success(0, "Ubuntu-24.04\nDebian");
            }

            String script = command.getLast();
            if (script.contains("pwd")) {
                return WslPreflightService.CommandResult.success(0, "/mnt/c/workspaces/sample-repo");
            }
            if (script.contains("codex --version")) {
                return WslPreflightService.CommandResult.success(0, "codex-cli 0.114.0");
            }
            if (script.contains("OPENAI_API_KEY")) {
                return WslPreflightService.CommandResult.success(0,
                        "Detected stored Codex login tokens in /home/test/.codex/auth.json.");
            }
            if (script.contains("git rev-parse --is-inside-work-tree")) {
                return WslPreflightService.CommandResult.success(0, "true");
            }

            return WslPreflightService.CommandResult.failure("Unexpected command");
        });

        WslPreflightReport report = service.run(new ActiveProject(repository), executionProfile);

        assertEquals(WslPreflightReport.OverallStatus.PASS, report.status());
        Map<String, WslPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(WslPreflightReport.CheckStatus.PASS, checksById.get("wsl_distribution").status());
        assertEquals(WslPreflightReport.CheckStatus.PASS, checksById.get("path_mapping").status());
        assertEquals(WslPreflightReport.CheckStatus.PASS, checksById.get("codex_cli").status());
        assertEquals(WslPreflightReport.CheckStatus.PASS, checksById.get("codex_auth").status());
        assertEquals(WslPreflightReport.CheckStatus.PASS, checksById.get("git_ready").status());
        assertTrue(checksById.get("path_mapping").detail().contains("/mnt/c/workspaces/sample-repo"));
        assertTrue(checksById.get("git_ready").remediationCommands().isEmpty());
    }

    @Test
    void runReportsFailingChecksWhenDistroOrPathMappingAreNotUsable() throws IOException {
        Path workspaceRoot = Files.createDirectories(tempDir.resolve("workspaces"));
        Path repository = Files.createDirectories(tempDir.resolve("other-root").resolve("sample-repo"));
        ExecutionProfile executionProfile = new ExecutionProfile(
                ExecutionProfile.ProfileType.WSL,
                "Ubuntu-24.04",
                workspaceRoot.toString(),
                "/mnt/c/workspaces"
        );

        List<List<String>> executedCommands = new ArrayList<>();
        WslPreflightService service = new WslPreflightService((workingDirectory, command) -> {
            executedCommands.add(command);
            if (command.contains("--list")) {
                return WslPreflightService.CommandResult.success(0, "Debian");
            }

            return WslPreflightService.CommandResult.failure("Unexpected command");
        });

        WslPreflightReport report = service.run(new ActiveProject(repository), executionProfile);

        assertEquals(WslPreflightReport.OverallStatus.FAIL, report.status());
        Map<String, WslPreflightReport.CheckResult> checksById = checksById(report);
        assertEquals(WslPreflightReport.CheckCategory.DISTRIBUTION, checksById.get("wsl_distribution").category());
        assertEquals(WslPreflightReport.CheckCategory.PATH_MAPPING, checksById.get("path_mapping").category());
        assertEquals(WslPreflightReport.CheckCategory.TOOLING, checksById.get("codex_cli").category());
        assertEquals(WslPreflightReport.CheckCategory.AUTHENTICATION, checksById.get("codex_auth").category());
        assertEquals(WslPreflightReport.CheckCategory.GIT, checksById.get("git_ready").category());
        assertFalse(checksById.get("wsl_distribution").detail().isBlank());
        assertTrue(checksById.get("path_mapping").detail().contains("outside the configured Windows path prefix"));
        assertEquals(WslPreflightReport.CheckStatus.FAIL, checksById.get("codex_cli").status());
        assertEquals(WslPreflightReport.CheckStatus.FAIL, checksById.get("codex_auth").status());
        assertEquals(WslPreflightReport.CheckStatus.FAIL, checksById.get("git_ready").status());
        assertTrue(checksById.get("wsl_distribution").remediationCommands().stream()
                .anyMatch(command -> command.command().contains("wsl.exe --install -d")));
        assertTrue(checksById.get("path_mapping").remediationCommands().stream()
                .anyMatch(command -> command.command().contains("Test-Path")));
        assertTrue(executedCommands.stream().noneMatch(command -> command.contains("npm install")));
        assertTrue(executedCommands.stream().noneMatch(command -> command.getLast().contains("codex login")));
    }

    private Map<String, WslPreflightReport.CheckResult> checksById(WslPreflightReport report) {
        return report.checks().stream().collect(Collectors.toMap(
                WslPreflightReport.CheckResult::id,
                checkResult -> checkResult
        ));
    }
}
