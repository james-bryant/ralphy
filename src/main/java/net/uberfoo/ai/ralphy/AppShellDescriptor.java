package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

@Component
public class AppShellDescriptor {
    public String appName() {
        return "Ralphy";
    }

    public String navigationPlaceholder() {
        return "Open or create a project to populate navigation.";
    }

    public String shellTagline() {
        return "Desktop orchestration for repositories, PRDs, and execution loops.";
    }

    public String statusPlaceholder() {
        return "Ready to host project, editor, and execution workflows.";
    }

    public String windowTitle() {
        return appName();
    }

    public String workspacePlaceholder() {
        return "Project, editor, and execution workflows will appear here.";
    }
}
