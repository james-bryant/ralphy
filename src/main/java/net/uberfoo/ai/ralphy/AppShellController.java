package net.uberfoo.ai.ralphy;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AppShellController {
    private static final String ACTIVE_NAV_STYLE_CLASS = "shell-nav-button-active";
    private static final String ACTIVE_INTERVIEW_STEP_STYLE_CLASS = "interview-step-button-active";
    private static final String COMPLETE_INTERVIEW_STEP_STYLE_CLASS = "interview-step-button-complete";
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
    private static final String NO_ACTIVE_PRD_INTERVIEW_SUMMARY =
            "Open or create a repository to start a PRD interview draft.";
    private static final String NO_ACTIVE_PRD_INTERVIEW_PROMPT =
            "Choose or restore an active repository before answering sequenced PRD interview questions.";
    private static final String PRD_INTERVIEW_DRAFT_LOCATION_DETAIL =
            "Draft answers are stored per project in .ralph-tui/project-metadata.json.";
    private static final String PRD_INTERVIEW_UNSAVED_CHANGES_MESSAGE =
            "Draft has unsaved changes. Save or move to another question to persist them.";
    private static final String NO_ACTIVE_PRD_DOCUMENT_MESSAGE =
            "Open or create a repository before generating a Markdown PRD.";
    private static final String EMPTY_PRD_DOCUMENT_MESSAGE =
            "No active PRD yet. Generate one from the latest interview answers or import an existing Markdown PRD.";
    private static final String READY_PRD_DOCUMENT_MESSAGE =
            "The active project already has a saved Markdown PRD. Edit it below and save when you refine the plan.";
    private static final String PRD_DOCUMENT_UNSAVED_CHANGES_MESSAGE =
            "Markdown PRD has unsaved changes. Save to update .ralph-tui/prds/active-prd.md.";
    private static final String PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_REGENERATE_MESSAGE =
            "Save the current Markdown PRD before regenerating it from interview answers.";
    private static final String PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_IMPORT_MESSAGE =
            "Save the current Markdown PRD before importing another Markdown file.";
    private static final String PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_PRD_JSON_IMPORT_MESSAGE =
            "Save the current Markdown PRD before importing prd.json.";
    private static final String NO_ACTIVE_PRD_TO_SAVE_MESSAGE =
            "Generate or import a Markdown PRD before saving edits.";
    private static final String NO_ACTIVE_PRD_TO_EXPORT_MESSAGE =
            "Generate or import a Markdown PRD before exporting it.";
    private static final String NO_ACTIVE_PRD_TO_IMPORT_JSON_MESSAGE =
            "Generate, save, or import a Markdown PRD before importing prd.json.";
    private static final String NO_ACTIVE_STORY_PROGRESS_SUMMARY = "No active project";
    private static final String NO_ACTIVE_STORY_PROGRESS_DETAIL =
            "Open a repository before tracking story progress.";
    private static final String NO_ACTIVE_STORY_PROGRESS_CURRENT = "Current story: none";
    private static final String NO_ACTIVE_STORY_PROGRESS_COUNTS = "0 total stories tracked.";
    private static final String NO_SYNCED_STORY_PROGRESS_SUMMARY = "No synced stories";
    private static final String NO_SYNCED_STORY_PROGRESS_DETAIL =
            "Save a valid PRD so Ralphy can create persisted story records.";
    private static final String NO_SYNCED_STORY_PROGRESS_CURRENT = "Current story: none";
    private static final String NO_SYNCED_STORY_PROGRESS_COUNTS = "0 total stories tracked.";
    private static final String NO_ACTIVE_RUN_OUTPUT_SUMMARY = "No active project";
    private static final String NO_ACTIVE_RUN_OUTPUT_DETAIL =
            "Open a repository before reviewing live or persisted run output.";
    private static final String NO_PERSISTED_RUN_OUTPUT_SUMMARY = "No run output yet";
    private static final String NO_PERSISTED_RUN_OUTPUT_DETAIL =
            "Start a story to stream live Codex output or reopen a completed run.";
    private static final String PENDING_ASSISTANT_SUMMARY_MESSAGE =
            "Waiting for the final assistant summary. Switch to Raw Output to follow the live stream.";
    private static final String PENDING_RAW_OUTPUT_MESSAGE = "Waiting for Codex to emit live output...";
    private static final String MISSING_ASSISTANT_SUMMARY_MESSAGE =
            "No final assistant summary is stored for this run.";
    private static final String MISSING_RAW_OUTPUT_MESSAGE = "No raw output is stored for this run.";
    private static final String STDERR_SECTION_HEADER = "[stderr]";
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
    private final PresetCatalogService presetCatalogService;
    private final PrdMarkdownGenerator prdMarkdownGenerator;
    private final PrdInterviewService prdInterviewService;
    private final MarkdownPrdFileChooser markdownPrdFileChooser;
    private final PrdJsonFileChooser prdJsonFileChooser;
    private final RepositoryDirectoryChooser repositoryDirectoryChooser;
    private final Executor backgroundExecutor;
    private final ToggleGroup executionProfileToggleGroup = new ToggleGroup();
    private final ToggleGroup presetCatalogToggleGroup = new ToggleGroup();
    private final ToggleGroup runOutputViewToggleGroup = new ToggleGroup();
    private final List<Button> prdInterviewQuestionButtons = new ArrayList<>();
    private PrdInterviewDraft currentPrdInterviewDraft = PrdInterviewDraft.empty();
    private int currentPrdInterviewQuestionIndex;
    private boolean renderingPrdInterview;
    private boolean renderingPrdDocument;
    private boolean prdDocumentDirty;
    private boolean singleStorySessionInProgress;
    private boolean pauseRequested;
    private boolean executionPaused;
    private String singleStorySessionTaskId;
    private String pausedSessionTaskId;
    private PresetUseCase activeSessionPresetUseCase;
    private String prdDocumentEditorBaseline = "";
    private String prdDocumentLineSeparator = System.lineSeparator();
    private RunOutputPresentationState runOutputPresentationState = RunOutputPresentationState.empty();

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
    private Label singleStorySessionDetailLabel;

    @FXML
    private Label singleStorySessionMessageLabel;

    @FXML
    private Label singleStorySessionSummaryLabel;

    @FXML
    private Label storyProgressSummaryLabel;

    @FXML
    private Label storyProgressDetailLabel;

    @FXML
    private Label storyProgressCurrentStoryLabel;

    @FXML
    private Label storyProgressOverallCountsLabel;

    @FXML
    private Label storyProgressPendingCountLabel;

    @FXML
    private Label storyProgressBlockedCountLabel;

    @FXML
    private Label storyProgressRunningCountLabel;

    @FXML
    private Label storyProgressPassedCountLabel;

    @FXML
    private Label storyProgressFailedCountLabel;

    @FXML
    private Label storyProgressPausedCountLabel;

    @FXML
    private Label runOutputDetailLabel;

    @FXML
    private RadioButton assistantSummaryViewRadioButton;

    @FXML
    private RadioButton rawOutputViewRadioButton;

    @FXML
    private Label runOutputSummaryLabel;

    @FXML
    private TextArea runOutputTextArea;

    @FXML
    private Label prdValidationDetailLabel;

    @FXML
    private Label prdValidationErrorsLabel;

    @FXML
    private Label prdValidationSummaryLabel;

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
    private Label presetAssumptionsValueLabel;

    @FXML
    private VBox presetCatalogCard;

    @FXML
    private Label presetPreviewNameLabel;

    @FXML
    private Label presetPreviewOverviewLabel;

    @FXML
    private Label presetPreviewVersionLabel;

    @FXML
    private TextArea presetPromptPreviewArea;

    @FXML
    private RadioButton prdCreationPresetRadioButton;

    @FXML
    private TextArea prdInterviewAnswerArea;

    @FXML
    private VBox prdDocumentCard;

    @FXML
    private TextField prdDocumentPathField;

    @FXML
    private TextArea prdDocumentPreviewArea;

    @FXML
    private Label prdDocumentStateLabel;

    @FXML
    private Button generatePrdButton;

    @FXML
    private Button importPrdDocumentButton;

    @FXML
    private VBox prdInterviewCard;

    @FXML
    private Button importPrdJsonButton;

    @FXML
    private Button savePrdDocumentButton;

    @FXML
    private Button exportPrdDocumentButton;

    @FXML
    private Label prdInterviewDraftStateLabel;

    @FXML
    private Label prdInterviewGuidanceLabel;

    @FXML
    private Button prdInterviewNextButton;

    @FXML
    private Button prdInterviewPreviousButton;

    @FXML
    private Label prdInterviewPromptLabel;

    @FXML
    private VBox prdInterviewQuestionsContainer;

    @FXML
    private Button prdInterviewSaveButton;

    @FXML
    private Label prdInterviewSectionLabel;

    @FXML
    private Label prdInterviewSummaryLabel;

    @FXML
    private Label prdInterviewTitleLabel;

    @FXML
    private Label prdInterviewQuestionCounterLabel;

    @FXML
    private Label presetRequiredSkillsValueLabel;

    @FXML
    private Button projectsNavButton;

    @FXML
    private RadioButton retryFixPresetRadioButton;

    @FXML
    private Label statusLabel;

    @FXML
    private RadioButton runSummaryPresetRadioButton;

    @FXML
    private RadioButton storyImplementationPresetRadioButton;

    @FXML
    private Label taglineLabel;

    @FXML
    private Label workspaceTitleLabel;

    @FXML
    private Label workspacePlaceholderLabel;

    @FXML
    private Button saveExecutionProfileButton;

    @FXML
    private Button startSingleStoryButton;

    @FXML
    private Button pauseSingleStoryButton;

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
                              PresetCatalogService presetCatalogService,
                              PrdMarkdownGenerator prdMarkdownGenerator,
                              PrdInterviewService prdInterviewService,
                              MarkdownPrdFileChooser markdownPrdFileChooser,
                              PrdJsonFileChooser prdJsonFileChooser,
                              RepositoryDirectoryChooser repositoryDirectoryChooser,
                              @Qualifier("ralphyBackgroundExecutor") Executor backgroundExecutor) {
        this.shellDescriptor = shellDescriptor;
        this.activeProjectService = activeProjectService;
        this.presetCatalogService = presetCatalogService;
        this.prdMarkdownGenerator = prdMarkdownGenerator;
        this.prdInterviewService = prdInterviewService;
        this.markdownPrdFileChooser = markdownPrdFileChooser;
        this.prdJsonFileChooser = prdJsonFileChooser;
        this.repositoryDirectoryChooser = repositoryDirectoryChooser;
        this.backgroundExecutor = backgroundExecutor;
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
        prdInterviewDraftStateLabel.managedProperty().bind(prdInterviewDraftStateLabel.visibleProperty());
        prdDocumentStateLabel.managedProperty().bind(prdDocumentStateLabel.visibleProperty());
        prdValidationErrorsLabel.managedProperty().bind(prdValidationErrorsLabel.visibleProperty());
        singleStorySessionMessageLabel.managedProperty().bind(singleStorySessionMessageLabel.visibleProperty());
        nativeExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        wslExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        executionProfileToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                updateExecutionProfileFieldState()
        );
        assistantSummaryViewRadioButton.setToggleGroup(runOutputViewToggleGroup);
        rawOutputViewRadioButton.setToggleGroup(runOutputViewToggleGroup);
        runOutputViewToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                renderRunOutputView()
        );
        assistantSummaryViewRadioButton.setSelected(true);
        configurePresetCatalog();
        configurePrdInterview();
        configurePrdDocumentEditor();
        prdDocumentPathField.setEditable(false);
        runOutputTextArea.setEditable(false);
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
        if (activeProjectService.activeProject().isPresent()) {
            if (!persistCurrentPrdInterviewDraft(currentPrdInterviewQuestionIndex, false)) {
                return;
            }
            if (!persistCurrentPrdDocument(false)) {
                return;
            }
        }
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
        if (activeProjectService.activeProject().isPresent()) {
            if (!persistCurrentPrdInterviewDraft(currentPrdInterviewQuestionIndex, false)) {
                return;
            }
            if (!persistCurrentPrdDocument(false)) {
                return;
            }
        }
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
        renderPrdValidation();
        renderSingleStorySession();
        setExecutionProfileMessage("");
    }

    @FXML
    private void runNativeWindowsPreflight() {
        ActiveProjectService.NativeWindowsPreflightRunResult runResult = activeProjectService.runNativeWindowsPreflight();
        renderNativeWindowsPreflight();
        renderSingleStorySession();
        setNativePreflightMessage(runResult.message());
    }

    @FXML
    private void runWslPreflight() {
        ActiveProjectService.WslPreflightRunResult runResult = activeProjectService.runWslPreflight();
        renderWslPreflight();
        renderSingleStorySession();
        setWslPreflightMessage(runResult.message());
    }

    @FXML
    private void savePrdInterviewDraft() {
        persistCurrentPrdInterviewDraft(currentPrdInterviewQuestionIndex, true);
    }

    @FXML
    private void savePrdDocument() {
        persistCurrentPrdDocument(true);
    }

    @FXML
    private void importPrdDocument() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdDocumentState(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
            return;
        }
        if (prdDocumentDirty) {
            setPrdDocumentState(PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_IMPORT_MESSAGE);
            return;
        }

        Optional<Path> selectedPath = markdownPrdFileChooser.chooseImportFile(
                importPrdDocumentButton.getScene().getWindow(),
                initialImportPrdPath(activeProject.get())
        );
        if (selectedPath.isEmpty()) {
            return;
        }

        ActiveProjectService.MarkdownPrdImportResult importResult =
                activeProjectService.importMarkdownPrd(selectedPath.get());
        if (importResult.markdown() != null) {
            renderGeneratedPrd(activeProject.get());
            renderPrdValidation();
            renderSingleStorySession();
            renderStoryProgressDashboard();
        }
        if (!importResult.successful()) {
            setPrdDocumentState(importResult.message());
            return;
        }

        setPrdDocumentState("Imported Markdown PRD from " + importResult.importedFromPath()
                + " into " + importResult.activePrdPath() + ".");
    }

    @FXML
    private void importPrdJson() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdDocumentState(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
            return;
        }
        if (prdDocumentDirty) {
            setPrdDocumentState(PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_PRD_JSON_IMPORT_MESSAGE);
            return;
        }
        if (activeProjectService.activePrdMarkdown().isEmpty()) {
            setPrdDocumentState(NO_ACTIVE_PRD_TO_IMPORT_JSON_MESSAGE);
            return;
        }

        Optional<Path> selectedPath = prdJsonFileChooser.chooseImportFile(
                importPrdJsonButton.getScene().getWindow(),
                initialImportPrdJsonPath(activeProject.get())
        );
        if (selectedPath.isEmpty()) {
            return;
        }

        ActiveProjectService.PrdJsonImportResult importResult = activeProjectService.importPrdJson(selectedPath.get());
        if (importResult.taskState() != null) {
            renderGeneratedPrd(activeProject.get());
            renderPrdValidation();
            renderSingleStorySession();
            renderStoryProgressDashboard();
        }
        setPrdDocumentState(formatPrdJsonImportMessage(importResult));
    }

    @FXML
    private void exportPrdDocument() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdDocumentState(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
            return;
        }
        if (!persistCurrentPrdDocument(false)) {
            return;
        }
        if (activeProjectService.activePrdMarkdown().isEmpty()) {
            setPrdDocumentState(NO_ACTIVE_PRD_TO_EXPORT_MESSAGE);
            return;
        }

        Optional<Path> selectedPath = markdownPrdFileChooser.chooseExportFile(
                exportPrdDocumentButton.getScene().getWindow(),
                initialExportPrdPath(activeProject.get())
        );
        if (selectedPath.isEmpty()) {
            return;
        }

        ActiveProjectService.MarkdownPrdExportResult exportResult =
                activeProjectService.exportActivePrd(selectedPath.get());
        if (!exportResult.successful()) {
            setPrdDocumentState(exportResult.message());
            return;
        }

        setPrdDocumentState("Exported Markdown PRD to " + exportResult.exportedToPath() + ".");
    }

    @FXML
    private void generatePrdFromInterviewAnswers() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            renderGeneratedPrd(null);
            return;
        }

        if (prdDocumentDirty) {
            setPrdDocumentState(PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_REGENERATE_MESSAGE);
            return;
        }

        if (!persistCurrentPrdInterviewDraft(currentPrdInterviewQuestionIndex, false)) {
            return;
        }

        String markdown = prdMarkdownGenerator.generate(activeProject.get(), currentPrdInterviewDraft);
        ActiveProjectService.ActivePrdSaveResult saveResult = activeProjectService.saveActivePrd(markdown);
        if (!saveResult.successful()) {
            setPrdDocumentState(saveResult.message());
            return;
        }

        renderGeneratedPrd(activeProject.get());
        renderPrdValidation();
        renderSingleStorySession();
        renderStoryProgressDashboard();
        setPrdDocumentState("Generated PRD saved to " + saveResult.path()
                + ". Edit it below and save when you refine the plan.");
    }

    @FXML
    private void showPreviousPrdInterviewQuestion() {
        navigateToPrdInterviewQuestion(currentPrdInterviewQuestionIndex - 1);
    }

    @FXML
    private void showNextPrdInterviewQuestion() {
        navigateToPrdInterviewQuestion(currentPrdInterviewQuestionIndex + 1);
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

    private Path initialImportPrdPath(ActiveProject activeProject) {
        return trackedMarkdownPrdPath(MarkdownPrdExchangeLocations::lastImportedPath)
                .orElse(activeProject.repositoryPath());
    }

    private Path initialExportPrdPath(ActiveProject activeProject) {
        return trackedMarkdownPrdPath(MarkdownPrdExchangeLocations::lastExportedPath)
                .orElse(activeProject.activePrdPath());
    }

    private Path initialImportPrdJsonPath(ActiveProject activeProject) {
        return activeProject.activePrdJsonPath();
    }

    private Optional<Path> trackedMarkdownPrdPath(
            java.util.function.Function<MarkdownPrdExchangeLocations, String> valueExtractor) {
        return activeProjectService.markdownPrdExchangeLocations()
                .map(valueExtractor)
                .flatMap(this::toPath);
    }

    private Optional<Path> toPath(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Path.of(value).toAbsolutePath().normalize());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
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
            singleStorySessionInProgress = false;
            pauseRequested = false;
            singleStorySessionTaskId = null;
            clearPausedSessionState();
            runOutputPresentationState = RunOutputPresentationState.empty();
            activeProjectNameLabel.setText(NO_ACTIVE_PROJECT_NAME);
            activeProjectPathLabel.setText(NO_ACTIVE_PROJECT_PATH);
            activeProjectStatusLabel.setText(NO_ACTIVE_PROJECT_STATUS);
            renderExecutionProfile(null);
            renderExecutionOverview(null);
            renderNativeWindowsPreflight();
            renderWslPreflight();
            renderPrdValidation();
            renderSingleStorySession();
            renderStoryProgressDashboard();
            renderRunOutputView();
            renderPrdInterview(null);
            renderGeneratedPrd(null);
            return;
        }

        refreshRunOutputPresentationState();
        activeProjectNameLabel.setText(activeProject.displayName());
        activeProjectPathLabel.setText(activeProject.displayPath());
        activeProjectStatusLabel.setText(activeProject.displayName());
        renderExecutionProfile(activeProjectService.executionProfile().orElse(ExecutionProfile.nativePowerShell()));
        renderExecutionOverview(activeProject);
        renderNativeWindowsPreflight();
        renderWslPreflight();
        renderPrdValidation();
        renderSingleStorySession();
        renderStoryProgressDashboard();
        renderRunOutputView();
        renderPrdInterview(activeProjectService.prdInterviewDraft().orElse(PrdInterviewDraft.empty()));
        renderGeneratedPrd(activeProject);
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

    private void renderSingleStorySession() {
        PresetUseCase sessionPresetUseCase = sessionPresetUseCase();
        ActiveProjectService.SingleStorySessionAvailability availability =
                activeProjectService.singleStorySessionAvailability(sessionPresetUseCase);

        if (pauseRequested && singleStorySessionInProgress && hasText(singleStorySessionTaskId)) {
            singleStorySessionSummaryLabel.setText("Pause requested");
            singleStorySessionDetailLabel.setText(singleStorySessionTaskId
                    + " is still running. Ralphy will stop before the next story starts.");
        } else if (singleStorySessionInProgress && hasText(singleStorySessionTaskId)) {
            singleStorySessionSummaryLabel.setText("Running " + singleStorySessionTaskId);
            singleStorySessionDetailLabel.setText(runningSessionDetail(sessionPresetUseCase, singleStorySessionTaskId));
        } else if (executionPaused && hasText(pausedSessionTaskId) && availability.startable()) {
            singleStorySessionSummaryLabel.setText("Session paused");
            singleStorySessionDetailLabel.setText(pausedSessionTaskId
                    + " is waiting for " + sessionResumeLabel(sessionPresetUseCase) + ".");
        } else {
            if (executionPaused && !availability.startable()) {
                clearPausedSessionState();
            }
            singleStorySessionSummaryLabel.setText(availability.summary());
            singleStorySessionDetailLabel.setText(availability.detail());
        }

        startSingleStoryButton.setText(executionPaused
                ? sessionResumeLabel(sessionPresetUseCase)
                : sessionActionLabel(sessionPresetUseCase));
        startSingleStoryButton.setDisable(!availability.startable() || singleStorySessionInProgress);
        pauseSingleStoryButton.setText(pauseRequested ? "Pause Requested" : "Pause");
        pauseSingleStoryButton.setDisable(!singleStorySessionInProgress || pauseRequested);
    }

    private void renderStoryProgressDashboard() {
        StoryProgressDashboard dashboard = buildStoryProgressDashboard();
        storyProgressSummaryLabel.setText(dashboard.summary());
        storyProgressDetailLabel.setText(dashboard.detail());
        storyProgressCurrentStoryLabel.setText(dashboard.currentStory());
        storyProgressOverallCountsLabel.setText(dashboard.overallCounts());
        storyProgressPendingCountLabel.setText(Integer.toString(dashboard.pendingCount()));
        storyProgressBlockedCountLabel.setText(Integer.toString(dashboard.blockedCount()));
        storyProgressRunningCountLabel.setText(Integer.toString(dashboard.runningCount()));
        storyProgressPassedCountLabel.setText(Integer.toString(dashboard.passedCount()));
        storyProgressFailedCountLabel.setText(Integer.toString(dashboard.failedCount()));
        storyProgressPausedCountLabel.setText(Integer.toString(dashboard.pausedCount()));
    }

    private void renderRunOutputView() {
        if (activeProjectService.activeProject().isEmpty()) {
            runOutputSummaryLabel.setText(NO_ACTIVE_RUN_OUTPUT_SUMMARY);
            runOutputDetailLabel.setText(NO_ACTIVE_RUN_OUTPUT_DETAIL);
            runOutputTextArea.setText(NO_ACTIVE_RUN_OUTPUT_DETAIL);
            return;
        }

        runOutputSummaryLabel.setText(runOutputPresentationState.summary());
        runOutputDetailLabel.setText(runOutputPresentationState.detail());
        runOutputTextArea.setText(selectedRunOutputView() == RunOutputView.ASSISTANT_SUMMARY
                ? assistantSummaryBody(runOutputPresentationState)
                : rawOutputBody(runOutputPresentationState));
    }

    private void refreshRunOutputPresentationState() {
        if (singleStorySessionInProgress && runOutputPresentationState.active()) {
            return;
        }
        runOutputPresentationState = loadPersistedRunOutputPresentationState();
    }

    private RunOutputPresentationState loadPersistedRunOutputPresentationState() {
        if (activeProjectService.activeProject().isEmpty()) {
            return RunOutputPresentationState.empty();
        }

        return activeProjectService.latestRunMetadata()
                .map(this::toRunOutputPresentationState)
                .orElseGet(RunOutputPresentationState::empty);
    }

    private RunOutputPresentationState toRunOutputPresentationState(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        String storyId = valueOrEmpty(runMetadataRecord.storyId());
        String status = valueOrEmpty(runMetadataRecord.status());
        String runId = valueOrEmpty(runMetadataRecord.runId());
        String profileType = valueOrEmpty(runMetadataRecord.profileType());
        String summary = hasText(storyId)
                ? "Latest run: " + storyId + " | " + status
                : "Latest run output";
        String detail = buildRunOutputDetail(runId, profileType, runMetadataRecord.startedAt(), runMetadataRecord.endedAt());
        return new RunOutputPresentationState(
                true,
                "RUNNING".equalsIgnoreCase(status) && !hasText(runMetadataRecord.endedAt()),
                summary,
                detail,
                readAssistantSummary(runMetadataRecord),
                readCombinedRawOutput(runMetadataRecord)
        );
    }

    private String buildRunOutputDetail(String runId, String profileType, String startedAt, String endedAt) {
        List<String> details = new ArrayList<>();
        if (hasText(runId)) {
            details.add("Run " + runId);
        }
        if (hasText(profileType)) {
            details.add(profileType + " profile");
        }
        if (hasText(startedAt)) {
            details.add("Started " + startedAt);
        }
        if (hasText(endedAt)) {
            details.add("Ended " + endedAt);
        }
        if (details.isEmpty()) {
            return NO_PERSISTED_RUN_OUTPUT_DETAIL;
        }
        return String.join(" | ", details);
    }

    private String readAssistantSummary(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        return readArtifactText(runMetadataRecord.artifactPaths().assistantSummaryPath());
    }

    private String readCombinedRawOutput(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        String stdout = readArtifactText(runMetadataRecord.artifactPaths().stdoutPath());
        String stderr = readArtifactText(runMetadataRecord.artifactPaths().stderrPath());
        if (!hasText(stdout) && !hasText(stderr)) {
            return "";
        }
        if (!hasText(stderr)) {
            return stdout;
        }
        if (!hasText(stdout)) {
            return STDERR_SECTION_HEADER + System.lineSeparator() + stderr;
        }

        StringBuilder rawOutput = new StringBuilder(stdout);
        if (!stdout.endsWith(System.lineSeparator())) {
            rawOutput.append(System.lineSeparator());
        }
        rawOutput.append(System.lineSeparator())
                .append(STDERR_SECTION_HEADER)
                .append(System.lineSeparator())
                .append(stderr);
        return rawOutput.toString();
    }

    private String readArtifactText(String artifactPath) {
        if (!hasText(artifactPath)) {
            return "";
        }

        try {
            Path resolvedPath = Path.of(artifactPath);
            if (!Files.exists(resolvedPath)) {
                return "";
            }
            return Files.readString(resolvedPath, StandardCharsets.UTF_8);
        } catch (InvalidPathException | java.io.IOException ignored) {
            return "";
        }
    }

    private String assistantSummaryBody(RunOutputPresentationState state) {
        if (!state.available()) {
            return NO_PERSISTED_RUN_OUTPUT_DETAIL;
        }
        if (hasText(state.assistantSummary())) {
            return state.assistantSummary();
        }
        return state.active() ? PENDING_ASSISTANT_SUMMARY_MESSAGE : MISSING_ASSISTANT_SUMMARY_MESSAGE;
    }

    private String rawOutputBody(RunOutputPresentationState state) {
        if (!state.available()) {
            return NO_PERSISTED_RUN_OUTPUT_DETAIL;
        }
        if (hasText(state.rawOutput())) {
            return state.rawOutput();
        }
        return state.active() ? PENDING_RAW_OUTPUT_MESSAGE : MISSING_RAW_OUTPUT_MESSAGE;
    }

    private RunOutputView selectedRunOutputView() {
        if (rawOutputViewRadioButton.isSelected()) {
            return RunOutputView.RAW_OUTPUT;
        }
        return RunOutputView.ASSISTANT_SUMMARY;
    }

    private StoryProgressDashboard buildStoryProgressDashboard() {
        if (activeProjectService.activeProject().isEmpty()) {
            return new StoryProgressDashboard(
                    NO_ACTIVE_STORY_PROGRESS_SUMMARY,
                    NO_ACTIVE_STORY_PROGRESS_DETAIL,
                    NO_ACTIVE_STORY_PROGRESS_CURRENT,
                    NO_ACTIVE_STORY_PROGRESS_COUNTS,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        Optional<PrdTaskState> taskState = activeProjectService.prdTaskState();
        if (taskState.isEmpty() || taskState.get().tasks().isEmpty()) {
            return new StoryProgressDashboard(
                    NO_SYNCED_STORY_PROGRESS_SUMMARY,
                    NO_SYNCED_STORY_PROGRESS_DETAIL,
                    NO_SYNCED_STORY_PROGRESS_CURRENT,
                    NO_SYNCED_STORY_PROGRESS_COUNTS,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        String pausedStoryId = pausedStoryId();
        EnumMap<StoryDashboardState, Integer> counts = new EnumMap<>(StoryDashboardState.class);
        for (StoryDashboardState state : StoryDashboardState.values()) {
            counts.put(state, 0);
        }

        List<DashboardStoryState> dashboardStories = taskState.get().tasks().stream()
                .map(task -> new DashboardStoryState(task, dashboardStateFor(task, pausedStoryId)))
                .toList();
        for (DashboardStoryState dashboardStory : dashboardStories) {
            StoryDashboardState state = dashboardStory.state();
            counts.put(state, counts.get(state) + 1);
        }

        DashboardStoryState focusedStory = findFocusedStory(dashboardStories).orElse(null);
        int totalStories = taskState.get().tasks().size();
        int passedCount = counts.get(StoryDashboardState.PASSED);
        int outstandingCount = totalStories - passedCount;

        String summary;
        String detail;
        if (pauseRequested && singleStorySessionInProgress && hasText(singleStorySessionTaskId)) {
            summary = "Pause requested";
            detail = singleStorySessionTaskId
                    + " is still running. Ralphy will stop after the current step completes.";
        } else if (counts.get(StoryDashboardState.RUNNING) > 0) {
            summary = "Execution running";
            detail = focusedStory == null
                    ? "A story is in progress and the counts update from persisted task state."
                    : focusedStory.task().taskId()
                    + " is running and the dashboard reflects the live execution state.";
        } else if (counts.get(StoryDashboardState.PAUSED) > 0) {
            summary = "Execution paused";
            detail = focusedStory == null
                    ? "A resumable story is waiting to resume."
                    : executionPaused
                    ? focusedStory.task().taskId()
                    + " is ready but paused before the next step begins."
                    : focusedStory.task().taskId()
                    + " was restored from persisted metadata and is waiting to resume.";
        } else if (passedCount == totalStories) {
            summary = "All synced stories passed";
            detail = totalStories + " of " + totalStories + " stories are in the passed state.";
        } else if (counts.get(StoryDashboardState.FAILED) > 0) {
            summary = "Execution needs review";
            detail = counts.get(StoryDashboardState.FAILED)
                    + " failed stor"
                    + (counts.get(StoryDashboardState.FAILED) == 1 ? "y requires" : "ies require")
                    + " attention before the next loop step.";
        } else if (counts.get(StoryDashboardState.BLOCKED) > 0
                && counts.get(StoryDashboardState.PENDING) == 0
                && counts.get(StoryDashboardState.RUNNING) == 0
                && counts.get(StoryDashboardState.PAUSED) == 0) {
            summary = "Execution blocked";
            detail = counts.get(StoryDashboardState.BLOCKED)
                    + " blocked stor"
                    + (counts.get(StoryDashboardState.BLOCKED) == 1 ? "y is" : "ies are")
                    + " preventing forward progress.";
        } else {
            summary = "Execution ready";
            detail = counts.get(StoryDashboardState.PENDING)
                    + " pending, "
                    + counts.get(StoryDashboardState.BLOCKED)
                    + " blocked, and "
                    + counts.get(StoryDashboardState.FAILED)
                    + " failed stories are currently tracked.";
        }

        return new StoryProgressDashboard(
                summary,
                detail,
                formatCurrentStory(focusedStory),
                totalStories
                        + " total stories | "
                        + passedCount
                        + " passed | "
                        + outstandingCount
                        + " not yet done.",
                counts.get(StoryDashboardState.PENDING),
                counts.get(StoryDashboardState.BLOCKED),
                counts.get(StoryDashboardState.RUNNING),
                counts.get(StoryDashboardState.PASSED),
                counts.get(StoryDashboardState.FAILED),
                counts.get(StoryDashboardState.PAUSED)
        );
    }

    private Optional<DashboardStoryState> findFocusedStory(List<DashboardStoryState> dashboardStories) {
        for (StoryDashboardState preferredState : List.of(
                StoryDashboardState.RUNNING,
                StoryDashboardState.PAUSED,
                StoryDashboardState.FAILED,
                StoryDashboardState.PENDING,
                StoryDashboardState.BLOCKED,
                StoryDashboardState.PASSED
        )) {
            Optional<DashboardStoryState> matchingStory = dashboardStories.stream()
                    .filter(dashboardStory -> dashboardStory.state() == preferredState)
                    .findFirst();
            if (matchingStory.isPresent()) {
                return matchingStory;
            }
        }
        return Optional.empty();
    }

    private StoryDashboardState dashboardStateFor(PrdTaskRecord task, String pausedStoryId) {
        if (singleStorySessionInProgress
                && hasText(singleStorySessionTaskId)
                && task.taskId().equals(singleStorySessionTaskId)) {
            return StoryDashboardState.RUNNING;
        }
        if (isPausedStory(task, pausedStoryId)) {
            return StoryDashboardState.PAUSED;
        }

        return switch (task.status()) {
            case READY -> StoryDashboardState.PENDING;
            case BLOCKED -> StoryDashboardState.BLOCKED;
            case RUNNING -> StoryDashboardState.RUNNING;
            case COMPLETED -> StoryDashboardState.PASSED;
            case FAILED -> StoryDashboardState.FAILED;
        };
    }

    private boolean isPausedStory(PrdTaskRecord task, String pausedStoryId) {
        if (!hasText(pausedStoryId) || !task.taskId().equals(pausedStoryId)) {
            return false;
        }
        if (executionPaused && pausedStoryId.equals(pausedSessionTaskId)) {
            return true;
        }
        return task.status() == PrdTaskStatus.RUNNING;
    }

    private String pausedStoryId() {
        if (executionPaused && hasText(pausedSessionTaskId)) {
            return pausedSessionTaskId;
        }
        if (singleStorySessionInProgress) {
            return null;
        }

        return activeProjectService.latestRunRecoveryState()
                .filter(candidate -> candidate.action() == ActiveProjectService.RunRecoveryAction.RESUMABLE)
                .map(ActiveProjectService.RunRecoveryCandidate::storyId)
                .orElse(null);
    }

    private void clearPausedSessionState() {
        executionPaused = false;
        pausedSessionTaskId = null;
        activeSessionPresetUseCase = null;
    }

    private String formatCurrentStory(DashboardStoryState focusedStory) {
        if (focusedStory == null) {
            return "Current story: none";
        }

        return switch (focusedStory.state()) {
            case RUNNING -> "Current story: Running | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
            case PAUSED -> "Current story: Paused | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
            case FAILED -> "Current story: Failed | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
            case PENDING -> "Current story: Next pending | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
            case BLOCKED -> "Current story: Blocked | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
            case PASSED -> "Current story: Most recent pass | "
                    + focusedStory.task().taskId()
                    + " - "
                    + focusedStory.task().title();
        };
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

    private void renderPrdValidation() {
        ActiveProjectService.PrdExecutionGate executionGate = activeProjectService.prdExecutionGate();
        prdValidationSummaryLabel.setText(executionGate.summary());
        prdValidationDetailLabel.setText(executionGate.detail());

        String validationErrors = formatPrdValidationErrors(executionGate.validationReport());
        boolean hasValidationErrors = !validationErrors.isBlank();
        prdValidationErrorsLabel.setText(hasValidationErrors ? validationErrors : "");
        prdValidationErrorsLabel.setVisible(hasValidationErrors);
    }

    private String formatPrdValidationErrors(PrdValidationReport validationReport) {
        return validationReport.errors().stream()
                .map(PrdValidationError::description)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
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

    @FXML
    private void startSingleStorySession() {
        if (singleStorySessionInProgress) {
            return;
        }

        PresetUseCase presetUseCase = sessionPresetUseCase();
        ActiveProjectService.SingleStorySessionAvailability availability =
                activeProjectService.singleStorySessionAvailability(presetUseCase);
        if (!availability.startable()) {
            setSingleStorySessionMessage(availability.detail());
            renderSingleStorySession();
            renderStoryProgressDashboard();
            return;
        }

        pauseRequested = false;
        executionPaused = false;
        pausedSessionTaskId = null;
        activeSessionPresetUseCase = presetUseCase;
        launchStorySessionStep(presetUseCase, availability);
    }

    @FXML
    private void pauseSingleStorySession() {
        if (!singleStorySessionInProgress || pauseRequested) {
            return;
        }

        pauseRequested = true;
        setSingleStorySessionMessage("Pause requested. Ralphy will stop after "
                + singleStorySessionTaskId
                + " completes.");
        renderSingleStorySession();
        renderStoryProgressDashboard();
    }

    private void launchStorySessionStep(PresetUseCase presetUseCase,
                                        ActiveProjectService.SingleStorySessionAvailability availability) {
        singleStorySessionInProgress = true;
        singleStorySessionTaskId = availability.story().taskId();
        activeSessionPresetUseCase = presetUseCase;
        setSingleStorySessionMessage(startSessionMessage(availability));
        rawOutputViewRadioButton.setSelected(true);
        runOutputPresentationState = RunOutputPresentationState.live(
                "Preparing " + availability.story().taskId(),
                availability.story().taskId() + " is queued for execution.",
                "",
                ""
        );
        renderSingleStorySession();
        renderStoryProgressDashboard();
        renderRunOutputView();

        CodexLauncherService.RunOutputListener runOutputListener = runOutputListener();
        CompletableFuture.supplyAsync(
                        () -> activeProjectService.startEligibleSingleStory(presetUseCase, runOutputListener),
                        backgroundExecutor
                )
                .whenComplete((result, throwable) -> Platform.runLater(() ->
                        handleStorySessionStepCompletion(presetUseCase, result, throwable)
                ));
    }

    private void handleStorySessionStepCompletion(PresetUseCase presetUseCase,
                                                  ActiveProjectService.SingleStoryStartResult result,
                                                  Throwable throwable) {
        if (result != null && result.launchResult() != null) {
            runOutputPresentationState = loadPersistedRunOutputPresentationState();
        }

        if (throwable != null) {
            finishStorySession("Single story session failed: " + throwable.getMessage(), false);
            return;
        }
        if (result == null || !result.successful()) {
            finishStorySession(result == null ? "Single story session failed." : result.detail(), false);
            return;
        }
        if (result.finalStatus() == PrdTaskStatus.FAILED) {
            finishStorySession(result.detail(), false);
            return;
        }

        ActiveProjectService.SingleStorySessionAvailability nextAvailability =
                activeProjectService.singleStorySessionAvailability(presetUseCase);
        if (pauseRequested) {
            pauseRequested = false;
            if (nextAvailability.startable()) {
                singleStorySessionInProgress = false;
                singleStorySessionTaskId = null;
                executionPaused = true;
                pausedSessionTaskId = nextAvailability.story().taskId();
                setSingleStorySessionMessage(pausedBeforeMessage(nextAvailability));
                renderActiveProject(activeProjectService.activeProject().orElse(null));
                return;
            }

            finishStorySession(sessionCompletionMessage(presetUseCase, result, nextAvailability), false);
            return;
        }

        if (nextAvailability.startable()) {
            launchStorySessionStep(presetUseCase, nextAvailability);
            return;
        }

        finishStorySession(sessionCompletionMessage(presetUseCase, result, nextAvailability), false);
    }

    private void finishStorySession(String message, boolean preservePausedState) {
        singleStorySessionInProgress = false;
        singleStorySessionTaskId = null;
        pauseRequested = false;
        if (!preservePausedState) {
            clearPausedSessionState();
        }
        setSingleStorySessionMessage(message);
        renderActiveProject(activeProjectService.activeProject().orElse(null));
    }

    private CodexLauncherService.RunOutputListener runOutputListener() {
        return new CodexLauncherService.RunOutputListener() {
            @Override
            public void onLaunchStarted(CodexLauncherService.CodexLaunchPlan launchPlan) {
                Platform.runLater(() -> {
                    runOutputPresentationState = RunOutputPresentationState.live(
                            "Streaming " + launchPlan.storyId() + " | RUNNING",
                            "Run " + launchPlan.runId()
                                    + " started in "
                                    + launchPlan.executionProfile().summary()
                                    + ".",
                            runOutputPresentationState.assistantSummary(),
                            runOutputPresentationState.rawOutput()
                    );
                    renderRunOutputView();
                });
            }

            @Override
            public void onStdout(String text) {
                Platform.runLater(() -> {
                    runOutputPresentationState = runOutputPresentationState.appendRawOutput(text);
                    renderRunOutputView();
                });
            }

            @Override
            public void onStderr(String text) {
                Platform.runLater(() -> {
                    runOutputPresentationState = runOutputPresentationState.appendRawOutput(
                            formatStderrOutput(text, runOutputPresentationState.rawOutput())
                    );
                    renderRunOutputView();
                });
            }
        };
    }

    private String startSessionMessage(ActiveProjectService.SingleStorySessionAvailability availability) {
        return joinMessageParts(
                skippedStoriesClause("Skipping", availability.skippedStories()),
                "Starting " + availability.story().taskId() + "..."
        );
    }

    private String pausedBeforeMessage(ActiveProjectService.SingleStorySessionAvailability availability) {
        return joinMessageParts(
                "Paused before " + pausedSessionTaskId + ".",
                skippedStoriesClause("Skipping", availability.skippedStories()),
                "Resume when ready."
        );
    }

    private String sessionCompletionMessage(PresetUseCase presetUseCase,
                                            ActiveProjectService.SingleStoryStartResult result,
                                            ActiveProjectService.SingleStorySessionAvailability nextAvailability) {
        return switch (nextAvailability.state()) {
            case NO_ELIGIBLE_STORY -> joinMessageParts(
                    presetUseCase == PresetUseCase.RETRY_FIX ? "Retry queue complete." : "Play complete.",
                    skippedStoriesClause("Skipped", nextAvailability.skippedStories()),
                    "No additional eligible stories remain."
            );
            case REVIEW_REQUIRED, BLOCKED -> hasText(nextAvailability.detail())
                    ? nextAvailability.detail()
                    : result.detail();
            case READY -> result.detail();
        };
    }

    private void setSingleStorySessionMessage(String message) {
        boolean visible = hasText(message);
        singleStorySessionMessageLabel.setText(visible ? message : "");
        singleStorySessionMessageLabel.setVisible(visible);
    }

    private String formatStderrOutput(String text, String existingRawOutput) {
        if (!hasText(text)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (!hasText(existingRawOutput) || !existingRawOutput.contains(STDERR_SECTION_HEADER)) {
            if (hasText(existingRawOutput) && !existingRawOutput.endsWith(System.lineSeparator())) {
                builder.append(System.lineSeparator());
            }
            if (hasText(existingRawOutput)) {
                builder.append(System.lineSeparator());
            }
            builder.append(STDERR_SECTION_HEADER).append(System.lineSeparator());
        }
        builder.append(text);
        return builder.toString();
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

    private void configurePresetCatalog() {
        configurePresetToggle(prdCreationPresetRadioButton, PresetUseCase.PRD_CREATION);
        configurePresetToggle(storyImplementationPresetRadioButton, PresetUseCase.STORY_IMPLEMENTATION);
        configurePresetToggle(retryFixPresetRadioButton, PresetUseCase.RETRY_FIX);
        configurePresetToggle(runSummaryPresetRadioButton, PresetUseCase.RUN_SUMMARY);
        presetCatalogToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            renderPresetPreview(selectedPresetUseCase());
            renderSingleStorySession();
        });
        presetPromptPreviewArea.setEditable(false);
        prdCreationPresetRadioButton.setSelected(true);
        renderPresetPreview(PresetUseCase.PRD_CREATION);
    }

    private void configurePresetToggle(RadioButton radioButton, PresetUseCase useCase) {
        radioButton.setToggleGroup(presetCatalogToggleGroup);
        radioButton.setUserData(useCase);
    }

    private PresetUseCase selectedPresetUseCase() {
        if (presetCatalogToggleGroup.getSelectedToggle() == null) {
            return PresetUseCase.PRD_CREATION;
        }
        return (PresetUseCase) presetCatalogToggleGroup.getSelectedToggle().getUserData();
    }

    private PresetUseCase sessionPresetUseCase() {
        if ((singleStorySessionInProgress || pauseRequested || executionPaused) && activeSessionPresetUseCase != null) {
            return activeSessionPresetUseCase;
        }
        return selectedPresetUseCase();
    }

    private String sessionActionLabel(PresetUseCase presetUseCase) {
        return presetUseCase == PresetUseCase.RETRY_FIX ? "Retry Failed Story" : "Play";
    }

    private String sessionResumeLabel(PresetUseCase presetUseCase) {
        return presetUseCase == PresetUseCase.RETRY_FIX ? "Resume Retry" : "Resume Play";
    }

    private String runningSessionDetail(PresetUseCase presetUseCase, String storyId) {
        return presetUseCase == PresetUseCase.RETRY_FIX
                ? storyId + " is running with Retry Failed Story."
                : storyId + " is running in Play mode.";
    }

    private String skippedStoriesClause(String verb, List<ActiveProjectService.StorySkip> skippedStories) {
        if (skippedStories == null || skippedStories.isEmpty()) {
            return "";
        }

        List<String> skippedDetails = new ArrayList<>(skippedStories.size());
        for (ActiveProjectService.StorySkip skippedStory : skippedStories) {
            skippedDetails.add(skippedStory.taskId() + " (" + skippedStory.reason() + ")");
        }
        return verb + " " + String.join(", ", skippedDetails) + ".";
    }

    private String joinMessageParts(String... parts) {
        List<String> populatedParts = new ArrayList<>();
        for (String part : parts) {
            if (hasText(part)) {
                populatedParts.add(part.trim());
            }
        }
        return String.join(" ", populatedParts);
    }

    private void renderPresetPreview(PresetUseCase useCase) {
        BuiltInPreset preset = presetCatalogService.defaultPreset(useCase);
        presetPreviewNameLabel.setText(preset.displayName());
        presetPreviewVersionLabel.setText("Preset ID " + preset.presetId() + " | Version " + preset.version());
        presetPreviewOverviewLabel.setText(preset.overview());
        presetRequiredSkillsValueLabel.setText(formatMetadataList(
                preset.requiredSkills(),
                "No required skills recorded for this preset."
        ));
        presetAssumptionsValueLabel.setText(formatMetadataList(
                preset.operatingAssumptions(),
                "No operating assumptions recorded for this preset."
        ));
        presetPromptPreviewArea.setText(preset.promptPreview());
    }

    private void configurePrdInterview() {
        prdInterviewAnswerArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (renderingPrdInterview || activeProjectService.activeProject().isEmpty()) {
                return;
            }
            setPrdInterviewDraftState(PRD_INTERVIEW_UNSAVED_CHANGES_MESSAGE);
        });

        List<Button> questionButtons = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < prdInterviewService.questionCount(); questionIndex++) {
            PrdInterviewQuestion question = prdInterviewService.questionForIndex(questionIndex);
            Button questionButton = new Button();
            questionButton.setId("prdInterviewQuestion" + capitalize(question.questionId()));
            questionButton.getStyleClass().add("interview-step-button");
            questionButton.setMaxWidth(Double.MAX_VALUE);
            questionButton.setWrapText(true);
            int targetQuestionIndex = questionIndex;
            questionButton.setOnAction(event -> navigateToPrdInterviewQuestion(targetQuestionIndex));
            questionButtons.add(questionButton);
        }

        prdInterviewQuestionButtons.clear();
        prdInterviewQuestionButtons.addAll(questionButtons);
        prdInterviewQuestionsContainer.getChildren().setAll(questionButtons);
        renderPrdInterview(activeProjectService.activeProject().orElse(null) == null
                ? null
                : activeProjectService.prdInterviewDraft().orElse(PrdInterviewDraft.empty()));
    }

    private void configurePrdDocumentEditor() {
        prdDocumentPreviewArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (renderingPrdDocument || activeProjectService.activeProject().isEmpty()) {
                return;
            }
            if (activeProjectService.activePrdMarkdown().isEmpty()) {
                return;
            }

            boolean dirty = !normalizeEditorLineEndings(newValue).equals(prdDocumentEditorBaseline);
            setPrdDocumentDirty(dirty);
            if (dirty) {
                setPrdDocumentState(PRD_DOCUMENT_UNSAVED_CHANGES_MESSAGE);
            } else {
                setPrdDocumentState(READY_PRD_DOCUMENT_MESSAGE);
            }
        });
    }

    private void navigateToPrdInterviewQuestion(int targetQuestionIndex) {
        if (activeProjectService.activeProject().isEmpty()) {
            return;
        }

        persistCurrentPrdInterviewDraft(targetQuestionIndex, false);
    }

    private boolean persistCurrentPrdInterviewDraft(int targetQuestionIndex, boolean manualSave) {
        if (activeProjectService.activeProject().isEmpty()) {
            return false;
        }

        int normalizedTargetQuestionIndex = prdInterviewService.normalizeQuestionIndex(targetQuestionIndex);
        PrdInterviewQuestion currentQuestion = prdInterviewService.questionForIndex(currentPrdInterviewQuestionIndex);
        PrdInterviewDraft replacementDraft = currentPrdInterviewDraft.withAnswer(
                currentQuestion,
                prdInterviewAnswerArea.getText(),
                normalizedTargetQuestionIndex
        );

        ActiveProjectService.PrdInterviewDraftSaveResult saveResult =
                activeProjectService.savePrdInterviewDraft(replacementDraft);
        if (!saveResult.successful()) {
            setPrdInterviewDraftState(saveResult.message());
            return false;
        }

        currentPrdInterviewDraft = prdInterviewService.normalizeDraft(saveResult.draft());
        currentPrdInterviewQuestionIndex = currentPrdInterviewDraft.selectedQuestionIndex();
        renderCurrentPrdInterviewQuestion();
        setPrdInterviewDraftState((manualSave ? "Draft saved." : "Answer saved.")
                + " Last updated " + currentPrdInterviewDraft.updatedAt() + ".");
        return true;
    }

    private void renderPrdInterview(PrdInterviewDraft draft) {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        prdInterviewCard.setDisable(!activeProjectPresent);
        prdInterviewAnswerArea.setDisable(!activeProjectPresent);
        prdInterviewSaveButton.setDisable(!activeProjectPresent);

        if (!activeProjectPresent) {
            currentPrdInterviewDraft = PrdInterviewDraft.empty();
            currentPrdInterviewQuestionIndex = 0;
            prdInterviewSummaryLabel.setText(NO_ACTIVE_PRD_INTERVIEW_SUMMARY);
            prdInterviewQuestionCounterLabel.setText("Question sequence ready");
            prdInterviewSectionLabel.setText("PRD Interview");
            prdInterviewTitleLabel.setText("Active project required");
            prdInterviewPromptLabel.setText(NO_ACTIVE_PRD_INTERVIEW_PROMPT);
            prdInterviewGuidanceLabel.setText(PRD_INTERVIEW_DRAFT_LOCATION_DETAIL);
            renderingPrdInterview = true;
            prdInterviewAnswerArea.clear();
            renderingPrdInterview = false;
            prdInterviewPreviousButton.setDisable(true);
            prdInterviewNextButton.setDisable(true);
            refreshPrdInterviewQuestionButtons(false);
            setPrdInterviewDraftState(NO_ACTIVE_PRD_INTERVIEW_SUMMARY);
            return;
        }

        currentPrdInterviewDraft = prdInterviewService.normalizeDraft(draft == null ? PrdInterviewDraft.empty() : draft);
        currentPrdInterviewQuestionIndex =
                prdInterviewService.normalizeQuestionIndex(currentPrdInterviewDraft.selectedQuestionIndex());
        renderCurrentPrdInterviewQuestion();
        if (currentPrdInterviewDraft.answeredQuestionCount() == 0) {
            setPrdInterviewDraftState(PRD_INTERVIEW_DRAFT_LOCATION_DETAIL);
        } else {
            setPrdInterviewDraftState("Draft restored. Last updated " + currentPrdInterviewDraft.updatedAt() + ".");
        }
    }

    private boolean persistCurrentPrdDocument(boolean manualSave) {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            if (manualSave) {
                setPrdDocumentState(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
                return false;
            }
            return true;
        }

        if (activeProjectService.activePrdMarkdown().isEmpty()) {
            if (manualSave) {
                setPrdDocumentState(NO_ACTIVE_PRD_TO_SAVE_MESSAGE);
                return false;
            }
            return true;
        }

        if (!prdDocumentDirty) {
            if (manualSave) {
                setPrdDocumentState("Markdown PRD already matches the saved file.");
            }
            return true;
        }

        String markdown = restoreDocumentLineEndings(prdDocumentPreviewArea.getText(), prdDocumentLineSeparator);
        ActiveProjectService.ActivePrdSaveResult saveResult = activeProjectService.saveActivePrd(markdown);
        if (!saveResult.successful()) {
            setPrdDocumentState(saveResult.message());
            return false;
        }

        renderGeneratedPrd(activeProject.get());
        renderPrdValidation();
        renderSingleStorySession();
        renderStoryProgressDashboard();
        setPrdDocumentState((manualSave ? "Markdown PRD saved to " : "Pending Markdown PRD edits saved to ")
                + saveResult.path() + ".");
        return true;
    }

    private void renderGeneratedPrd(ActiveProject activeProject) {
        boolean activeProjectPresent = activeProject != null;
        prdDocumentCard.setDisable(!activeProjectPresent);
        generatePrdButton.setDisable(!activeProjectPresent);
        importPrdDocumentButton.setDisable(!activeProjectPresent);
        importPrdJsonButton.setDisable(!activeProjectPresent);
        exportPrdDocumentButton.setDisable(true);
        savePrdDocumentButton.setDisable(true);
        setPrdDocumentDirty(false);

        if (!activeProjectPresent) {
            prdDocumentPathField.clear();
            applyPrdDocumentToEditor("");
            prdDocumentPreviewArea.setDisable(true);
            prdDocumentPreviewArea.setEditable(false);
            setPrdDocumentState(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
            return;
        }

        prdDocumentPathField.setText(activeProject.activePrdPath().toString());
        Optional<String> activePrdMarkdown = activeProjectService.activePrdMarkdown();
        if (activePrdMarkdown.isEmpty()) {
            applyPrdDocumentToEditor("");
            prdDocumentPreviewArea.setDisable(true);
            prdDocumentPreviewArea.setEditable(false);
            setPrdDocumentState(EMPTY_PRD_DOCUMENT_MESSAGE);
            return;
        }

        prdDocumentPreviewArea.setDisable(false);
        prdDocumentPreviewArea.setEditable(true);
        exportPrdDocumentButton.setDisable(false);
        applyPrdDocumentToEditor(activePrdMarkdown.get());
        setPrdDocumentState(READY_PRD_DOCUMENT_MESSAGE);
    }

    private void applyPrdDocumentToEditor(String markdown) {
        String value = markdown == null ? "" : markdown;
        prdDocumentLineSeparator = detectLineSeparator(value);
        prdDocumentEditorBaseline = normalizeEditorLineEndings(value);
        renderingPrdDocument = true;
        prdDocumentPreviewArea.setText(value);
        renderingPrdDocument = false;
        setPrdDocumentDirty(false);
    }

    private void setPrdDocumentDirty(boolean dirty) {
        prdDocumentDirty = dirty;
        boolean hasSavedPrd = activeProjectService.activeProject().isPresent()
                && activeProjectService.activePrdMarkdown().isPresent();
        savePrdDocumentButton.setDisable(!hasSavedPrd || !dirty);
    }

    private void renderCurrentPrdInterviewQuestion() {
        PrdInterviewQuestion question = prdInterviewService.questionForIndex(currentPrdInterviewQuestionIndex);
        prdInterviewSummaryLabel.setText(currentPrdInterviewDraft.answeredQuestionCount()
                + " of "
                + prdInterviewService.questionCount()
                + " questions answered. Draft answers can be revisited before PRD generation.");
        prdInterviewQuestionCounterLabel.setText("Question "
                + (currentPrdInterviewQuestionIndex + 1)
                + " of "
                + prdInterviewService.questionCount());
        prdInterviewSectionLabel.setText(question.sectionTitle());
        prdInterviewTitleLabel.setText(question.title());
        prdInterviewPromptLabel.setText(question.prompt());
        prdInterviewGuidanceLabel.setText(question.guidance());
        renderingPrdInterview = true;
        prdInterviewAnswerArea.setText(currentPrdInterviewDraft.answerFor(question.questionId()));
        renderingPrdInterview = false;
        prdInterviewPreviousButton.setDisable(currentPrdInterviewQuestionIndex == 0);
        prdInterviewNextButton.setDisable(currentPrdInterviewQuestionIndex >= prdInterviewService.questionCount() - 1);
        refreshPrdInterviewQuestionButtons(true);
    }

    private void refreshPrdInterviewQuestionButtons(boolean activeProjectPresent) {
        for (int questionIndex = 0; questionIndex < prdInterviewQuestionButtons.size(); questionIndex++) {
            PrdInterviewQuestion question = prdInterviewService.questionForIndex(questionIndex);
            Button questionButton = prdInterviewQuestionButtons.get(questionIndex);
            boolean answered = activeProjectPresent && currentPrdInterviewDraft.hasAnswerFor(question.questionId());
            boolean activeQuestion = activeProjectPresent && questionIndex == currentPrdInterviewQuestionIndex;
            questionButton.setDisable(!activeProjectPresent);
            questionButton.setText((answered ? "Complete" : "Pending")
                    + " | "
                    + (questionIndex + 1)
                    + ". "
                    + question.title());
            questionButton.getStyleClass().removeAll(
                    ACTIVE_INTERVIEW_STEP_STYLE_CLASS,
                    COMPLETE_INTERVIEW_STEP_STYLE_CLASS
            );
            if (answered) {
                questionButton.getStyleClass().add(COMPLETE_INTERVIEW_STEP_STYLE_CLASS);
            }
            if (activeQuestion) {
                questionButton.getStyleClass().add(ACTIVE_INTERVIEW_STEP_STYLE_CLASS);
            }
        }
    }

    private String formatMetadataList(List<String> values, String emptyMessage) {
        if (values == null || values.isEmpty()) {
            return emptyMessage;
        }
        return values.stream()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse(emptyMessage);
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

    private String normalizeEditorLineEndings(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String restoreDocumentLineEndings(String value, String lineSeparator) {
        String normalizedValue = normalizeEditorLineEndings(value);
        String targetLineSeparator = hasText(lineSeparator) ? lineSeparator : System.lineSeparator();
        return normalizedValue.replace("\n", targetLineSeparator);
    }

    private String detectLineSeparator(String value) {
        if (value == null || value.isEmpty()) {
            return System.lineSeparator();
        }
        if (value.contains("\r\n")) {
            return "\r\n";
        }
        if (value.contains("\n")) {
            return "\n";
        }
        if (value.contains("\r")) {
            return "\r";
        }
        return System.lineSeparator();
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

    private void setPrdInterviewDraftState(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        prdInterviewDraftStateLabel.setText(hasMessage ? message : "");
        prdInterviewDraftStateLabel.setVisible(hasMessage);
    }

    private void setPrdDocumentState(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        prdDocumentStateLabel.setText(hasMessage ? message : "");
        prdDocumentStateLabel.setVisible(hasMessage);
    }

    private String formatPrdJsonImportMessage(ActiveProjectService.PrdJsonImportResult importResult) {
        if (importResult.conflictDetails().isEmpty()) {
            return importResult.message();
        }

        return importResult.message()
                + System.lineSeparator()
                + "Conflicts:"
                + System.lineSeparator()
                + importResult.conflictDetails().stream()
                .map(detail -> "- " + detail)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record ShellSection(String title, String workspaceText, String statusText) {
    }

    private enum RunOutputView {
        ASSISTANT_SUMMARY,
        RAW_OUTPUT
    }

    private enum StoryDashboardState {
        PENDING,
        BLOCKED,
        RUNNING,
        PASSED,
        FAILED,
        PAUSED
    }

    private record DashboardStoryState(PrdTaskRecord task, StoryDashboardState state) {
    }

    private record StoryProgressDashboard(String summary,
                                          String detail,
                                          String currentStory,
                                          String overallCounts,
                                          int pendingCount,
                                          int blockedCount,
                                          int runningCount,
                                          int passedCount,
                                          int failedCount,
                                          int pausedCount) {
    }

    private record RunOutputPresentationState(boolean available,
                                              boolean active,
                                              String summary,
                                              String detail,
                                              String assistantSummary,
                                              String rawOutput) {
        private static RunOutputPresentationState empty() {
            return new RunOutputPresentationState(
                    false,
                    false,
                    NO_PERSISTED_RUN_OUTPUT_SUMMARY,
                    NO_PERSISTED_RUN_OUTPUT_DETAIL,
                    "",
                    ""
            );
        }

        private static RunOutputPresentationState live(String summary,
                                                       String detail,
                                                       String assistantSummary,
                                                       String rawOutput) {
            return new RunOutputPresentationState(true, true, summary, detail, assistantSummary, rawOutput);
        }

        private RunOutputPresentationState appendRawOutput(String additionalOutput) {
            if (additionalOutput == null || additionalOutput.isBlank()) {
                return this;
            }
            return new RunOutputPresentationState(
                    available,
                    active,
                    summary,
                    detail,
                    assistantSummary,
                    rawOutput + additionalOutput
            );
        }
    }
}
