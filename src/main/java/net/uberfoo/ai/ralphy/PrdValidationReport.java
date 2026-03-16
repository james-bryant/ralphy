package net.uberfoo.ai.ralphy;

import java.util.List;

public record PrdValidationReport(List<PrdValidationError> errors) {
    public PrdValidationReport {
        errors = List.copyOf(errors == null ? List.of() : errors);
    }

    public static PrdValidationReport empty() {
        return new PrdValidationReport(List.of());
    }

    public static PrdValidationReport failure(List<PrdValidationError> errors) {
        return new PrdValidationReport(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
