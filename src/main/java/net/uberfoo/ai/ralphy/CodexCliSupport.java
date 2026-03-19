package net.uberfoo.ai.ralphy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class CodexCliSupport {
    static final String DEFAULT_COMMAND = "codex";
    static final String INSTALL_COMMAND = "npm install -g @openai/codex";
    static final String LOGIN_COMMAND = "codex login";
    static final String LOGIN_STATUS_COMMAND = "codex login status";

    private CodexCliSupport() {
    }

    static List<String> buildNativeCommand(String codexCommand,
                                           Map<String, String> environmentVariables,
                                           List<String> trailingArguments) {
        return buildNativeCommand(codexCommand, environmentVariables, HostOperatingSystem.detectRuntime(),
                trailingArguments);
    }

    static List<String> buildNativeCommand(String codexCommand,
                                           Map<String, String> environmentVariables,
                                           HostOperatingSystem hostOperatingSystem,
                                           List<String> trailingArguments) {
        Objects.requireNonNull(environmentVariables, "environmentVariables must not be null");
        Objects.requireNonNull(trailingArguments, "trailingArguments must not be null");

        ParsedCommand parsedCommand = parseCommand(codexCommand);
        String resolvedExecutable = resolveNativeExecutable(parsedCommand.executable(), environmentVariables,
                hostOperatingSystem);
        List<String> command = new ArrayList<>();
        command.add(resolvedExecutable);
        command.addAll(parsedCommand.arguments());
        command.addAll(trailingArguments);
        return List.copyOf(command);
    }

    static void prependNativePathEntries(Map<String, String> environmentVariables) {
        prependNativePathEntries(environmentVariables, HostOperatingSystem.detectRuntime());
    }

    static void prependNativePathEntries(Map<String, String> environmentVariables,
                                         HostOperatingSystem hostOperatingSystem) {
        Objects.requireNonNull(environmentVariables, "environmentVariables must not be null");
        if (hostOperatingSystem == null || !hostOperatingSystem.isWindows()) {
            return;
        }

        List<String> pathEntries = nativePathEntries(environmentVariables);
        if (pathEntries.isEmpty()) {
            return;
        }

        String currentPath = environmentVariables.getOrDefault("PATH", "");
        List<String> combinedEntries = new ArrayList<>(pathEntries);
        if (hasText(currentPath)) {
            combinedEntries.add(currentPath);
        }

        environmentVariables.put("PATH", combinedEntries.stream()
                .filter(CodexCliSupport::hasText)
                .distinct()
                .collect(Collectors.joining(File.pathSeparator)));
    }

    static String buildWslCodexScript(String codexCommand, List<String> commandArguments) {
        return buildWslCliScript("Codex CLI", codexCommand, commandArguments);
    }

    static String buildWslCliScript(String cliDisplayName, String command, List<String> commandArguments) {
        Objects.requireNonNull(cliDisplayName, "cliDisplayName must not be null");
        Objects.requireNonNull(commandArguments, "commandArguments must not be null");
        ParsedCommand parsedCommand = parseCommand(command);
        List<String> configuredArguments = parsedCommand.arguments();
        List<String> versionArguments = new ArrayList<>(configuredArguments);
        versionArguments.add("--version");
        List<String> launchArguments = new ArrayList<>(configuredArguments);
        launchArguments.addAll(commandArguments);
        String versionCheckCommand = shellCommand(parsedCommand.executable(), versionArguments);
        String launchCommand = shellCommand(parsedCommand.executable(), launchArguments);
        return """
                if ! %s >/dev/null 2>&1; then
                  printf '%%s\\n' 'Unable to locate a working %s inside WSL using the configured interactive shell.' >&2
                  exit 1
                fi
                exec %s
                """.formatted(versionCheckCommand, cliDisplayName, launchCommand);
    }

    static String buildShellCommand(String command, List<String> arguments) {
        return shellCommand(command, arguments);
    }

    private static String normalizeCommand(String codexCommand) {
        if (!hasText(codexCommand)) {
            return DEFAULT_COMMAND;
        }
        return codexCommand.trim();
    }

    private static ParsedCommand parseCommand(String codexCommand) {
        List<String> tokens = tokenizeCommandLine(normalizeCommand(codexCommand));
        if (tokens.isEmpty()) {
            return new ParsedCommand(DEFAULT_COMMAND, List.of());
        }

        String executable = tokens.get(0);
        List<String> arguments = tokens.size() > 1
                ? List.copyOf(tokens.subList(1, tokens.size()))
                : List.of();
        return new ParsedCommand(executable, arguments);
    }

    private static List<String> tokenizeCommandLine(String commandLine) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char activeQuote = 0;
        for (int index = 0; index < commandLine.length(); index++) {
            char currentCharacter = commandLine.charAt(index);
            if (activeQuote == 0) {
                if (Character.isWhitespace(currentCharacter)) {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }
                if (currentCharacter == '"' || currentCharacter == '\'') {
                    activeQuote = currentCharacter;
                    continue;
                }
                current.append(currentCharacter);
                continue;
            }

            if (currentCharacter == activeQuote) {
                activeQuote = 0;
            } else {
                current.append(currentCharacter);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String resolveNativeExecutable(String executable,
                                                  Map<String, String> environmentVariables,
                                                  HostOperatingSystem hostOperatingSystem) {
        String normalizedExecutable = hasText(executable) ? executable.trim() : DEFAULT_COMMAND;
        if (looksLikePath(normalizedExecutable)) {
            return normalizedExecutable;
        }

        for (String candidate : nativeExecutableCandidates(normalizedExecutable, environmentVariables, hostOperatingSystem)) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        return normalizedExecutable;
    }

    private static boolean looksLikePath(String command) {
        return command.contains("\\") || command.contains("/") || command.contains(":");
    }

    private static boolean looksLikeWindowsPath(String command) {
        return command.contains("\\") || command.matches("^[A-Za-z]:.*");
    }

    private static boolean looksLikeSimpleCommandName(String command) {
        return hasText(command) && command.matches("[A-Za-z0-9._-]+");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> nativeExecutableCandidates(String commandName,
                                                           Map<String, String> environmentVariables,
                                                           HostOperatingSystem hostOperatingSystem) {
        List<String> candidates = new ArrayList<>();
        for (String directory : nativePathEntries(environmentVariables, hostOperatingSystem)) {
            for (String executableName : nativeExecutableNames(commandName)) {
                candidates.add(Path.of(directory, executableName).toString());
            }
        }
        return candidates;
    }

    private static List<String> nativePathEntries(Map<String, String> environmentVariables) {
        return nativePathEntries(environmentVariables, HostOperatingSystem.detectRuntime());
    }

    private static List<String> nativePathEntries(Map<String, String> environmentVariables,
                                                  HostOperatingSystem hostOperatingSystem) {
        Objects.requireNonNull(environmentVariables, "environmentVariables must not be null");
        if (hostOperatingSystem == null || !hostOperatingSystem.isWindows()) {
            return List.of();
        }

        List<String> entries = new ArrayList<>();
        addPathEntry(entries, environmentVariables.get("APPDATA"), "npm");
        addPathEntry(entries, environmentVariables.get("LOCALAPPDATA"), "Volta", "bin");
        addPathEntry(entries, environmentVariables.get("LOCALAPPDATA"), "Programs", "nodejs");
        addPathEntry(entries, environmentVariables.get("USERPROFILE"), "scoop", "shims");
        addPathEntry(entries, environmentVariables.get("ProgramFiles"), "nodejs");
        addPathEntry(entries, environmentVariables.get("ProgramFiles(x86)"), "nodejs");
        return entries.stream().distinct().toList();
    }

    private static void addPathEntry(List<String> entries, String root, String... segments) {
        if (!hasText(root)) {
            return;
        }

        Path candidatePath = Path.of(root, segments);
        if (Files.isDirectory(candidatePath)) {
            entries.add(candidatePath.toString());
        }
    }

    private static List<String> nativeExecutableNames(String commandName) {
        String normalizedName = hasText(commandName) ? commandName.trim() : DEFAULT_COMMAND;
        if (normalizedName.contains(".")) {
            return List.of(normalizedName);
        }
        return List.of(
                normalizedName + ".cmd",
                normalizedName + ".exe",
                normalizedName + ".bat",
                normalizedName
        );
    }

    private static String shellCommand(String command, List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");
        StringBuilder builder = new StringBuilder();
        builder.append(shellCommandWord(command));
        for (String argument : arguments) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(quoteForSh(argument));
        }
        return builder.toString();
    }

    private static String shellCommandWord(String command) {
        String normalizedCommand = normalizeCommand(command);
        if (looksLikeSimpleCommandName(normalizedCommand)) {
            return normalizedCommand;
        }
        return quoteForSh(normalizedCommand);
    }

    private static String quoteForSh(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue.replace("'", "'\"'\"'") + "'";
    }

    private record ParsedCommand(String executable, List<String> arguments) {
    }
}
