package net.uberfoo.ai.ralphy;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

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
    private TextField windowsPathPrefixField;

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
        nativeExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        wslExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        executionProfileToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                updateExecutionProfileFieldState()
        );
        nativeExecutionProfileRadioButton.setSelected(true);
        renderActiveProject(activeProjectService.activeProject().orElse(null));
        setProjectValidationMessage(activeProjectService.startupRecoveryMessage());
        setExecutionProfileMessage("");
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
        setExecutionProfileMessage("");
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
        if (activeProject == null) {
            activeProjectNameLabel.setText(NO_ACTIVE_PROJECT_NAME);
            activeProjectPathLabel.setText(NO_ACTIVE_PROJECT_PATH);
            activeProjectStatusLabel.setText(NO_ACTIVE_PROJECT_STATUS);
            renderExecutionProfile(null);
            renderExecutionOverview(null);
            return;
        }

        activeProjectNameLabel.setText(activeProject.displayName());
        activeProjectPathLabel.setText(activeProject.displayPath());
        activeProjectStatusLabel.setText(activeProject.displayName());
        renderExecutionProfile(activeProjectService.executionProfile().orElse(ExecutionProfile.nativePowerShell()));
        renderExecutionOverview(activeProject);
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

    private record ShellSection(String title, String workspaceText, String statusText) {
    }
}
