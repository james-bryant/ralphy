package net.uberfoo.ai.ralphy;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class AppShellController {
    private static final String ACTIVE_NAV_STYLE_CLASS = "shell-nav-button-active";
    private static final String NO_ACTIVE_PROJECT_NAME = "No active repository selected.";
    private static final String NO_ACTIVE_PROJECT_PATH =
            "Open an existing repository or create a new one to make it the active project.";
    private static final String NO_ACTIVE_PROJECT_STATUS = "No active project selected.";
    private static final String NO_ACTIVE_PROJECT_RUN_TITLE = "No active project";
    private static final String NO_ACTIVE_PROJECT_RUN_DETAIL =
            "Choose or restore a project to view resumable or reviewable run state.";
    private static final String NO_PERSISTED_RUN_STATE_TITLE = "No persisted run state";
    private static final String NO_PERSISTED_RUN_STATE_DETAIL =
            "The active project has no resumable or reviewable run metadata yet.";
    private static final String NO_ACTIVE_PROFILE_SUMMARY =
            "Open or create a repository to configure its execution profile.";
    private static final String NO_ACTIVE_NATIVE_PREFLIGHT_SUMMARY = "No active project";
    private static final String NO_ACTIVE_NATIVE_PREFLIGHT_DETAIL =
            "Open or create a repository to check native Windows Codex readiness.";
    private static final String NO_NATIVE_PREFLIGHT_SUMMARY = "Native preflight not run";
    private static final String NO_NATIVE_PREFLIGHT_DETAIL =
            "Run native Windows preflight before starting a PowerShell loop.";
    private static final String NO_ACTIVE_WSL_PREFLIGHT_SUMMARY = "No active project";
    private static final String NO_ACTIVE_WSL_PREFLIGHT_DETAIL =
            "Open or create a repository to check WSL Codex readiness.";
    private static final String NO_WSL_PREFLIGHT_SUMMARY = "WSL preflight not run";
    private static final String NO_WSL_PREFLIGHT_DETAIL =
            "Run WSL preflight before starting a WSL loop.";
    private static final String WSL_PREFLIGHT_PROFILE_REQUIRED_SUMMARY = "WSL profile not selected";
    private static final String WSL_PREFLIGHT_PROFILE_REQUIRED_DETAIL =
            "Save a WSL execution profile before running WSL preflight.";
    private static final ShellSection PROJECTS_SECTION = new ShellSection(
            "Projects",
            "Repository onboarding, recent projects, and diagnostics will appear here.",
            "Projects workspace ready."
    );
    private static final ShellSection PRD_EDITOR_SECTION = new ShellSection(
            "PRD Editor",
            "Create, refine, and validate the active PRD in this workspace.",
            "PRD Editor workspace ready."
    );
    private static final ShellSection EXECUTION_SECTION = new ShellSection(
            "Execution",
            "Run controls, current story progress, and live execution output will appear here.",
            "Execution workspace ready."
    );

    private final AppShellDescriptor shellDescriptor;
    private final ActiveProjectService activeProjectService;
    private final RepositoryDirectoryChooser repositoryDirectoryChooser;
    private final ToggleGroup executionProfileToggleGroup = new ToggleGroup();

    @FXML
    private Label activeProjectNameLabel;

    @FXML
    private Label activeProjectPathLabel;

    @FXML
    private Label activeProjectStatusLabel;

    @FXML
    private Label brandLabel;

    @FXML
    private Button createRepositoryButton;

    @FXML
    private Button executionNavButton;

    @FXML
    private Label executionOverviewDetailLabel;

    @FXML
    private Label executionOverviewHeadlineLabel;

    @FXML
    private Label executionProfileMessageLabel;

    @FXML
    private Label executionProfileSummaryLabel;

    @FXML
    private Label navigationPlaceholderLabel;

    @FXML
    private RadioButton nativeExecutionProfileRadioButton;

    @FXML
    private Label nativePreflightChecksLabel;

    @FXML
    private Label nativePreflightDetailLabel;

    @FXML
    private Label nativePreflightMessageLabel;

    @FXML
    private VBox nativePreflightRemediationContainer;

    @FXML
    private VBox nativePreflightRemediationSection;

    @FXML
    private Label nativePreflightSummaryLabel;

    @FXML
    private Button openRepositoryButton;

    @FXML
    private TextField newProjectNameField;

    @FXML
    private Label projectValidationMessageLabel;

    @FXML
    private Button prdEditorNavButton;

    @FXML
    private Button projectsNavButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label taglineLabel;

    @FXML
    private Label workspaceTitleLabel;

    @FXML
    private Label workspacePlaceholderLabel;

    @FXML
    private Button saveExecutionProfileButton;

    @FXML
    private Button runNativePreflightButton;

    @FXML
    private Button rerunNativePreflightFromRemediationButton;

    @FXML
    private Button rerunWslPreflightFromRemediationButton;

    @FXML
    private Button runWslPreflightButton;

    @FXML
    private TextField windowsPathPrefixField;

    @FXML
    private Label wslPreflightChecksLabel;

    @FXML
    private Label wslPreflightDetailLabel;

    @FXML
    private Label wslPreflightMessageLabel;

    @FXML
    private VBox wslPreflightRemediationContainer;

    @FXML
    private VBox wslPreflightRemediationSection;

    @FXML
    private Label wslPreflightSummaryLabel;

    @FXML
    private TextField wslDistributionField;

    @FXML
    private TextField wslPathPrefixField;

    @FXML
    private RadioButton wslExecutionProfileRadioButton;

    public AppShellController(AppShellDescriptor shellDescriptor,
                              ActiveProjectService activeProjectService,
                              RepositoryDirectoryChooser repositoryDirectoryChooser) {
        this.shellDescriptor = shellDescriptor;
        this.activeProjectService = activeProjectService;
        this.repositoryDirectoryChooser = repositoryDirectoryChooser;
    }

    @FXML
    private void initialize() {
        brandLabel.setText(shellDescriptor.appName());
        taglineLabel.setText(shellDescriptor.shellTagline());
        navigationPlaceholderLabel.setText(shellDescriptor.navigationPlaceholder());
        workspacePlaceholderLabel.setText(shellDescriptor.workspacePlaceholder());
        statusLabel.setText(shellDescriptor.statusPlaceholder());
        workspaceTitleLabel.setText("Workspace");
        clearActiveNavigationButton();
        projectValidationMessageLabel.managedProperty().bind(projectValidationMessageLabel.visibleProperty());
        executionProfileMessageLabel.managedProperty().bind(executionProfileMessageLabel.visibleProperty());
        nativePreflightMessageLabel.managedProperty().bind(nativePreflightMessageLabel.visibleProperty());
        wslPreflightMessageLabel.managedProperty().bind(wslPreflightMessageLabel.visibleProperty());
        nativePreflightRemediationSection.managedProperty().bind(nativePreflightRemediationSection.visibleProperty());
        wslPreflightRemediationSection.managedProperty().bind(wslPreflightRemediationSection.visibleProperty());
        nativeExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        wslExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        executionProfileToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                updateExecutionProfileFieldState()
        );
        nativeExecutionProfileRadioButton.setSelected(true);
        renderActiveProject(activeProjectService.activeProject().orElse(null));
        setProjectValidationMessage(activeProjectService.startupRecoveryMessage());
        setExecutionProfileMessage("");
        setNativePreflightMessage("");
        setWslPreflightMessage("");
    }

    @FXML
    private void showProjects() {
        activateSection(PROJECTS_SECTION, projectsNavButton);
    }

    @FXML
    private void showPrdEditor() {
        activateSection(PRD_EDITOR_SECTION, prdEditorNavButton);
    }

    @FXML
    private void showExecution() {
        activateSection(EXECUTION_SECTION, executionNavButton);
    }

    @FXML
    private void openExistingRepository() {
        Path initialDirectory = activeProjectService.activeProject()
                .map(ActiveProject::repositoryPath)
                .orElseGet(this::defaultBrowseDirectory);
        Optional<Path> selectedDirectory = repositoryDirectoryChooser.chooseRepository(
                openRepositoryButton.getScene().getWindow(),
                initialDirectory
        );
        if (selectedDirectory.isEmpty()) {
            return;
        }

        ActiveProjectService.ProjectActivationResult selectionResult =
                activeProjectService.openRepository(selectedDirectory.get());
        if (selectionResult.successful()) {
            renderActiveProject(selectionResult.activeProject());
            setProjectValidationMessage("");
            return;
        }

        renderActiveProject(activeProjectService.activeProject().orElse(null));
        setProjectValidationMessage(selectionResult.message());
    }

    @FXML
    private void createNewRepository() {
        String requestedProjectName = newProjectNameField.getText();
        if (requestedProjectName == null || requestedProjectName.isBlank()) {
            setProjectValidationMessage("Enter a project folder name.");
            return;
        }

        Optional<Path> selectedParentDirectory = repositoryDirectoryChooser.chooseParentDirectory(
                createRepositoryButton.getScene().getWindow(),
                defaultCreateParentDirectory()
        );
        if (selectedParentDirectory.isEmpty()) {
            return;
        }

        ActiveProjectService.ProjectActivationResult creationResult = activeProjectService.createRepository(
                selectedParentDirectory.get(),
                requestedProjectName
        );
        if (creationResult.successful()) {
            renderActiveProject(creationResult.activeProject());
            newProjectNameField.clear();
            setProjectValidationMessage("");
            return;
        }

        renderActiveProject(activeProjectService.activeProject().orElse(null));
        setProjectValidationMessage(creationResult.message());
    }

    @FXML
    private void saveExecutionProfile() {
        ActiveProjectService.ExecutionProfileSaveResult saveResult =
                activeProjectService.saveExecutionProfile(buildExecutionProfileFromForm());
        if (!saveResult.successful()) {
            setExecutionProfileMessage(saveResult.message());
            return;
        }

        renderExecutionProfile(saveResult.executionProfile());
        renderNativeWindowsPreflight();
        renderWslPreflight();
        setExecutionProfileMessage("");
    }

    @FXML
    private void runNativeWindowsPreflight() {
        ActiveProjectService.NativeWindowsPreflightRunResult runResult = activeProjectService.runNativeWindowsPreflight();
        renderNativeWindowsPreflight();
        setNativePreflightMessage(runResult.message());
    }

    @FXML
    private void runWslPreflight() {
        ActiveProjectService.WslPreflightRunResult runResult = activeProjectService.runWslPreflight();
        renderWslPreflight();
        setWslPreflightMessage(runResult.message());
    }

    private void activateSection(ShellSection section, Button activeButton) {
        workspaceTitleLabel.setText(section.title());
        workspacePlaceholderLabel.setText(section.workspaceText());
        statusLabel.setText(section.statusText());
        updateActiveNavigationButton(activeButton);
    }

    private Path defaultCreateParentDirectory() {
        return activeProjectService.activeProject()
                .map(ActiveProject::repositoryPath)
                .map(Path::getParent)
                .orElseGet(this::defaultBrowseDirectory);
    }

    private Path defaultBrowseDirectory() {
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome);
        }

        return Path.of(".").toAbsolutePath().normalize();
    }

    private void clearActiveNavigationButton() {
        for (Button button : navigationButtons()) {
            button.getStyleClass().remove(ACTIVE_NAV_STYLE_CLASS);
        }
    }

    private List<Button> navigationButtons() {
        return List.of(projectsNavButton, prdEditorNavButton, executionNavButton);
    }

    private void updateActiveNavigationButton(Button activeButton) {
        clearActiveNavigationButton();
        if (!activeButton.getStyleClass().contains(ACTIVE_NAV_STYLE_CLASS)) {
            activeButton.getStyleClass().add(ACTIVE_NAV_STYLE_CLASS);
        }
    }

    private void renderActiveProject(ActiveProject activeProject) {
        setNativePreflightMessage("");
        setWslPreflightMessage("");
        if (activeProject == null) {
            activeProjectNameLabel.setText(NO_ACTIVE_PROJECT_NAME);
            activeProjectPathLabel.setText(NO_ACTIVE_PROJECT_PATH);
            activeProjectStatusLabel.setText(NO_ACTIVE_PROJECT_STATUS);
            renderExecutionProfile(null);
            renderExecutionOverview(null);
            renderNativeWindowsPreflight();
            renderWslPreflight();
            return;
        }

        activeProjectNameLabel.setText(activeProject.displayName());
        activeProjectPathLabel.setText(activeProject.displayPath());
        activeProjectStatusLabel.setText(activeProject.displayName());
        renderExecutionProfile(activeProjectService.executionProfile().orElse(ExecutionProfile.nativePowerShell()));
        renderExecutionOverview(activeProject);
        renderNativeWindowsPreflight();
        renderWslPreflight();
    }

    private void renderExecutionProfile(ExecutionProfile executionProfile) {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        setExecutionProfileMessage("");
        nativeExecutionProfileRadioButton.setDisable(!activeProjectPresent);
        wslExecutionProfileRadioButton.setDisable(!activeProjectPresent);
        saveExecutionProfileButton.setDisable(!activeProjectPresent);

        if (!activeProjectPresent || executionProfile == null) {
            nativeExecutionProfileRadioButton.setSelected(true);
            wslDistributionField.clear();
            windowsPathPrefixField.clear();
            wslPathPrefixField.clear();
            executionProfileSummaryLabel.setText(NO_ACTIVE_PROFILE_SUMMARY);
            updateExecutionProfileFieldState();
            return;
        }

        if (executionProfile.type() == ExecutionProfile.ProfileType.WSL) {
            wslExecutionProfileRadioButton.setSelected(true);
        } else {
            nativeExecutionProfileRadioButton.setSelected(true);
        }
        wslDistributionField.setText(valueOrEmpty(executionProfile.wslDistribution()));
        windowsPathPrefixField.setText(valueOrEmpty(executionProfile.windowsPathPrefix()));
        wslPathPrefixField.setText(valueOrEmpty(executionProfile.wslPathPrefix()));
        executionProfileSummaryLabel.setText(executionProfile.summary());
        updateExecutionProfileFieldState();
    }

    private void renderExecutionOverview(ActiveProject activeProject) {
        if (activeProject == null) {
            executionOverviewHeadlineLabel.setText(NO_ACTIVE_PROJECT_RUN_TITLE);
            executionOverviewDetailLabel.setText(NO_ACTIVE_PROJECT_RUN_DETAIL);
            return;
        }

        Optional<ActiveProjectService.RunRecoveryCandidate> runRecoveryCandidate =
                activeProjectService.latestRunRecoveryState();
        if (runRecoveryCandidate.isEmpty()) {
            executionOverviewHeadlineLabel.setText(NO_PERSISTED_RUN_STATE_TITLE);
            executionOverviewDetailLabel.setText(NO_PERSISTED_RUN_STATE_DETAIL);
            return;
        }

        ActiveProjectService.RunRecoveryCandidate candidate = runRecoveryCandidate.get();
        executionOverviewHeadlineLabel.setText(candidate.action().label() + " run available");
        executionOverviewDetailLabel.setText(describeRunRecovery(candidate));
    }

    private String describeRunRecovery(ActiveProjectService.RunRecoveryCandidate candidate) {
        String runSubject = formatRunSubject(candidate);
        if (candidate.action() == ActiveProjectService.RunRecoveryAction.RESUMABLE) {
            return runSubject + " is in " + candidate.status() + " state and can be resumed.";
        }

        return runSubject + " ended in " + candidate.status() + " state and can be reviewed.";
    }

    private String formatRunSubject(ActiveProjectService.RunRecoveryCandidate candidate) {
        boolean hasStoryId = hasText(candidate.storyId());
        boolean hasRunId = hasText(candidate.runId());
        if (hasStoryId && hasRunId) {
            return "Story " + candidate.storyId() + " from run " + candidate.runId();
        }
        if (hasStoryId) {
            return "Story " + candidate.storyId();
        }
        if (hasRunId) {
            return "Run " + candidate.runId();
        }
        return "The latest run";
    }

    private void renderNativeWindowsPreflight() {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        runNativePreflightButton.setDisable(!activeProjectPresent);

        if (!activeProjectPresent) {
            nativePreflightSummaryLabel.setText(NO_ACTIVE_NATIVE_PREFLIGHT_SUMMARY);
            nativePreflightDetailLabel.setText(NO_ACTIVE_NATIVE_PREFLIGHT_DETAIL);
            nativePreflightChecksLabel.setText("");
            clearNativePreflightRemediation();
            return;
        }

        Optional<NativeWindowsPreflightReport> nativePreflightReport =
                activeProjectService.latestNativeWindowsPreflightReport();
        if (nativePreflightReport.isEmpty()) {
            nativePreflightSummaryLabel.setText(NO_NATIVE_PREFLIGHT_SUMMARY);
            nativePreflightDetailLabel.setText(NO_NATIVE_PREFLIGHT_DETAIL);
            nativePreflightChecksLabel.setText("");
            clearNativePreflightRemediation();
            return;
        }

        NativeWindowsPreflightReport report = nativePreflightReport.get();
        nativePreflightSummaryLabel.setText(report.passed()
                ? "Ready for native execution"
                : "Native execution blocked");
        nativePreflightDetailLabel.setText("Last checked " + report.executedAt()
                + ". Native PowerShell runs stay blocked until every check passes.");
        nativePreflightChecksLabel.setText(formatNativeWindowsPreflightChecks(report));
        renderNativePreflightRemediation(report);
    }

    private void renderWslPreflight() {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        if (!activeProjectPresent) {
            runWslPreflightButton.setDisable(true);
            wslPreflightSummaryLabel.setText(NO_ACTIVE_WSL_PREFLIGHT_SUMMARY);
            wslPreflightDetailLabel.setText(NO_ACTIVE_WSL_PREFLIGHT_DETAIL);
            wslPreflightChecksLabel.setText("");
            clearWslPreflightRemediation();
            return;
        }

        ExecutionProfile executionProfile = activeProjectService.executionProfile()
                .orElse(ExecutionProfile.nativePowerShell());
        if (executionProfile.type() != ExecutionProfile.ProfileType.WSL) {
            runWslPreflightButton.setDisable(true);
            wslPreflightSummaryLabel.setText(WSL_PREFLIGHT_PROFILE_REQUIRED_SUMMARY);
            wslPreflightDetailLabel.setText(WSL_PREFLIGHT_PROFILE_REQUIRED_DETAIL);
            wslPreflightChecksLabel.setText("");
            clearWslPreflightRemediation();
            return;
        }

        runWslPreflightButton.setDisable(false);
        Optional<WslPreflightReport> wslPreflightReport = activeProjectService.latestWslPreflightReport();
        if (wslPreflightReport.isEmpty()) {
            wslPreflightSummaryLabel.setText(NO_WSL_PREFLIGHT_SUMMARY);
            wslPreflightDetailLabel.setText(NO_WSL_PREFLIGHT_DETAIL);
            wslPreflightChecksLabel.setText("");
            clearWslPreflightRemediation();
            return;
        }

        WslPreflightReport report = wslPreflightReport.get();
        wslPreflightSummaryLabel.setText(report.passed()
                ? "Ready for WSL execution"
                : "WSL execution blocked");
        wslPreflightDetailLabel.setText("Last checked " + report.executedAt()
                + ". WSL runs stay blocked until every check passes.");
        wslPreflightChecksLabel.setText(formatWslPreflightChecks(report));
        renderWslPreflightRemediation(report);
    }

    private String formatNativeWindowsPreflightChecks(NativeWindowsPreflightReport report) {
        return report.checks().stream()
                .map(this::formatNativeWindowsPreflightCheck)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private String formatNativeWindowsPreflightCheck(NativeWindowsPreflightReport.CheckResult checkResult) {
        return checkResult.status().name()
                + " | "
                + checkResult.category().label()
                + " | "
                + checkResult.label()
                + " | "
                + checkResult.detail();
    }

    private String formatWslPreflightChecks(WslPreflightReport report) {
        return report.checks().stream()
                .map(this::formatWslPreflightCheck)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private String formatWslPreflightCheck(WslPreflightReport.CheckResult checkResult) {
        return checkResult.status().name()
                + " | "
                + checkResult.category().label()
                + " | "
                + checkResult.label()
                + " | "
                + checkResult.detail();
    }

    private void renderNativePreflightRemediation(NativeWindowsPreflightReport report) {
        List<VBox> remediationCards = report.checks().stream()
                .filter(checkResult -> checkResult.status() == NativeWindowsPreflightReport.CheckStatus.FAIL)
                .filter(NativeWindowsPreflightReport.CheckResult::hasRemediationCommands)
                .map(this::buildNativePreflightRemediationCard)
                .toList();
        nativePreflightRemediationContainer.getChildren().setAll(remediationCards);
        nativePreflightRemediationSection.setVisible(!remediationCards.isEmpty());
        rerunNativePreflightFromRemediationButton.setDisable(runNativePreflightButton.isDisable());
    }

    private VBox buildNativePreflightRemediationCard(NativeWindowsPreflightReport.CheckResult checkResult) {
        return buildRemediationCard(checkResult.label(), checkResult.detail(), checkResult.remediationCommands());
    }

    private void clearNativePreflightRemediation() {
        nativePreflightRemediationContainer.getChildren().clear();
        nativePreflightRemediationSection.setVisible(false);
        rerunNativePreflightFromRemediationButton.setDisable(true);
    }

    private void renderWslPreflightRemediation(WslPreflightReport report) {
        List<VBox> remediationCards = report.checks().stream()
                .filter(checkResult -> checkResult.status() == WslPreflightReport.CheckStatus.FAIL)
                .filter(WslPreflightReport.CheckResult::hasRemediationCommands)
                .map(this::buildWslPreflightRemediationCard)
                .toList();
        wslPreflightRemediationContainer.getChildren().setAll(remediationCards);
        wslPreflightRemediationSection.setVisible(!remediationCards.isEmpty());
        rerunWslPreflightFromRemediationButton.setDisable(runWslPreflightButton.isDisable());
    }

    private VBox buildWslPreflightRemediationCard(WslPreflightReport.CheckResult checkResult) {
        return buildRemediationCard(checkResult.label(), checkResult.detail(), checkResult.remediationCommands());
    }

    private void clearWslPreflightRemediation() {
        wslPreflightRemediationContainer.getChildren().clear();
        wslPreflightRemediationSection.setVisible(false);
        rerunWslPreflightFromRemediationButton.setDisable(true);
    }

    private VBox buildRemediationCard(String title,
                                      String detail,
                                      List<PreflightRemediationCommand> remediationCommands) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("muted-text");
        detailLabel.setWrapText(true);

        VBox card = new VBox(8.0, titleLabel, detailLabel);
        card.getStyleClass().add("preflight-remediation-card");
        for (PreflightRemediationCommand remediationCommand : remediationCommands) {
            card.getChildren().add(buildRemediationCommandRow(remediationCommand));
        }
        return card;
    }

    private VBox buildRemediationCommandRow(PreflightRemediationCommand remediationCommand) {
        Label label = new Label(remediationCommand.label());
        label.getStyleClass().add("section-title");
        label.setWrapText(true);

        TextField commandField = new TextField(remediationCommand.command());
        commandField.setEditable(false);
        commandField.getStyleClass().add("preflight-command-field");
        HBox.setHgrow(commandField, Priority.ALWAYS);

        Button copyButton = new Button("Copy");
        copyButton.getStyleClass().add("preflight-copy-button");
        copyButton.setOnAction(event -> copyToClipboard(remediationCommand.command()));

        HBox commandRow = new HBox(8.0, commandField, copyButton);
        commandRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(6.0, label, commandRow);
    }

    private void copyToClipboard(String value) {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(value);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void updateExecutionProfileFieldState() {
        boolean enableWslFields = activeProjectService.activeProject().isPresent()
                && wslExecutionProfileRadioButton.isSelected();
        wslDistributionField.setDisable(!enableWslFields);
        windowsPathPrefixField.setDisable(!enableWslFields);
        wslPathPrefixField.setDisable(!enableWslFields);
    }

    private ExecutionProfile buildExecutionProfileFromForm() {
        if (wslExecutionProfileRadioButton.isSelected()) {
            return new ExecutionProfile(
                    ExecutionProfile.ProfileType.WSL,
                    wslDistributionField.getText(),
                    windowsPathPrefixField.getText(),
                    wslPathPrefixField.getText()
            );
        }

        return ExecutionProfile.nativePowerShell();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void setProjectValidationMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        projectValidationMessageLabel.setText(hasMessage ? message : "");
        projectValidationMessageLabel.setVisible(hasMessage);
    }

    private void setExecutionProfileMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        executionProfileMessageLabel.setText(hasMessage ? message : "");
        executionProfileMessageLabel.setVisible(hasMessage);
    }

    private void setNativePreflightMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        nativePreflightMessageLabel.setText(hasMessage ? message : "");
        nativePreflightMessageLabel.setVisible(hasMessage);
    }

    private void setWslPreflightMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        wslPreflightMessageLabel.setText(hasMessage ? message : "");
        wslPreflightMessageLabel.setVisible(hasMessage);
    }

    private record ShellSection(String title, String workspaceText, String statusText) {
    }
}
