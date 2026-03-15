package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreflightRemediationCommand(String label, String command) {
    public PreflightRemediationCommand {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(command, "command must not be null");
    }
}
