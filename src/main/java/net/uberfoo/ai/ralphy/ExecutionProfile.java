package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record ExecutionProfile(ProfileType type,
                               String wslDistribution,
                               String windowsPathPrefix,
                               String wslPathPrefix) {
    public ExecutionProfile {
        Objects.requireNonNull(type, "type must not be null");
        wslDistribution = normalize(wslDistribution);
        windowsPathPrefix = normalize(windowsPathPrefix);
        wslPathPrefix = normalize(wslPathPrefix);

        if (type == ProfileType.NATIVE) {
            wslDistribution = null;
            windowsPathPrefix = null;
            wslPathPrefix = null;
        }
    }

    public static ExecutionProfile nativeHost() {
        return new ExecutionProfile(ProfileType.NATIVE, null, null, null);
    }

    public static ExecutionProfile nativePowerShell() {
        return nativeHost();
    }

    public String summary() {
        return summary(HostOperatingSystem.detectRuntime());
    }

    public String summary(HostOperatingSystem hostOperatingSystem) {
        HostOperatingSystem resolvedHostOperatingSystem =
                hostOperatingSystem == null ? HostOperatingSystem.OTHER : hostOperatingSystem;
        if (type == ProfileType.NATIVE) {
            return resolvedHostOperatingSystem.nativeProfileLabel();
        }

        return "WSL: " + valueOrEmpty(wslDistribution)
                + " (" + valueOrEmpty(windowsPathPrefix)
                + " -> " + valueOrEmpty(wslPathPrefix) + ")";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public enum ProfileType {
        NATIVE("NATIVE", "Native"),
        WSL("WSL", "WSL");

        private final String storageValue;
        private final String label;

        ProfileType(String storageValue, String label) {
            this.storageValue = storageValue;
            this.label = label;
        }

        public String storageValue() {
            return storageValue;
        }

        public String label() {
            return label;
        }

        public static ProfileType fromStorageValue(String storageValue) {
            if (storageValue == null
                    || storageValue.isBlank()
                    || "UNCONFIGURED".equalsIgnoreCase(storageValue)
                    || "POWERSHELL".equalsIgnoreCase(storageValue)
                    || "NATIVE".equalsIgnoreCase(storageValue)) {
                return NATIVE;
            }

            for (ProfileType candidate : values()) {
                if (candidate.storageValue.equalsIgnoreCase(storageValue.trim())) {
                    return candidate;
                }
            }

            return NATIVE;
        }
    }

    public static String storedProfileTypeLabel(String storageValue) {
        if ("WSL".equalsIgnoreCase(storageValue)) {
            return "WSL";
        }
        if ("POWERSHELL".equalsIgnoreCase(storageValue)) {
            return HostOperatingSystem.WINDOWS.nativeProfileLabel();
        }
        if ("NATIVE".equalsIgnoreCase(storageValue)) {
            return "Native profile";
        }
        return valueOrEmpty(storageValue);
    }
}
