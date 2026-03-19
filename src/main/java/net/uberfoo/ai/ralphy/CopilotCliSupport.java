package net.uberfoo.ai.ralphy;

import java.util.List;
import java.util.Map;

final class CopilotCliSupport {
    static final String DEFAULT_COMMAND = "copilot";
    static final String INSTALL_COMMAND = "npm install -g @github/copilot";
    static final String LOGIN_COMMAND = "copilot login";

    private CopilotCliSupport() {
    }

    static List<String> buildNativeCommand(String copilotCommand,
                                           Map<String, String> environmentVariables,
                                           List<String> trailingArguments) {
        return CodexCliSupport.buildNativeCommand(copilotCommand, environmentVariables, trailingArguments);
    }

    static List<String> buildNativeCommand(String copilotCommand,
                                           Map<String, String> environmentVariables,
                                           HostOperatingSystem hostOperatingSystem,
                                           List<String> trailingArguments) {
        return CodexCliSupport.buildNativeCommand(copilotCommand, environmentVariables, hostOperatingSystem,
                trailingArguments);
    }

    static String buildWslCopilotScript(String copilotCommand, List<String> commandArguments) {
        return CodexCliSupport.buildWslCliScript("GitHub Copilot CLI", copilotCommand, commandArguments);
    }
}
