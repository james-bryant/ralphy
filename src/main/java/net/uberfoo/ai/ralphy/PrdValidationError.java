package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record PrdValidationError(String location, String message) {
    public PrdValidationError {
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public String description() {
        return location + " | " + message;
    }
}
