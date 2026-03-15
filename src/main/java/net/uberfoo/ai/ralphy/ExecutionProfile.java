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

        if (type == ProfileType.POWERSHELL) {
            wslDistribution = null;
            windowsPathPrefix = null;
            wslPathPrefix = null;
        }
    }

    public static ExecutionProfile nativePowerShell() {
        return new ExecutionProfile(ProfileType.POWERSHELL, null, null, null);
    }

    public String summary() {
        if (type == ProfileType.POWERSHELL) {
            return type.label();
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
        POWERSHELL("POWERSHELL", "Native Windows PowerShell"),
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
            if (storageValue == null || storageValue.isBlank() || "UNCONFIGURED".equalsIgnoreCase(storageValue)) {
                return POWERSHELL;
            }

            for (ProfileType candidate : values()) {
                if (candidate.storageValue.equalsIgnoreCase(storageValue.trim())) {
                    return candidate;
                }
            }

            return POWERSHELL;
        }
    }
}
