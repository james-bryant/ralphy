package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WslPreflightReport(String executedAt,
                                 OverallStatus status,
                                 List<CheckResult> checks) {
    public WslPreflightReport {
        Objects.requireNonNull(executedAt, "executedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        checks = List.copyOf(Objects.requireNonNull(checks, "checks must not be null"));
    }

    public boolean passed() {
        return status == OverallStatus.PASS;
    }

    public enum OverallStatus {
        PASS,
        FAIL
    }

    public enum CheckStatus {
        PASS,
        FAIL
    }

    public enum CheckCategory {
        DISTRIBUTION("Distribution"),
        PATH_MAPPING("Path Mapping"),
        TOOLING("Tooling"),
        AUTHENTICATION("Authentication"),
        GIT("Git");

        private final String label;

        CheckCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckResult(String id,
                              String label,
                              CheckCategory category,
                              CheckStatus status,
                              String detail,
                              List<PreflightRemediationCommand> remediationCommands) {
        public CheckResult {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(category, "category must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(detail, "detail must not be null");
            remediationCommands = remediationCommands == null ? List.of() : List.copyOf(remediationCommands);
        }

        public CheckResult(String id,
                           String label,
                           CheckCategory category,
                           CheckStatus status,
                           String detail) {
            this(id, label, category, status, detail, List.of());
        }

        public boolean hasRemediationCommands() {
            return !remediationCommands.isEmpty();
        }
    }
}
