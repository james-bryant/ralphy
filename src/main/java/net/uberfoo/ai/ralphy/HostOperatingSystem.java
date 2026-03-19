package net.uberfoo.ai.ralphy;

import java.util.Locale;

public enum HostOperatingSystem {
    WINDOWS("Native Windows PowerShell"),
    LINUX("Native Linux Shell"),
    MAC("Native macOS Shell"),
    OTHER("Native Shell");

    private static final String HOST_OS_OVERRIDE_PROPERTY = "ralphy.host.os-name";

    private final String nativeProfileLabel;

    HostOperatingSystem(String nativeProfileLabel) {
        this.nativeProfileLabel = nativeProfileLabel;
    }

    public String nativeProfileLabel() {
        return nativeProfileLabel;
    }

    public boolean isWindows() {
        return this == WINDOWS;
    }

    public boolean supportsWslProfiles() {
        return this == WINDOWS;
    }

    public String nativeExecutionLabel() {
        return switch (this) {
            case WINDOWS -> "native Windows execution";
            case LINUX -> "native Linux execution";
            case MAC -> "native macOS execution";
            case OTHER -> "native execution";
        };
    }

    public static HostOperatingSystem detectRuntime() {
        String configuredOsName = System.getProperty(HOST_OS_OVERRIDE_PROPERTY);
        if (configuredOsName != null && !configuredOsName.isBlank()) {
            return detect(configuredOsName);
        }
        return detect(System.getProperty("os.name", ""));
    }

    public static HostOperatingSystem detect(String osName) {
        String normalizedName = osName == null ? "" : osName.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.contains("win")) {
            return WINDOWS;
        }
        if (normalizedName.contains("linux") || normalizedName.contains("nux")) {
            return LINUX;
        }
        if (normalizedName.contains("mac") || normalizedName.contains("darwin")) {
            return MAC;
        }
        return OTHER;
    }
}
