package net.uberfoo.ai.ralphy;

import java.util.List;
import java.util.Objects;

final class WslShellSupport {
    static final String FALLBACK_SHELL = "/bin/bash";
    private static final String USER_SHELL_QUERY = "getent passwd \"$USER\" | cut -d: -f7";

    private WslShellSupport() {
    }

    static String resolveShellPath(String distribution, CommandExecutor commandExecutor) {
        Objects.requireNonNull(distribution, "distribution must not be null");
        Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");

        CommandResult commandResult = commandExecutor.execute(List.of(
                "wsl.exe",
                "--distribution",
                distribution,
                "--exec",
                "/bin/sh",
                "-lc",
                USER_SHELL_QUERY
        ));
        if (!commandResult.successful()) {
            return FALLBACK_SHELL;
        }

        return commandResult.output().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.startsWith("/"))
                .findFirst()
                .orElse(FALLBACK_SHELL);
    }

    @FunctionalInterface
    interface CommandExecutor {
        CommandResult execute(List<String> command);
    }

    record CommandResult(boolean successful, String output) {
    }
}
