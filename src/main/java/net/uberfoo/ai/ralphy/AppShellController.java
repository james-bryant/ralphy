package net.uberfoo.ai.ralphy;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class AppShellController {
    private final AppShellDescriptor shellDescriptor;

    @FXML
    private Label brandLabel;

    @FXML
    private Label navigationPlaceholderLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label taglineLabel;

    @FXML
    private Label workspacePlaceholderLabel;

    public AppShellController(AppShellDescriptor shellDescriptor) {
        this.shellDescriptor = shellDescriptor;
    }

    @FXML
    private void initialize() {
        brandLabel.setText(shellDescriptor.appName());
        taglineLabel.setText(shellDescriptor.shellTagline());
        navigationPlaceholderLabel.setText(shellDescriptor.navigationPlaceholder());
        workspacePlaceholderLabel.setText(shellDescriptor.workspacePlaceholder());
        statusLabel.setText(shellDescriptor.statusPlaceholder());
    }
}
