package net.uberfoo.ai.ralphy;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

public final class WslPathMapper {
    private WslPathMapper() {
    }

    public static PathMappingResult mapRepositoryPath(ExecutionProfile executionProfile, Path repositoryPath) {
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");
        Objects.requireNonNull(repositoryPath, "repositoryPath must not be null");

        if (executionProfile.type() != ExecutionProfile.ProfileType.WSL) {
            return PathMappingResult.failure("Save a WSL execution profile before mapping repository paths.");
        }

        Path normalizedRepositoryPath = repositoryPath.toAbsolutePath().normalize();
        Path normalizedWindowsPrefix;
        try {
            normalizedWindowsPrefix = Path.of(executionProfile.windowsPathPrefix());
        } catch (InvalidPathException exception) {
            return PathMappingResult.failure("The configured Windows path prefix is invalid: "
                    + executionProfile.windowsPathPrefix());
        }

        if (!normalizedWindowsPrefix.isAbsolute()) {
            return PathMappingResult.failure("The configured Windows path prefix must be absolute: "
                    + executionProfile.windowsPathPrefix());
        }

        String normalizedWslPrefix = normalizeWslPrefix(executionProfile.wslPathPrefix());
        if (normalizedWslPrefix.isBlank() || !normalizedWslPrefix.startsWith("/")) {
            return PathMappingResult.failure("The configured WSL path prefix must start with '/': "
                    + executionProfile.wslPathPrefix());
        }

        String repositoryText = normalizedRepositoryPath.toString();
        String prefixText = normalizedWindowsPrefix.toAbsolutePath().normalize().toString();
        if (!isWithinWindowsPrefix(repositoryText, prefixText)) {
            return PathMappingResult.failure("The active repository " + normalizedRepositoryPath
                    + " is outside the configured Windows path prefix " + normalizedWindowsPrefix + ".");
        }

        String suffix = repositoryText.substring(prefixText.length()).replace('\\', '/');
        if (!suffix.isEmpty() && !suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }

        return PathMappingResult.success(normalizedWslPrefix + suffix);
    }

    private static boolean isWithinWindowsPrefix(String candidatePath, String prefixPath) {
        String normalizedCandidate = candidatePath.replace('/', '\\');
        String normalizedPrefix = prefixPath.replace('/', '\\');
        if (normalizedCandidate.equalsIgnoreCase(normalizedPrefix)) {
            return true;
        }

        return normalizedCandidate.regionMatches(true, 0, normalizedPrefix, 0, normalizedPrefix.length())
                && normalizedCandidate.length() > normalizedPrefix.length()
                && normalizedCandidate.charAt(normalizedPrefix.length()) == '\\';
    }

    private static String normalizeWslPrefix(String wslPathPrefix) {
        if (wslPathPrefix == null || wslPathPrefix.isBlank()) {
            return "";
        }

        String normalized = wslPathPrefix.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public record PathMappingResult(boolean successful, String wslPath, String message) {
        private static PathMappingResult success(String wslPath) {
            return new PathMappingResult(true, wslPath, "");
        }

        private static PathMappingResult failure(String message) {
            return new PathMappingResult(false, null, message);
        }
    }
}
