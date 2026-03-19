package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.Node;
import javafx.scene.Parent;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AppShellController {
    private static final ObjectMapper RUN_OUTPUT_OBJECT_MAPPER = new ObjectMapper();
    private static final double DEFAULT_RUN_OUTPUT_VIEWPORT_HEIGHT = 560.0;
    private static final double STRUCTURED_RUN_OUTPUT_VIEWPORT_EXTRA_HEIGHT = 160.0;
    private static final double SCROLL_BOTTOM_PIN_THRESHOLD_PX = 48.0;
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
            "Open or create a repository to check native Codex readiness.";
    private static final String NO_NATIVE_PREFLIGHT_SUMMARY = "Native preflight not run";
    private static final String NO_NATIVE_PREFLIGHT_DETAIL =
            "Run native preflight before starting an execution loop.";
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
            "Choose or restore an active repository before starting the prompt-first PRD planning workflow.";
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
    private static final String NO_ACTIVE_RUN_HISTORY_SUMMARY = "No active project";
    private static final String NO_ACTIVE_RUN_HISTORY_DETAIL =
            "Open a repository before reviewing persisted story attempts and artifacts.";
    private static final String NO_PERSISTED_RUN_HISTORY_SUMMARY = "No run history yet";
    private static final String NO_PERSISTED_RUN_HISTORY_DETAIL =
            "Start a story to build persisted attempt history for this project.";
    private static final String NO_SELECTED_HISTORY_ARTIFACT_SUMMARY = "No history artifact selected";
    private static final String NO_SELECTED_HISTORY_ARTIFACT_DETAIL =
            "Choose a stored prompt, log, or summary from the run history list.";
    private static final String MISSING_HISTORY_ARTIFACT_MESSAGE =
            "The selected artifact could not be found on disk.";
    private static final String INVALID_HISTORY_ARTIFACT_MESSAGE =
            "The selected artifact path is invalid and could not be opened.";
    private static final String UNREADABLE_HISTORY_ARTIFACT_MESSAGE =
            "The selected artifact could not be read.";
    private static final String PENDING_ASSISTANT_SUMMARY_MESSAGE =
            "Waiting for the final assistant summary. Switch to Raw Output to follow the live stream.";
    private static final String PENDING_RAW_OUTPUT_MESSAGE = "Waiting for the agent CLI to emit live output...";
    private static final String MISSING_ASSISTANT_SUMMARY_MESSAGE =
            "No final assistant summary is stored for this run.";
    private static final String MISSING_RAW_OUTPUT_MESSAGE = "No raw output is stored for this run.";
    private static final String STDERR_SECTION_HEADER = "[stderr]";
    private static final int COMMAND_OUTPUT_PREVIEW_LINE_COUNT = 5;
    private static final ShellSection PROJECTS_SECTION = new ShellSection(
            "Projects",
            "Repository onboarding, recent projects, and diagnostics will appear here.",
            "Projects workspace ready."
    );
    private static final ShellSection AGENT_CONFIGURATION_SECTION = new ShellSection(
            "Agent Configuration",
            "User-scoped execution profile and preflight diagnostics appear here.",
            "Agent configuration workspace ready."
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
    private final PrdPlannerService prdPlannerService;
    private final ExecutionAgentModelCatalogService executionAgentModelCatalogService;
    private final UserPreferencesSettingsService userPreferencesSettingsService;
    private final MarkdownPrdFileChooser markdownPrdFileChooser;
    private final PrdJsonFileChooser prdJsonFileChooser;
    private final RepositoryDirectoryChooser repositoryDirectoryChooser;
    private final Executor backgroundExecutor;
    private final Executor modelCatalogExecutor;
    private final HostOperatingSystem hostOperatingSystem;
    private final ToggleGroup executionProfileToggleGroup = new ToggleGroup();
    private final ToggleGroup presetCatalogToggleGroup = new ToggleGroup();
    private final ToggleGroup runOutputViewToggleGroup = new ToggleGroup();
    private final Object liveRunOutputLock = new Object();
    private final StringBuilder liveRunOutputBuffer = new StringBuilder();
    private final StringBuilder pendingLiveRunOutputDelta = new StringBuilder();
    private final List<Button> prdInterviewQuestionButtons = new ArrayList<>();
    private PresetUseCase lastExecutionWorkflowPreset = PresetUseCase.STORY_IMPLEMENTATION;
    private PrdInterviewDraft currentPrdInterviewDraft = PrdInterviewDraft.empty();
    private PrdPlanningSession currentPrdPlanningSession = PrdPlanningSession.empty();
    private Button activeWorkspaceNavButton;
    private int currentPrdInterviewQuestionIndex;
    private boolean renderingPrdInterview;
    private boolean renderingPrdDocument;
    private boolean prdDocumentDirty;
    private boolean prdPlannerRequestInProgress;
    private boolean singleStorySessionInProgress;
    private boolean pauseRequested;
    private boolean executionPaused;
    private boolean liveRunOutputHasStderrSection;
    private boolean liveRunOutputFlushScheduled;
    private boolean liveRawOutputTextAreaInitialized;
    private boolean structuredRunOutputPinnedToBottom = true;
    private String singleStorySessionTaskId;
    private String pausedSessionTaskId;
    private PresetUseCase activeSessionPresetUseCase;
    private String pendingPrdPlannerUserMessage = "";
    private String prdDocumentEditorBaseline = "";
    private String prdDocumentLineSeparator = System.lineSeparator();
    private final Map<String, StructuredCommandOutputViewState> structuredRunOutputCommandViewStates = new HashMap<>();
    private RunOutputPresentationState runOutputPresentationState = RunOutputPresentationState.empty();
    private RunHistoryArtifactViewerState runHistoryArtifactViewerState = RunHistoryArtifactViewerState.empty();
    private List<ExecutionAgentProviderChoice> availableExecutionAgentProviderChoices = List.of();

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
    private ScrollPane workspaceScrollPane;
    @FXML
    private VBox workspacePane;

    @FXML
    private HBox projectsWorkspaceRow;

    @FXML
    private Label executionProfileMessageLabel;

    @FXML
    private Button agentConfigurationNavButton;

    @FXML
    private VBox projectContextCard;

    @FXML
    private VBox agentConfigurationProfileCard;

    @FXML
    private VBox planningAgentSettingsCard;

    @FXML
    private VBox executionAgentSettingsCard;

    @FXML
    private VBox prdValidationCard;

    @FXML
    private ComboBox<ExecutionAgentProviderChoice> executionSettingsProviderComboBox;

    @FXML
    private ComboBox<ExecutionModelChoice> executionSettingsModelComboBox;

    @FXML
    private ComboBox<ThinkingLevelChoice> executionThinkingLevelComboBox;

    @FXML
    private Label executionSettingsStatusLabel;

    @FXML
    private ComboBox<ExecutionAgentProviderChoice> planningSettingsProviderComboBox;

    @FXML
    private ComboBox<ExecutionModelChoice> planningSettingsModelComboBox;

    @FXML
    private ComboBox<ThinkingLevelChoice> planningThinkingLevelComboBox;

    @FXML
    private Label planningSettingsStatusLabel;

    @FXML
    private Label executionAgentSelectionSummaryLabel;

    @FXML
    private Label executionAgentSelectionDetailLabel;

    @FXML
    private Label executionProfileSummaryLabel;

    @FXML
    private Label singleStorySessionDetailLabel;

    @FXML
    private Label singleStorySessionMessageLabel;

    @FXML
    private HBox singleStorySessionProgressRow;

    @FXML
    private ProgressIndicator singleStorySessionProgressIndicator;

    @FXML
    private Label singleStorySessionProgressLabel;

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
    private RadioButton structuredRunOutputViewRadioButton;

    @FXML
    private Label runOutputSummaryLabel;

    @FXML
    private TextArea runOutputTextArea;

    @FXML
    private ScrollPane runOutputStructuredScrollPane;

    @FXML
    private VBox runOutputStructuredContainer;

    @FXML
    private VBox runOutputCard;

    @FXML
    private VBox runHistoryEntriesContainer;

    @FXML
    private VBox runHistoryCard;

    @FXML
    private Label runHistorySummaryLabel;

    @FXML
    private Label runHistoryDetailLabel;

    @FXML
    private Label runHistoryArtifactSummaryLabel;

    @FXML
    private TextField runHistoryArtifactPathField;

    @FXML
    private TextArea runHistoryArtifactTextArea;

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
    private VBox wslExecutionProfileFieldsContainer;

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
    private VBox livePrdPlannerCard;

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
    private Button prdPlannerApplyDraftButton;

    @FXML
    private Button prdPlannerClearButton;

    @FXML
    private Label prdPlannerAgentSummaryLabel;

    @FXML
    private Label prdPlannerAgentDetailLabel;

    @FXML
    private Label prdPlannerDetailLabel;

    @FXML
    private TextArea prdPlannerInputArea;

    @FXML
    private Label prdPlannerMessageLabel;

    @FXML
    private Label prdPlannerProgressLabel;

    @FXML
    private HBox prdPlannerProgressRow;

    @FXML
    private ProgressIndicator prdPlannerProgressIndicator;

    @FXML
    private Button prdPlannerSendButton;

    @FXML
    private Label prdPlannerSummaryLabel;

    @FXML
    private TextArea prdPlannerTranscriptArea;

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
    private Button saveExecutionAgentSettingsButton;

    @FXML
    private Button savePlanningAgentSettingsButton;

    @FXML
    private RadioButton retryFixPresetRadioButton;

    @FXML
    private Label statusLabel;

    @FXML
    private VBox singleStorySessionCard;

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
    private VBox storyProgressDashboardCard;

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
    private VBox wslPreflightSection;

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
                              PrdPlannerService prdPlannerService,
                              ExecutionAgentModelCatalogService executionAgentModelCatalogService,
                              UserPreferencesSettingsService userPreferencesSettingsService,
                              MarkdownPrdFileChooser markdownPrdFileChooser,
                              PrdJsonFileChooser prdJsonFileChooser,
                              RepositoryDirectoryChooser repositoryDirectoryChooser,
                              @Qualifier("ralphyBackgroundExecutor") Executor backgroundExecutor,
                              @Qualifier("ralphyModelCatalogExecutor") Executor modelCatalogExecutor) {
        this.shellDescriptor = shellDescriptor;
        this.activeProjectService = activeProjectService;
        this.presetCatalogService = presetCatalogService;
        this.prdMarkdownGenerator = prdMarkdownGenerator;
        this.prdInterviewService = prdInterviewService;
        this.prdPlannerService = prdPlannerService;
        this.executionAgentModelCatalogService = executionAgentModelCatalogService;
        this.userPreferencesSettingsService = userPreferencesSettingsService;
        this.markdownPrdFileChooser = markdownPrdFileChooser;
        this.prdJsonFileChooser = prdJsonFileChooser;
        this.repositoryDirectoryChooser = repositoryDirectoryChooser;
        this.backgroundExecutor = backgroundExecutor;
        this.modelCatalogExecutor = modelCatalogExecutor;
        this.hostOperatingSystem = HostOperatingSystem.detectRuntime();
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
        wslExecutionProfileFieldsContainer.managedProperty().bind(wslExecutionProfileFieldsContainer.visibleProperty());
        wslPreflightSection.managedProperty().bind(wslPreflightSection.visibleProperty());
        prdInterviewDraftStateLabel.managedProperty().bind(prdInterviewDraftStateLabel.visibleProperty());
        prdPlannerMessageLabel.managedProperty().bind(prdPlannerMessageLabel.visibleProperty());
        prdPlannerProgressRow.managedProperty().bind(prdPlannerProgressRow.visibleProperty());
        prdDocumentStateLabel.managedProperty().bind(prdDocumentStateLabel.visibleProperty());
        prdValidationErrorsLabel.managedProperty().bind(prdValidationErrorsLabel.visibleProperty());
        singleStorySessionMessageLabel.managedProperty().bind(singleStorySessionMessageLabel.visibleProperty());
        executionSettingsStatusLabel.managedProperty().bind(executionSettingsStatusLabel.visibleProperty());
        planningSettingsStatusLabel.managedProperty().bind(planningSettingsStatusLabel.visibleProperty());
        singleStorySessionProgressRow.managedProperty().bind(singleStorySessionProgressRow.visibleProperty());
        prdPlannerTranscriptArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (activeProjectService.activeProject().isPresent()) {
                scrollPrdPlannerTranscriptToBottom();
            }
        });
        nativeExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        wslExecutionProfileRadioButton.setToggleGroup(executionProfileToggleGroup);
        nativeExecutionProfileRadioButton.setText(hostOperatingSystem.nativeProfileLabel());
        boolean wslSupported = hostOperatingSystem.supportsWslProfiles();
        wslExecutionProfileRadioButton.setVisible(wslSupported);
        wslExecutionProfileRadioButton.setManaged(wslSupported);
        wslPreflightSection.setVisible(wslSupported);
        executionProfileToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                updateExecutionProfileFieldState()
        );
        assistantSummaryViewRadioButton.setToggleGroup(runOutputViewToggleGroup);
        rawOutputViewRadioButton.setToggleGroup(runOutputViewToggleGroup);
        structuredRunOutputViewRadioButton.setToggleGroup(runOutputViewToggleGroup);
        runOutputViewToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                renderRunOutputView()
        );
        configurePresetCatalog();
        configureAgentSettingsSelection();
        configurePrdInterview();
        configurePrdDocumentEditor();
        setSectionVisible(prdCreationPresetRadioButton, false);
        prdPlannerTranscriptArea.setEditable(false);
        prdPlannerProgressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        singleStorySessionProgressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        prdDocumentPathField.setEditable(false);
        runOutputTextArea.managedProperty().bind(runOutputTextArea.visibleProperty());
        runOutputTextArea.setEditable(false);
        runOutputStructuredScrollPane.managedProperty().bind(runOutputStructuredScrollPane.visibleProperty());
        runOutputStructuredScrollPane.vvalueProperty().addListener((observable, oldValue, newValue) ->
                updateStructuredRunOutputPinnedToBottomState()
        );
        runOutputStructuredScrollPane.viewportBoundsProperty().addListener((observable, oldValue, newValue) -> {
            if (selectedRunOutputView() == RunOutputView.STRUCTURED_OUTPUT
                    && runOutputPresentationState.active()
                    && structuredRunOutputPinnedToBottom) {
                scrollStructuredRunOutputToBottom();
            }
        });
        runOutputStructuredContainer.boundsInLocalProperty().addListener((observable, oldValue, newValue) -> {
            if (selectedRunOutputView() == RunOutputView.STRUCTURED_OUTPUT
                    && runOutputPresentationState.active()
                    && structuredRunOutputPinnedToBottom) {
                scrollStructuredRunOutputToBottom();
            }
        });
        configureRunOutputViewportSizing();
        runHistoryArtifactPathField.setEditable(false);
        runHistoryArtifactTextArea.setEditable(false);
        nativeExecutionProfileRadioButton.setSelected(true);
        renderActiveProject(activeProjectService.activeProject().orElse(null));
        setProjectValidationMessage(activeProjectService.startupRecoveryMessage());
        setExecutionProfileMessage("");
        setNativePreflightMessage("");
        setWslPreflightMessage("");
        setPrdPlannerMessage("");
        showProjects();
    }

    @FXML
    private void showProjects() {
        activateSection(PROJECTS_SECTION, projectsNavButton);
    }

    @FXML
    private void showAgentConfiguration() {
        activateSection(AGENT_CONFIGURATION_SECTION, agentConfigurationNavButton);
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
        refreshAvailableAgentProviderChoices();
        renderPrdValidation();
        renderSingleStorySession();
        setExecutionProfileMessage("");
    }

    @FXML
    private void runNativeWindowsPreflight() {
        ActiveProjectService.NativeWindowsPreflightRunResult runResult = activeProjectService.runNativeWindowsPreflight();
        renderNativeWindowsPreflight();
        refreshAvailableAgentProviderChoices();
        renderSingleStorySession();
        setNativePreflightMessage(runResult.message());
    }

    @FXML
    private void runWslPreflight() {
        ActiveProjectService.WslPreflightRunResult runResult = activeProjectService.runWslPreflight();
        renderWslPreflight();
        refreshAvailableAgentProviderChoices();
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
            renderRunHistory();
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
            renderRunHistory();
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
        renderRunHistory();
        setPrdDocumentState("Generated PRD saved to " + saveResult.path()
                + ". Edit it below and save when you refine the plan.");
    }

    @FXML
    private void sendPrdPlannerMessage() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdPlannerMessage(NO_ACTIVE_PRD_INTERVIEW_SUMMARY);
            return;
        }
        if (prdPlannerRequestInProgress) {
            return;
        }

        ExecutionAgentSelection planningAgentSelection = selectedPlanningAgentSelection();
        if (!planningAgentSelection.provider().executionSupported()) {
            setPrdPlannerMessage(planningAgentSelection.provider().displayName()
                    + " planning is not implemented yet.");
            renderPrdPlanner(currentPrdPlanningSession);
            return;
        }

        String userMessage = prdPlannerInputArea.getText();
        if (!hasText(userMessage)) {
            setPrdPlannerMessage("Enter a prompt or clarification before sending it to the planner.");
            return;
        }

        ExecutionProfile executionProfile = activeProjectService.executionProfile()
                .orElse(ExecutionProfile.nativeHost());
        ExecutionAgentSelection planningAgentSelectionForRequest = planningAgentSelection;
        PrdPlanningSession baseSession = currentPrdPlanningSession;
        pendingPrdPlannerUserMessage = userMessage.trim();
        currentPrdPlanningSession = baseSession.appendUserMessage(pendingPrdPlannerUserMessage);
        prdPlannerRequestInProgress = true;
        setPrdPlannerMessage("");
        prdPlannerInputArea.clear();
        renderPrdPlannerRequestState();
        ensurePrdPlannerVisible();
        CompletableFuture.supplyAsync(
                        () -> prdPlannerService.continueConversation(
                                activeProject.get(),
                                executionProfile,
                                baseSession,
                                pendingPrdPlannerUserMessage,
                                planningAgentSelectionForRequest,
                                CodexLauncherService.RunOutputListener.noop()
                        ),
                        backgroundExecutor
                )
                .whenComplete((result, throwable) -> Platform.runLater(() ->
                        handlePrdPlannerCompletion(result, throwable)
                ));
    }

    @FXML
    private void applyPlannerPrdDraft() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdPlannerMessage(NO_ACTIVE_PRD_DOCUMENT_MESSAGE);
            return;
        }
        if (!currentPrdPlanningSession.hasLatestPrdMarkdown()) {
            setPrdPlannerMessage("The planner has not produced a PRD draft yet.");
            return;
        }
        if (prdDocumentDirty) {
            setPrdDocumentState(PRD_DOCUMENT_SAVE_REQUIRED_BEFORE_IMPORT_MESSAGE);
            return;
        }

        ActiveProjectService.ActivePrdSaveResult saveResult =
                activeProjectService.saveActivePrd(currentPrdPlanningSession.latestPrdMarkdown());
        if (!saveResult.successful()) {
            setPrdPlannerMessage(saveResult.message());
            return;
        }

        if (!singleStorySessionInProgress && !pauseRequested && !executionPaused) {
            activeSessionPresetUseCase = null;
            lastExecutionWorkflowPreset = PresetUseCase.STORY_IMPLEMENTATION;
            selectWorkflowPreset(PresetUseCase.STORY_IMPLEMENTATION);
        }

        renderGeneratedPrd(activeProject.get());
        renderPrdValidation();
        renderSingleStorySession();
        renderStoryProgressDashboard();
        renderRunHistory();
        ensurePrdDocumentVisible();
        setPrdPlannerMessage("Applied the latest planner draft to " + saveResult.path() + ".");
    }

    @FXML
    private void clearPrdPlannerSession() {
        Optional<ActiveProject> activeProject = activeProjectService.activeProject();
        if (activeProject.isEmpty()) {
            setPrdPlannerMessage(NO_ACTIVE_PRD_INTERVIEW_SUMMARY);
            return;
        }
        ActiveProjectService.PrdPlanningSessionSaveResult saveResult =
                activeProjectService.savePrdPlanningSession(PrdPlanningSession.empty());
        if (!saveResult.successful()) {
            setPrdPlannerMessage(saveResult.message());
            return;
        }
        currentPrdPlanningSession = saveResult.session();
        pendingPrdPlannerUserMessage = "";
        prdPlannerInputArea.clear();
        renderPrdPlannerPreservingWorkspaceScroll(currentPrdPlanningSession);
        ensurePrdPlannerVisible();
        setPrdPlannerMessage("Planner session cleared.");
    }

    private void handlePrdPlannerCompletion(PrdPlannerService.PlannerTurnResult result, Throwable throwable) {
        prdPlannerRequestInProgress = false;
        pendingPrdPlannerUserMessage = "";
        if (throwable != null) {
            setPrdPlannerMessage("Planner request failed: " + throwable.getMessage());
            renderPrdPlannerPreservingWorkspaceScroll(currentPrdPlanningSession);
            return;
        }
        if (result == null) {
            setPrdPlannerMessage("Planner request failed.");
            renderPrdPlannerPreservingWorkspaceScroll(currentPrdPlanningSession);
            return;
        }

        if (result.session() != null) {
            ActiveProjectService.PrdPlanningSessionSaveResult saveResult =
                    activeProjectService.savePrdPlanningSession(result.session());
            if (saveResult.successful() && saveResult.session() != null) {
                currentPrdPlanningSession = saveResult.session();
            } else if (!saveResult.successful()) {
                setPrdPlannerMessage(saveResult.message());
            }
        }

        if (!result.successful()) {
            renderPrdPlannerPreservingWorkspaceScroll(currentPrdPlanningSession);
            setPrdPlannerMessage(result.message());
            return;
        }

        prdPlannerInputArea.clear();
        renderPrdPlannerPreservingWorkspaceScroll(currentPrdPlanningSession);
        if (hasText(result.latestPrdMarkdown())) {
            setPrdPlannerMessage("Planner produced a PRD draft. Review it in the transcript or apply it to the editor.");
        } else {
            setPrdPlannerMessage("Planner replied with the next clarification round.");
        }
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
        activeWorkspaceNavButton = activeButton;
        workspaceTitleLabel.setText(section.title());
        workspacePlaceholderLabel.setText(section.workspaceText());
        statusLabel.setText(section.statusText());
        updateActiveNavigationButton(activeButton);
        updateWorkspaceSectionVisibility(activeButton);
        syncWorkflowPresetForSection(activeButton);
    }

    private boolean updateWorkspaceSectionVisibility(Button activeButton) {
        boolean projectsSectionActive = activeButton == projectsNavButton;
        boolean agentConfigurationSectionActive = activeButton == agentConfigurationNavButton;
        boolean prdEditorSectionActive = activeButton == prdEditorNavButton;
        boolean executionSectionActive = activeButton == executionNavButton;

        boolean changed = false;
        changed |= setSectionVisible(projectsWorkspaceRow, projectsSectionActive);
        changed |= setSectionVisible(projectContextCard, projectsSectionActive);
        changed |= setSectionVisible(agentConfigurationProfileCard, agentConfigurationSectionActive);
        changed |= setSectionVisible(planningAgentSettingsCard, prdEditorSectionActive);
        changed |= setSectionVisible(prdValidationCard, prdEditorSectionActive);
        changed |= setSectionVisible(presetCatalogCard, executionSectionActive);
        changed |= setSectionVisible(executionAgentSettingsCard, executionSectionActive);
        changed |= setSectionVisible(livePrdPlannerCard, prdEditorSectionActive);
        changed |= setSectionVisible(prdInterviewCard, prdEditorSectionActive && shouldShowLegacyPrdInterview());
        changed |= setSectionVisible(prdDocumentCard, prdEditorSectionActive);
        changed |= setSectionVisible(singleStorySessionCard, executionSectionActive);
        changed |= setSectionVisible(runOutputCard, executionSectionActive);
        changed |= setSectionVisible(runHistoryCard, executionSectionActive);
        changed |= setSectionVisible(storyProgressDashboardCard, executionSectionActive);
        return changed;
    }

    private boolean setSectionVisible(Node section, boolean visible) {
        if (section == null) {
            return false;
        }
        boolean changed = section.isVisible() != visible || section.isManaged() != visible;
        section.setVisible(visible);
        section.setManaged(visible);
        return changed;
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
        return List.of(agentConfigurationNavButton, projectsNavButton, prdEditorNavButton, executionNavButton);
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
            resetLiveRunOutputBuffer();
            clearPausedSessionState();
            runOutputPresentationState = RunOutputPresentationState.empty();
            runHistoryArtifactViewerState = RunHistoryArtifactViewerState.empty();
            activeProjectNameLabel.setText(NO_ACTIVE_PROJECT_NAME);
            activeProjectPathLabel.setText(NO_ACTIVE_PROJECT_PATH);
            activeProjectStatusLabel.setText(NO_ACTIVE_PROJECT_STATUS);
            renderExecutionProfile(activeProjectService.executionProfile().orElse(ExecutionProfile.nativeHost()));
            renderExecutionOverview(null);
            renderNativeWindowsPreflight();
            renderWslPreflight();
            refreshAvailableAgentProviderChoices();
            renderPrdValidation();
            renderSingleStorySession();
            renderStoryProgressDashboard();
            renderRunOutputView();
            renderRunHistory();
            renderPrdPlanner(null);
            renderPrdInterview(null);
            renderGeneratedPrd(null);
            return;
        }

        refreshRunOutputPresentationState();
        resetRunHistoryArtifactViewerIfProjectChanged(activeProject);
        activeProjectNameLabel.setText(activeProject.displayName());
        activeProjectPathLabel.setText(activeProject.displayPath());
        activeProjectStatusLabel.setText(activeProject.displayName());
        renderExecutionProfile(activeProjectService.executionProfile().orElse(ExecutionProfile.nativeHost()));
        renderExecutionOverview(activeProject);
        renderNativeWindowsPreflight();
        renderWslPreflight();
        refreshAvailableAgentProviderChoices();
        renderPrdValidation();
        renderSingleStorySession();
        renderStoryProgressDashboard();
        renderRunOutputView();
        renderRunHistory();
        renderPrdPlanner(activeProjectService.prdPlanningSession().orElse(PrdPlanningSession.empty()));
        renderPrdInterview(activeProjectService.prdInterviewDraft().orElse(PrdInterviewDraft.empty()));
        renderGeneratedPrd(activeProject);
    }

    private void renderExecutionProfile(ExecutionProfile executionProfile) {
        setExecutionProfileMessage("");
        nativeExecutionProfileRadioButton.setDisable(false);
        wslExecutionProfileRadioButton.setDisable(!hostOperatingSystem.supportsWslProfiles());
        saveExecutionProfileButton.setDisable(false);

        if (executionProfile == null) {
            nativeExecutionProfileRadioButton.setSelected(true);
            wslDistributionField.clear();
            windowsPathPrefixField.clear();
            wslPathPrefixField.clear();
            executionProfileSummaryLabel.setText(ExecutionProfile.nativeHost().summary(hostOperatingSystem));
            updateExecutionProfileFieldState();
            return;
        }

        if (executionProfile.type() == ExecutionProfile.ProfileType.WSL && hostOperatingSystem.supportsWslProfiles()) {
            wslExecutionProfileRadioButton.setSelected(true);
        } else {
            nativeExecutionProfileRadioButton.setSelected(true);
            if (executionProfile.type() == ExecutionProfile.ProfileType.WSL
                    && !hostOperatingSystem.supportsWslProfiles()) {
                setExecutionProfileMessage("WSL profiles are only available when Ralphy is running on Windows.");
            }
        }
        wslDistributionField.setText(valueOrEmpty(executionProfile.wslDistribution()));
        windowsPathPrefixField.setText(valueOrEmpty(executionProfile.windowsPathPrefix()));
        wslPathPrefixField.setText(valueOrEmpty(executionProfile.wslPathPrefix()));
        ExecutionProfile visibleExecutionProfile = executionProfile.type() == ExecutionProfile.ProfileType.WSL
                && !hostOperatingSystem.supportsWslProfiles()
                ? ExecutionProfile.nativeHost()
                : executionProfile;
        executionProfileSummaryLabel.setText(visibleExecutionProfile.summary(hostOperatingSystem));
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
                activeProjectService.singleStorySessionAvailability(sessionPresetUseCase, selectedExecutionAgentSelection());
        ExecutionAgentSelection executionAgentSelection = selectedExecutionAgentSelection();
        boolean providerSupported = executionAgentSelection.provider().executionSupported();
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        executionAgentSelectionSummaryLabel.setText(describeAgentSelection(executionAgentSelection));
        executionAgentSelectionDetailLabel.setText(agentSelectionDetail(executionAgentSelection, StageConfigurationContext.EXECUTION));

        if (!providerSupported && !singleStorySessionInProgress) {
            singleStorySessionSummaryLabel.setText("Provider not supported");
            singleStorySessionDetailLabel.setText(executionAgentSelection.provider().displayName()
                    + " execution is not implemented yet. Switch provider to Codex.");
        } else if (pauseRequested && singleStorySessionInProgress && hasText(singleStorySessionTaskId)) {
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
        startSingleStoryButton.setDisable(!availability.startable()
                || singleStorySessionInProgress
                || !providerSupported);
        pauseSingleStoryButton.setText(pauseRequested ? "Pause Requested" : "Pause");
        pauseSingleStoryButton.setDisable(!singleStorySessionInProgress || pauseRequested);
        singleStorySessionProgressRow.setVisible(singleStorySessionInProgress);
        if (singleStorySessionInProgress && hasText(singleStorySessionTaskId)) {
            singleStorySessionProgressLabel.setText(pauseRequested
                    ? "Finishing " + singleStorySessionTaskId + " before pausing..."
                    : executionAgentSelection.provider().displayName() + " is running " + singleStorySessionTaskId + "...");
        } else {
            singleStorySessionProgressLabel.setText("");
        }
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

    private void renderRunHistory() {
        ActiveProjectService.RunHistoryReport runHistoryReport = activeProjectService.runHistory();
        runHistorySummaryLabel.setText(runHistoryReport.summary());
        runHistoryDetailLabel.setText(runHistoryReport.detail());
        runHistoryEntriesContainer.getChildren().setAll(runHistoryReport.entries().stream()
                .map(this::buildRunHistoryEntryCard)
                .toList());

        if (!runHistoryReport.available() || runHistoryReport.entries().isEmpty()) {
            runHistoryArtifactViewerState = RunHistoryArtifactViewerState.empty();
        }
        renderRunHistoryArtifactViewer();
    }

    private void renderRunHistoryArtifactViewer() {
        runHistoryArtifactSummaryLabel.setText(runHistoryArtifactViewerState.summary());
        runHistoryArtifactPathField.setText(runHistoryArtifactViewerState.path());
        runHistoryArtifactTextArea.setText(runHistoryArtifactViewerState.content());
    }

    private void resetRunHistoryArtifactViewerIfProjectChanged(ActiveProject activeProject) {
        if (activeProject == null || !hasText(runHistoryArtifactViewerState.path())) {
            return;
        }

        Optional<Path> selectedArtifactPath = toPath(runHistoryArtifactViewerState.path());
        if (selectedArtifactPath.isEmpty()
                || !selectedArtifactPath.get().startsWith(activeProject.ralphyDirectoryPath())) {
            runHistoryArtifactViewerState = RunHistoryArtifactViewerState.empty();
        }
    }

    private void renderPrdPlanner(PrdPlanningSession session) {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        livePrdPlannerCard.setDisable(!activeProjectPresent);
        boolean plannerRequestActive = prdPlannerRequestInProgress;
        ExecutionAgentSelection planningAgentSelection = selectedPlanningAgentSelection();
        boolean providerSupported = planningAgentSelection.provider().executionSupported();
        prdPlannerTranscriptArea.setDisable(!activeProjectPresent);
        prdPlannerInputArea.setDisable(!activeProjectPresent || plannerRequestActive);
        prdPlannerSendButton.setDisable(!activeProjectPresent || plannerRequestActive || !providerSupported);
        prdPlannerProgressRow.setVisible(activeProjectPresent && prdPlannerRequestInProgress);
        prdPlannerProgressLabel.setText(prdPlannerRequestInProgress
                ? "Waiting for " + planningAgentSelection.provider().displayName() + " to continue the planner..."
                : "");
        prdPlannerAgentSummaryLabel.setText(describeAgentSelection(planningAgentSelection));
        prdPlannerAgentDetailLabel.setText(agentSelectionDetail(planningAgentSelection, StageConfigurationContext.PLANNING));

        if (!activeProjectPresent) {
            currentPrdPlanningSession = PrdPlanningSession.empty();
            pendingPrdPlannerUserMessage = "";
            prdPlannerSummaryLabel.setText(NO_ACTIVE_PRD_INTERVIEW_SUMMARY);
            prdPlannerDetailLabel.setText(NO_ACTIVE_PRD_INTERVIEW_PROMPT);
            prdPlannerTranscriptArea.clear();
            prdPlannerInputArea.clear();
            prdPlannerApplyDraftButton.setDisable(true);
            prdPlannerClearButton.setDisable(true);
            return;
        }

        if (!providerSupported) {
            currentPrdPlanningSession = session == null ? PrdPlanningSession.empty() : session;
            prdPlannerSummaryLabel.setText("Planner provider not supported");
            prdPlannerDetailLabel.setText(planningAgentSelection.provider().displayName()
                    + " planning is not implemented yet. Choose Codex or GitHub Copilot in Planning Agent Settings.");
            prdPlannerTranscriptArea.setText(formatPrdPlannerTranscript(currentPrdPlanningSession));
            prdPlannerApplyDraftButton.setDisable(true);
            prdPlannerClearButton.setDisable(!currentPrdPlanningSession.hasMessages());
            return;
        }

        currentPrdPlanningSession = session == null ? PrdPlanningSession.empty() : session;
        prdPlannerTranscriptArea.setText(formatPrdPlannerTranscript(currentPrdPlanningSession));
        scrollPrdPlannerTranscriptToBottom();
        prdPlannerSendButton.setText(currentPrdPlanningSession.hasMessages() ? "Send Reply" : "Start Planner");
        prdPlannerApplyDraftButton.setDisable(
                plannerRequestActive || !currentPrdPlanningSession.hasLatestPrdMarkdown()
        );
        prdPlannerClearButton.setDisable(
                plannerRequestActive || !currentPrdPlanningSession.hasMessages()
        );

        if (!currentPrdPlanningSession.hasMessages()) {
            prdPlannerSummaryLabel.setText("Planner ready | 0/100");
            prdPlannerDetailLabel.setText(
                    "Send a feature prompt to start a live agent-backed PRD planning conversation."
            );
            return;
        }

        PlannerReadinessStatus readinessStatus = plannerReadinessStatus(currentPrdPlanningSession);
        if (prdPlannerRequestInProgress) {
            prdPlannerSummaryLabel.setText("Planner working | " + readinessStatus.score() + "/100");
            prdPlannerDetailLabel.setText("The latest message has been sent. Waiting for the configured planner to respond.");
            return;
        }

        if (currentPrdPlanningSession.hasLatestPrdMarkdown()) {
            prdPlannerSummaryLabel.setText("Draft readiness | " + readinessStatus.score() + "/100");
            prdPlannerDetailLabel.setText(readinessStatus.detail());
            return;
        }

        prdPlannerSummaryLabel.setText("Clarification in progress | " + readinessStatus.score() + "/100");
        prdPlannerDetailLabel.setText(readinessStatus.detail());
    }

    private String formatPrdPlannerTranscript(PrdPlanningSession session) {
        if (session == null || !session.hasMessages()) {
            return "No planner conversation yet. Start with a feature prompt.";
        }

        List<String> transcriptBlocks = new ArrayList<>();
        for (PrdPlanningSession.Message message : session.messages()) {
            transcriptBlocks.add(formatPrdPlannerTranscriptBlock(message.role(), message.createdAt(), message.content()));
        }
        if (prdPlannerRequestInProgress) {
            transcriptBlocks.add(formatPrdPlannerTranscriptBlock(
                    "assistant",
                    "Waiting for " + selectedPlanningAgentSelection().provider().displayName(),
                    "Planner is thinking..."
            ));
        }
        return String.join(System.lineSeparator() + System.lineSeparator(), transcriptBlocks);
    }

    private String formatPrdPlannerTranscriptBlock(String role, String timestamp, String content) {
        String participant = "user".equalsIgnoreCase(role) ? "You" : "Planner";
        String normalizedTimestamp = hasText(timestamp) ? timestamp : "Pending";
        return ("==== " + participant + " | " + normalizedTimestamp + " ===="
                + System.lineSeparator()
                + (content == null ? "" : content.trim())).trim();
    }

    private void scrollPrdPlannerTranscriptToBottom() {
        prdPlannerTranscriptArea.positionCaret(prdPlannerTranscriptArea.getLength());
        prdPlannerTranscriptArea.setScrollTop(Double.MAX_VALUE);
        prdPlannerTranscriptArea.setScrollLeft(0);
        Platform.runLater(() -> {
            prdPlannerTranscriptArea.positionCaret(prdPlannerTranscriptArea.getLength());
            prdPlannerTranscriptArea.setScrollTop(Double.MAX_VALUE);
            prdPlannerTranscriptArea.setScrollLeft(0);
            Platform.runLater(() -> {
                prdPlannerTranscriptArea.positionCaret(prdPlannerTranscriptArea.getLength());
                prdPlannerTranscriptArea.setScrollTop(Double.MAX_VALUE);
                prdPlannerTranscriptArea.setScrollLeft(0);
            });
        });
    }

    private void renderPrdPlannerRequestState() {
        boolean activeProjectPresent = activeProjectService.activeProject().isPresent();
        livePrdPlannerCard.setDisable(!activeProjectPresent);
        prdPlannerTranscriptArea.setDisable(!activeProjectPresent);
        prdPlannerInputArea.setDisable(!activeProjectPresent || prdPlannerRequestInProgress);
        prdPlannerSendButton.setDisable(!activeProjectPresent
                || prdPlannerRequestInProgress
                || !selectedPlanningAgentSelection().provider().executionSupported());
        prdPlannerProgressRow.setVisible(activeProjectPresent && prdPlannerRequestInProgress);
        prdPlannerProgressLabel.setText(prdPlannerRequestInProgress
                ? "Waiting for " + selectedPlanningAgentSelection().provider().displayName() + " to continue the planner..."
                : "");
        prdPlannerApplyDraftButton.setDisable(
                prdPlannerRequestInProgress || !currentPrdPlanningSession.hasLatestPrdMarkdown()
        );
        prdPlannerClearButton.setDisable(
                prdPlannerRequestInProgress || !currentPrdPlanningSession.hasMessages()
        );
    }

    private void refreshWorkspaceSectionVisibility() {
        if (activeWorkspaceNavButton == null) {
            return;
        }
        if (workspaceScrollPane == null || workspacePane == null) {
            updateWorkspaceSectionVisibility(activeWorkspaceNavButton);
            return;
        }
        double offsetFromTop = captureScrollOffsetFromTop(workspaceScrollPane);
        boolean visibilityChanged = updateWorkspaceSectionVisibility(activeWorkspaceNavButton);
        if (!visibilityChanged) {
            return;
        }
        restoreScrollOffsetFromTop(workspaceScrollPane, offsetFromTop);
    }

    private double captureScrollOffsetFromTop(ScrollPane scrollPane) {
        if (scrollPane == null || scrollPane.getContent() == null) {
            return 0.0;
        }
        double scrollableHeight = Math.max(
                scrollPane.getContent().getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight(),
                0.0
        );
        if (scrollableHeight <= 0.0) {
            return 0.0;
        }
        return Math.max(scrollPane.getVvalue(), 0.0) * scrollableHeight;
    }

    private void restoreScrollOffsetFromTop(ScrollPane scrollPane, double offsetFromTop) {
        if (scrollPane == null) {
            return;
        }
        double normalizedOffset = Math.max(offsetFromTop, 0.0);
        Platform.runLater(() -> Platform.runLater(() -> {
            if (scrollPane.getContent() == null) {
                return;
            }
            double scrollableHeight = Math.max(
                    scrollPane.getContent().getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight(),
                    0.0
            );
            if (scrollableHeight <= 0.0) {
                scrollPane.setVvalue(0.0);
                return;
            }
            scrollPane.setVvalue(Math.min(normalizedOffset / scrollableHeight, 1.0));
        }));
    }

    private boolean shouldShowLegacyPrdInterview() {
        return !prdPlannerRequestInProgress && !currentPrdPlanningSession.hasMessages();
    }

    private void syncWorkflowPresetForSection(Button activeButton) {
        if (activeButton == prdEditorNavButton) {
            return;
        }

        if (activeButton == executionNavButton) {
            selectWorkflowPreset(preferredExecutionWorkflowPreset());
            return;
        }

        renderPresetPreview(selectedPresetUseCase());
    }

    private void selectWorkflowPreset(PresetUseCase presetUseCase) {
        RadioButton radioButton = workflowPresetRadioButton(presetUseCase);
        if (radioButton == null) {
            return;
        }

        if (presetCatalogToggleGroup.getSelectedToggle() == radioButton) {
            renderPresetPreview(presetUseCase);
            renderSingleStorySession();
            return;
        }

        radioButton.setSelected(true);
    }

    private PresetUseCase preferredExecutionWorkflowPreset() {
        if (activeSessionPresetUseCase == PresetUseCase.STORY_IMPLEMENTATION
                || activeSessionPresetUseCase == PresetUseCase.RETRY_FIX
                || activeSessionPresetUseCase == PresetUseCase.RUN_SUMMARY) {
            return activeSessionPresetUseCase;
        }
        return lastExecutionWorkflowPreset;
    }

    private RadioButton workflowPresetRadioButton(PresetUseCase presetUseCase) {
        return switch (presetUseCase) {
            case PRD_CREATION -> prdCreationPresetRadioButton;
            case STORY_IMPLEMENTATION -> storyImplementationPresetRadioButton;
            case RETRY_FIX -> retryFixPresetRadioButton;
            case RUN_SUMMARY -> runSummaryPresetRadioButton;
        };
    }

    private PlannerReadinessStatus plannerReadinessStatus(PrdPlanningSession session) {
        if (session == null || !session.hasMessages()) {
            return new PlannerReadinessStatus(0, "Start with a feature prompt to begin the planning conversation.");
        }

        if (!session.hasLatestPrdMarkdown()) {
            return conversationReadinessStatus(session);
        }

        return draftReadinessStatus(session);
    }

    private PlannerReadinessStatus conversationReadinessStatus(PrdPlanningSession session) {
        String transcript = formatPrdPlannerTranscript(session).toLowerCase();
        int score = 20;
        if (session.messages().size() >= 2) {
            score += 10;
        }
        if (session.messages().size() >= 4) {
            score += 10;
        }

        List<String> missingTopics = new ArrayList<>();
        if (containsAny(transcript, "quality gate", "quality gates", "must pass", "verify")) {
            score += 20;
        } else {
            missingTopics.add("quality gates");
        }
        if (containsAny(transcript, "goal", "goals", "outcome", "success")) {
            score += 15;
        } else {
            missingTopics.add("goals and success signals");
        }
        if (containsAny(transcript, "scope", "out of scope", "non-goal", "non goal", "boundary")) {
            score += 15;
        } else {
            missingTopics.add("scope boundaries");
        }
        if (containsAny(transcript, "user story", "user stories", "target user", "persona", "who is this for")) {
            score += 10;
        } else {
            missingTopics.add("users and stories");
        }
        if (containsAny(transcript, "integration", "existing", "repository", "fit with")) {
            score += 10;
        } else {
            missingTopics.add("integration points");
        }

        String updatedAt = hasText(session.updatedAt()) ? "Last updated " + session.updatedAt() + ". " : "";
        if (missingTopics.isEmpty()) {
            return new PlannerReadinessStatus(
                    score,
                    updatedAt + "The planner has enough context to draft a PRD on the next strong turn."
            );
        }

        return new PlannerReadinessStatus(
                score,
                updatedAt + "Continue refining " + joinPlannerTopics(missingTopics) + "."
        );
    }

    private PlannerReadinessStatus draftReadinessStatus(PrdPlanningSession session) {
        String draft = session.latestPrdMarkdown().toLowerCase();
        int score = 20;
        List<String> missingSections = new ArrayList<>();

        score += plannerSectionScore(draft, missingSections, "overview", 10, "## overview", "## introduction");
        score += plannerSectionScore(draft, missingSections, "goals", 10, "## goals");
        score += plannerSectionScore(draft, missingSections, "quality gates", 20, "## quality gates");
        score += plannerSectionScore(draft, missingSections, "user stories", 20, "## user stories", "### us-");
        score += plannerSectionScore(draft, missingSections, "functional requirements", 10, "## functional requirements");
        score += plannerSectionScore(draft, missingSections, "non-goals", 10, "## non-goals", "## non goals", "## scope boundaries");
        score += plannerSectionScore(draft, missingSections, "success metrics", 10, "## success metrics");

        String updatedAt = hasText(session.updatedAt()) ? "Last updated " + session.updatedAt() + ". " : "";
        if (missingSections.isEmpty()) {
            return new PlannerReadinessStatus(
                    score,
                    updatedAt + "Core Ralph-style PRD sections are present. Apply the draft now or keep refining the details."
            );
        }

        return new PlannerReadinessStatus(
                score,
                updatedAt + "Draft still needs " + joinPlannerTopics(missingSections)
                        + ". Quality gates are mandatory before story execution."
        );
    }

    private int plannerSectionScore(String markdown,
                                    List<String> missingSections,
                                    String sectionName,
                                    int points,
                                    String... markers) {
        if (containsAny(markdown, markers)) {
            return points;
        }
        missingSections.add(sectionName);
        return 0;
    }

    private String joinPlannerTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "";
        }

        int maxTopics = Math.min(topics.size(), 3);
        List<String> visibleTopics = topics.subList(0, maxTopics);
        String joinedTopics = String.join(", ", visibleTopics);
        if (topics.size() <= maxTopics) {
            return joinedTopics + ".";
        }
        return joinedTopics + ", and more.";
    }

    private boolean containsAny(String value, String... candidates) {
        if (!hasText(value) || candidates == null || candidates.length == 0) {
            return false;
        }

        String normalizedValue = value.toLowerCase();
        for (String candidate : candidates) {
            if (hasText(candidate) && normalizedValue.contains(candidate.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private VBox buildRunHistoryEntryCard(ActiveProjectService.RunHistoryEntry entry) {
        Label titleLabel = new Label(entry.storyId() + " | " + entry.storyTitle());
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);

        Label summaryLabel = new Label("Run "
                + entry.runId()
                + " | "
                + entry.result()
                + " | "
                + historyTimestampSummary(entry));
        summaryLabel.getStyleClass().add("section-title");
        summaryLabel.setWrapText(true);

        Label detailLabel = new Label(buildRunHistoryEntryDetail(entry));
        detailLabel.getStyleClass().add("muted-text");
        detailLabel.setWrapText(true);

        HBox primaryActions = new HBox(
                8.0,
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.PROMPT),
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.STDOUT),
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.STDERR)
        );
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        HBox secondaryActions = new HBox(
                8.0,
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.STRUCTURED_EVENTS),
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.ASSISTANT_SUMMARY),
                buildRunHistoryArtifactButton(entry, RunHistoryArtifactType.ATTEMPT_SUMMARY)
        );
        secondaryActions.setAlignment(Pos.CENTER_LEFT);

        VBox entryCard = new VBox(8.0, titleLabel, summaryLabel, detailLabel, primaryActions, secondaryActions);
        entryCard.getStyleClass().add("run-history-entry-card");
        return entryCard;
    }

    private Button buildRunHistoryArtifactButton(ActiveProjectService.RunHistoryEntry entry,
                                                 RunHistoryArtifactType artifactType) {
        Button button = new Button(artifactType.buttonLabel());
        button.setId("runHistory"
                + artifactType.idFragment()
                + sanitizeFxId(entry.runId()));
        String artifactPath = historyArtifactPath(entry, artifactType);
        button.setDisable(!hasText(artifactPath));
        button.setOnAction(event -> {
            runHistoryArtifactViewerState = loadRunHistoryArtifact(entry, artifactType, artifactPath);
            renderRunHistoryArtifactViewer();
        });
        return button;
    }

    private RunHistoryArtifactViewerState loadRunHistoryArtifact(ActiveProjectService.RunHistoryEntry entry,
                                                                 RunHistoryArtifactType artifactType,
                                                                 String artifactPath) {
        String summary = entry.storyId()
                + " | "
                + artifactType.viewerLabel()
                + " | "
                + entry.runId();
        if (!hasText(artifactPath)) {
            return new RunHistoryArtifactViewerState(summary, "", MISSING_HISTORY_ARTIFACT_MESSAGE);
        }

        Optional<Path> resolvedArtifactPath = toPath(artifactPath);
        if (resolvedArtifactPath.isEmpty()) {
            return new RunHistoryArtifactViewerState(summary, artifactPath, INVALID_HISTORY_ARTIFACT_MESSAGE);
        }

        Path path = resolvedArtifactPath.get();
        if (!Files.exists(path)) {
            return new RunHistoryArtifactViewerState(summary, path.toString(), MISSING_HISTORY_ARTIFACT_MESSAGE);
        }

        try {
            return new RunHistoryArtifactViewerState(
                    summary,
                    path.toString(),
                    Files.readString(path, StandardCharsets.UTF_8)
            );
        } catch (java.io.IOException exception) {
            return new RunHistoryArtifactViewerState(summary, path.toString(), UNREADABLE_HISTORY_ARTIFACT_MESSAGE);
        }
    }

    private void renderPrdPlannerPreservingWorkspaceScroll(PrdPlanningSession session) {
        if (workspaceScrollPane == null || activeWorkspaceNavButton != prdEditorNavButton) {
            renderPrdPlanner(session);
            return;
        }
        double preservedVvalue = workspaceScrollPane.getVvalue();
        renderPrdPlanner(session);
        restoreWorkspaceScrollValue(workspaceScrollPane, preservedVvalue);
    }

    private void restoreWorkspaceScrollValue(ScrollPane scrollPane, double vvalue) {
        if (scrollPane == null) {
            return;
        }
        double normalizedVvalue = Math.max(0.0, Math.min(vvalue, 1.0));
        Platform.runLater(() -> Platform.runLater(() -> {
            if (scrollPane.getContent() == null) {
                return;
            }
            scrollPane.setVvalue(normalizedVvalue);
        }));
    }

    private String historyArtifactPath(ActiveProjectService.RunHistoryEntry entry, RunHistoryArtifactType artifactType) {
        return switch (artifactType) {
            case PROMPT -> entry.artifactPaths().promptPath();
            case STDOUT -> entry.artifactPaths().stdoutPath();
            case STDERR -> entry.artifactPaths().stderrPath();
            case STRUCTURED_EVENTS -> entry.artifactPaths().structuredEventsPath();
            case ASSISTANT_SUMMARY -> entry.artifactPaths().assistantSummaryPath();
            case ATTEMPT_SUMMARY -> entry.artifactPaths().summaryPath();
        };
    }

    private String historyTimestampSummary(ActiveProjectService.RunHistoryEntry entry) {
        List<String> timestamps = new ArrayList<>();
        if (hasText(entry.queuedAt())) {
            timestamps.add("Queued " + entry.queuedAt());
        }
        if (hasText(entry.startedAt())) {
            timestamps.add("Started " + entry.startedAt());
        }
        if (hasText(entry.endedAt())) {
            timestamps.add("Ended " + entry.endedAt());
        }
        return timestamps.isEmpty()
                ? "No timestamps recorded"
                : String.join(" | ", timestamps);
    }

    private String buildRunHistoryEntryDetail(ActiveProjectService.RunHistoryEntry entry) {
        List<String> lines = new ArrayList<>();
        lines.add("Preset "
                + entry.presetName()
                + (hasText(entry.presetVersion()) ? " " + entry.presetVersion() : ""));
        lines.add(hasText(entry.branchName())
                ? "Branch "
                + entry.branchName()
                + (hasText(entry.branchAction()) ? " (" + entry.branchAction() + ")" : "")
                : "Branch unavailable");
        lines.add(hasText(entry.commitHash())
                ? "Commit "
                + entry.commitHash()
                + (hasText(entry.commitMessage()) ? " | " + entry.commitMessage() : "")
                : "Commit not created");
        if (hasText(entry.detail())) {
            lines.add(entry.detail());
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String sanitizeFxId(String value) {
        if (!hasText(value)) {
            return "Unknown";
        }
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    private void renderRunOutputView() {
        updateRunOutputViewAvailability(currentRunOutputProvider());
        if (activeProjectService.activeProject().isEmpty()) {
            runOutputSummaryLabel.setText(NO_ACTIVE_RUN_OUTPUT_SUMMARY);
            runOutputDetailLabel.setText(NO_ACTIVE_RUN_OUTPUT_DETAIL);
            runOutputTextArea.setVisible(true);
            runOutputStructuredScrollPane.setVisible(false);
            runOutputTextArea.setText(NO_ACTIVE_RUN_OUTPUT_DETAIL);
            runOutputStructuredContainer.getChildren().clear();
            structuredRunOutputCommandViewStates.clear();
            structuredRunOutputPinnedToBottom = true;
            liveRawOutputTextAreaInitialized = false;
            return;
        }

        runOutputSummaryLabel.setText(runOutputPresentationState.summary());
        runOutputDetailLabel.setText(runOutputPresentationState.detail());
        RunOutputView selectedView = selectedRunOutputView();
        boolean structuredViewSelected = selectedView == RunOutputView.STRUCTURED_OUTPUT;
        runOutputTextArea.setVisible(!structuredViewSelected);
        runOutputStructuredScrollPane.setVisible(structuredViewSelected);
        if (structuredViewSelected) {
            captureRenderedStructuredRunOutputCommandViewStates();
            ScrollPositionState scrollPositionState = captureScrollPosition(runOutputStructuredScrollPane);
            boolean stickToBottomNow = scrollPositionState == null || scrollPositionState.stickToBottom();
            boolean keepPinnedToBottom = runOutputPresentationState.active()
                    && (stickToBottomNow || structuredRunOutputPinnedToBottom);
            runOutputStructuredContainer.getChildren().setAll(buildStructuredRunOutputNodes(runOutputPresentationState));
            liveRawOutputTextAreaInitialized = false;
            if (keepPinnedToBottom) {
                structuredRunOutputPinnedToBottom = true;
                scrollStructuredRunOutputToBottom();
                return;
            }
            structuredRunOutputPinnedToBottom = false;
            restoreScrollPosition(runOutputStructuredScrollPane, scrollPositionState, runOutputPresentationState.active());
            return;
        }

        runOutputTextArea.setText(selectedView == RunOutputView.ASSISTANT_SUMMARY
                ? assistantSummaryBody(runOutputPresentationState)
                : rawOutputBody(runOutputPresentationState));
        liveRawOutputTextAreaInitialized = selectedView == RunOutputView.RAW_OUTPUT
                && runOutputPresentationState.active()
                && hasText(runOutputPresentationState.rawOutput());
        if (selectedView == RunOutputView.RAW_OUTPUT && hasText(runOutputTextArea.getText())) {
            scrollRunOutputTextAreaToBottom();
        }
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
                providerForRun(runMetadataRecord),
                summary,
                detail,
                readAssistantSummary(runMetadataRecord),
                readCombinedRawOutput(runMetadataRecord)
        );
    }

    private ExecutionAgentProvider providerForRun(LocalMetadataStorage.RunMetadataRecord runMetadataRecord) {
        String normalizedCommand = String.join(" ", runMetadataRecord.command()).toLowerCase();
        return normalizedCommand.contains("copilot")
                ? ExecutionAgentProvider.GITHUB_COPILOT
                : ExecutionAgentProvider.CODEX;
    }

    private String buildRunOutputDetail(String runId, String profileType, String startedAt, String endedAt) {
        List<String> details = new ArrayList<>();
        if (hasText(runId)) {
            details.add("Run " + runId);
        }
        if (hasText(profileType)) {
            details.add(ExecutionProfile.storedProfileTypeLabel(profileType));
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
            return formatRunOutputJsonBlocks(state.rawOutput());
        }
        return state.active() ? PENDING_RAW_OUTPUT_MESSAGE : MISSING_RAW_OUTPUT_MESSAGE;
    }

    private String formatRunOutputJsonBlocks(String output) {
        if (!hasText(output)) {
            return "";
        }

        List<String> formattedLines = new ArrayList<>();
        for (String line : normalizeEditorLineEndings(output).split("\n", -1)) {
            if (line.isEmpty()) {
                formattedLines.add("");
                continue;
            }
            JsonNode jsonNode = parseJsonLine(line);
            formattedLines.add(jsonNode == null ? line : jsonNode.toPrettyString());
        }
        return String.join(System.lineSeparator(), formattedLines);
    }

    private List<Node> buildStructuredRunOutputNodes(RunOutputPresentationState state) {
        List<StructuredRunOutputEntry> entries = buildStructuredRunOutputEntries(state);
        pruneStructuredRunOutputCommandViewStates(entries);

        List<Node> cards = new ArrayList<>(entries.size());
        for (StructuredRunOutputEntry entry : entries) {
            cards.add(buildStructuredRunOutputCard(entry));
        }
        return cards;
    }

    private List<StructuredRunOutputEntry> buildStructuredRunOutputEntries(RunOutputPresentationState state) {
        if (!state.available()) {
            return List.of(new StructuredRunOutputEntry(
                    "run-output-unavailable",
                    "Run Output",
                    "Unavailable",
                    NO_PERSISTED_RUN_OUTPUT_DETAIL,
                    "",
                    "run-output-event-neutral",
                    false
            ));
        }
        if (!hasText(state.rawOutput())) {
            return List.of(new StructuredRunOutputEntry(
                    "run-output-empty",
                    "Run Output",
                    state.active() ? "Waiting for output" : "No output captured",
                    state.active() ? PENDING_RAW_OUTPUT_MESSAGE : MISSING_RAW_OUTPUT_MESSAGE,
                    "",
                    "run-output-event-neutral",
                    false
            ));
        }

        List<StructuredRunOutputEntry> entries = new ArrayList<>();
        Map<String, Integer> entryIndexes = new LinkedHashMap<>();
        StringBuilder plainTextBuffer = new StringBuilder();
        boolean stderrSection = false;
        int syntheticEntryIndex = 0;
        for (String line : normalizeEditorLineEndings(state.rawOutput()).split("\n", -1)) {
            if (STDERR_SECTION_HEADER.equals(line.trim())) {
                syntheticEntryIndex = flushStructuredPlainText(entries, plainTextBuffer, stderrSection, syntheticEntryIndex);
                stderrSection = true;
                continue;
            }

            JsonNode eventNode = !stderrSection ? parseJsonLine(line) : null;
            if (eventNode != null) {
                syntheticEntryIndex = flushStructuredPlainText(entries, plainTextBuffer, false, syntheticEntryIndex);
                upsertStructuredRunOutputEntry(
                        entries,
                        entryIndexes,
                        buildStructuredRunOutputEventEntry(eventNode, syntheticEntryIndex)
                );
                syntheticEntryIndex++;
                continue;
            }

            appendStructuredPlainTextLine(plainTextBuffer, line);
        }
        flushStructuredPlainText(entries, plainTextBuffer, stderrSection, syntheticEntryIndex);

        if (entries.isEmpty()) {
            entries.add(new StructuredRunOutputEntry(
                    "run-output-plain-text",
                    "Run Output",
                    "Plain text output",
                    formatRunOutputJsonBlocks(state.rawOutput()),
                    "",
                    "run-output-event-neutral",
                    false
            ));
        }
        return entries;
    }

    private void appendStructuredPlainTextLine(StringBuilder buffer, String line) {
        if (buffer.length() > 0) {
            buffer.append(System.lineSeparator());
        }
        buffer.append(line);
    }

    private int flushStructuredPlainText(List<StructuredRunOutputEntry> entries,
                                         StringBuilder buffer,
                                         boolean stderrSection,
                                         int syntheticEntryIndex) {
        if (!hasText(buffer.toString())) {
            buffer.setLength(0);
            return syntheticEntryIndex;
        }

        entries.add(new StructuredRunOutputEntry(
                (stderrSection ? "stderr-" : "output-") + syntheticEntryIndex,
                stderrSection ? "stderr" : "output",
                stderrSection ? "Standard error" : "Plain text output",
                buffer.toString(),
                "",
                stderrSection ? "run-output-event-failure" : "run-output-event-neutral",
                false
        ));
        buffer.setLength(0);
        return syntheticEntryIndex + 1;
    }

    private void upsertStructuredRunOutputEntry(List<StructuredRunOutputEntry> entries,
                                                Map<String, Integer> entryIndexes,
                                                StructuredRunOutputEntry entry) {
        Integer existingEntryIndex = entryIndexes.get(entry.key());
        if (existingEntryIndex == null) {
            entryIndexes.put(entry.key(), entries.size());
            entries.add(entry);
            return;
        }
        entries.set(existingEntryIndex, entry);
    }

    private StructuredRunOutputEntry buildStructuredRunOutputEventEntry(JsonNode eventNode, int syntheticEntryIndex) {
        String eventType = textValue(eventNode, "type");
        JsonNode itemNode = eventNode.path("item");
        String itemType = textValue(itemNode, "type");
        String itemStatus = firstNonBlank(textValue(itemNode, "status"), eventTypeStatus(eventType));
        String accentStyleClass = eventAccentStyleClass(eventType, itemType, itemStatus);
        String badge = hasText(itemType) ? itemType.replace('_', ' ') : eventType;
        String title = structuredEventTitle(eventType, itemNode);
        String detail = structuredEventDetail(eventType, eventNode, itemNode, itemStatus);
        String body = structuredEventBody(eventType, eventNode, itemNode);
        return new StructuredRunOutputEntry(
                structuredEventKey(eventType, itemNode, syntheticEntryIndex),
                badge,
                title,
                body,
                detail,
                accentStyleClass,
                "command_execution".equals(itemType)
        );
    }

    private String structuredEventKey(String eventType, JsonNode itemNode, int syntheticEntryIndex) {
        if (("item.started".equals(eventType) || "item.completed".equals(eventType) || "item.updated".equals(eventType))
                && hasText(textValue(itemNode, "id"))) {
            return "item-" + textValue(itemNode, "id");
        }
        return "event-" + syntheticEntryIndex;
    }

    private VBox buildStructuredRunOutputCard(StructuredRunOutputEntry entry) {
        String accentStyleClass = entry.accentStyleClass();
        String badgeText = hasText(entry.badge()) ? entry.badge() : "event";
        String titleText = hasText(entry.title()) ? entry.title() : "Run Output Event";
        boolean duplicateHeaderText = badgeText.equalsIgnoreCase(titleText);

        Label badgeLabel = new Label(badgeText);
        badgeLabel.getStyleClass().addAll("run-output-event-badge", accentStyleClass);

        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("run-output-event-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        if (entry.commandOutput()) {
            titleLabel.getStyleClass().add("run-output-event-command-title");
        }

        Node headerRow;
        if (duplicateHeaderText) {
            headerRow = badgeLabel;
        } else {
            headerRow = entry.commandOutput()
                    ? new VBox(2.0, badgeLabel, titleLabel)
                    : new HBox(8.0, badgeLabel, titleLabel);
        }
        if (headerRow instanceof HBox hbox) {
            hbox.setAlignment(Pos.CENTER_LEFT);
        } else if (headerRow instanceof VBox vbox) {
            vbox.setAlignment(Pos.CENTER_LEFT);
        } else if (headerRow instanceof Label label) {
            label.setMaxWidth(Double.MAX_VALUE);
        }

        VBox card = new VBox(6.0, headerRow);
        card.setId("structuredRunOutputEntry" + sanitizeFxId(entry.key()));
        card.getStyleClass().addAll("run-output-event-card", accentStyleClass);

        if (hasText(entry.detail())) {
            Label detailLabel = new Label(entry.detail());
            detailLabel.getStyleClass().add("run-output-event-meta");
            detailLabel.setWrapText(true);
            card.getChildren().add(detailLabel);
        }
        if (entry.commandOutput()) {
            card.getChildren().add(buildStructuredCommandOutputBox(entry));
        } else if (hasText(entry.body())) {
            Label bodyLabel = new Label(entry.body());
            bodyLabel.getStyleClass().add("run-output-event-body");
            bodyLabel.setWrapText(true);
            card.getChildren().add(bodyLabel);
        }
        return card;
    }

    private VBox buildStructuredCommandOutputBox(StructuredRunOutputEntry entry) {
        StructuredCommandOutputViewState viewState = structuredRunOutputCommandViewStates
                .getOrDefault(entry.key(), StructuredCommandOutputViewState.collapsed());
        CommandOutputDisplay commandOutputDisplay = commandOutputDisplay(entry.body(), viewState.expanded());

        VBox container = new VBox(6.0);
        if (commandOutputDisplay.truncated()) {
            Button toggleButton = new Button(viewState.expanded() ? "Collapse" : "Expand");
            toggleButton.setId("structuredRunOutputToggle" + sanitizeFxId(entry.key()));
            toggleButton.getStyleClass().add("run-output-event-toggle");
            toggleButton.setOnAction(event -> {
                StructuredCommandOutputViewState currentState = structuredRunOutputCommandViewStates
                        .getOrDefault(entry.key(), StructuredCommandOutputViewState.collapsed());
                structuredRunOutputCommandViewStates.put(entry.key(), currentState.withExpanded(!currentState.expanded()));
                renderRunOutputView();
            });
            container.getChildren().add(toggleButton);
        }

        Label outputLabel = new Label(commandOutputDisplay.visibleText());
        outputLabel.setWrapText(false);
        outputLabel.getStyleClass().add("run-output-event-output-content");

        ScrollPane outputScrollPane = new ScrollPane(outputLabel);
        outputScrollPane.setId("structuredRunOutputBody" + sanitizeFxId(entry.key()));
        outputScrollPane.setUserData(entry.key());
        outputScrollPane.setFitToWidth(false);
        outputScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outputScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outputScrollPane.getStyleClass().add("run-output-event-output-scroll");
        applyCommandOutputViewportHeight(outputScrollPane, commandOutputDisplay.visibleLineCount(), viewState.expanded());
        outputScrollPane.vvalueProperty().addListener((observable, oldValue, newValue) ->
                updateStructuredRunOutputCommandScrollState(entry.key(), outputScrollPane)
        );
        container.getChildren().add(outputScrollPane);

        restoreStructuredRunOutputCommandScrollState(outputScrollPane, viewState);
        return container;
    }

    private CommandOutputDisplay commandOutputDisplay(String fullOutput, boolean expanded) {
        String formattedOutput = hasText(fullOutput) ? formatRunOutputJsonBlocks(fullOutput) : "No command output captured yet.";
        List<String> lines = normalizedDisplayLines(formattedOutput);
        if (expanded || lines.size() <= COMMAND_OUTPUT_PREVIEW_LINE_COUNT) {
            return new CommandOutputDisplay(formattedOutput, lines.size(), lines.size() > COMMAND_OUTPUT_PREVIEW_LINE_COUNT);
        }

        List<String> previewLines = new ArrayList<>();
        previewLines.add("...");
        previewLines.addAll(lines.subList(Math.max(lines.size() - COMMAND_OUTPUT_PREVIEW_LINE_COUNT, 0), lines.size()));
        return new CommandOutputDisplay(String.join(System.lineSeparator(), previewLines), previewLines.size(), true);
    }

    private List<String> normalizedDisplayLines(String text) {
        if (!hasText(text)) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>(List.of(normalizeEditorLineEndings(text).split("\n", -1)));
        while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void applyCommandOutputViewportHeight(ScrollPane outputScrollPane, int visibleLineCount, boolean expanded) {
        double lineHeight = 18.0;
        double viewportHeight = expanded
                ? 160.0
                : Math.min(132.0, Math.max(72.0, 24.0 + (visibleLineCount * lineHeight)));
        outputScrollPane.setMinHeight(viewportHeight);
        outputScrollPane.setPrefHeight(viewportHeight);
        outputScrollPane.setMaxHeight(viewportHeight);
        outputScrollPane.setPrefViewportHeight(viewportHeight);
    }

    private void updateStructuredRunOutputCommandScrollState(String entryKey, ScrollPane outputScrollPane) {
        StructuredCommandOutputViewState currentState = structuredRunOutputCommandViewStates
                .getOrDefault(entryKey, StructuredCommandOutputViewState.collapsed());
        structuredRunOutputCommandViewStates.put(
                entryKey,
                currentState.withScrollPosition(captureScrollPosition(outputScrollPane))
        );
    }

    private void restoreStructuredRunOutputCommandScrollState(ScrollPane outputScrollPane,
                                                              StructuredCommandOutputViewState viewState) {
        if (viewState == null || !viewState.expanded()) {
            return;
        }
        restoreScrollPosition(outputScrollPane, viewState.scrollPosition(), true);
    }

    private void pruneStructuredRunOutputCommandViewStates(List<StructuredRunOutputEntry> entries) {
        Set<String> activeCommandEntryKeys = new HashSet<>();
        for (StructuredRunOutputEntry entry : entries) {
            if (entry.commandOutput()) {
                activeCommandEntryKeys.add(entry.key());
            }
        }
        structuredRunOutputCommandViewStates.keySet().retainAll(activeCommandEntryKeys);
    }

    private void captureRenderedStructuredRunOutputCommandViewStates() {
        if (runOutputStructuredContainer == null) {
            return;
        }
        captureRenderedStructuredRunOutputCommandViewStates(runOutputStructuredContainer);
    }

    private void captureRenderedStructuredRunOutputCommandViewStates(Node node) {
        if (node instanceof ScrollPane scrollPane && scrollPane.getUserData() instanceof String entryKey) {
            StructuredCommandOutputViewState currentState = structuredRunOutputCommandViewStates
                    .getOrDefault(entryKey, StructuredCommandOutputViewState.collapsed());
            if (currentState.expanded()) {
                structuredRunOutputCommandViewStates.put(
                        entryKey,
                        currentState.withScrollPosition(captureScrollPosition(scrollPane))
                );
            }
        }
        if (node instanceof Parent parent) {
            for (Node childNode : parent.getChildrenUnmodifiable()) {
                captureRenderedStructuredRunOutputCommandViewStates(childNode);
            }
        }
    }

    private String structuredEventTitle(String eventType, JsonNode itemNode) {
        if ("item.started".equals(eventType) || "item.completed".equals(eventType) || "item.updated".equals(eventType)) {
            String itemType = textValue(itemNode, "type");
            if ("reasoning".equals(itemType)) {
                return "Reasoning";
            }
            if ("command_execution".equals(itemType)) {
                return firstNonBlank(textValue(itemNode, "command"), "Command execution");
            }
            if ("agent_message".equals(itemType)) {
                return "Agent message";
            }
            if ("todo_list".equals(itemType)) {
                return "Todo list updated";
            }
            if ("web_search".equals(itemType)) {
                return firstNonBlank(textValue(itemNode, "query"), "Web search");
            }
            if ("file_change".equals(itemType)) {
                return structuredFileChangeTitle(itemNode);
            }
            return firstNonBlank(itemType.replace('_', ' '), eventType);
        }
        return switch (eventType) {
            case "thread.started" -> "Thread started";
            case "turn.started" -> "Turn started";
            case "turn.completed" -> "Turn completed";
            default -> eventType;
        };
    }

    private String structuredEventDetail(String eventType,
                                         JsonNode eventNode,
                                         JsonNode itemNode,
                                         String itemStatus) {
        if ("item.started".equals(eventType) || "item.completed".equals(eventType) || "item.updated".equals(eventType)) {
            List<String> details = new ArrayList<>();
            if (hasText(textValue(itemNode, "id"))) {
                details.add(textValue(itemNode, "id"));
            }
            if (hasText(itemStatus)) {
                details.add(itemStatus.replace('_', ' '));
            }
            if (!itemNode.path("exit_code").isMissingNode() && !itemNode.path("exit_code").isNull()) {
                details.add("Exit " + itemNode.path("exit_code").asInt());
            }
            if ("web_search".equals(textValue(itemNode, "type"))) {
                String actionType = textValue(itemNode.path("action"), "type");
                if (hasText(actionType)) {
                    details.add("Action " + actionType.replace('_', ' '));
                }
            }
            return String.join(" | ", details);
        }
        if ("thread.started".equals(eventType) && hasText(textValue(eventNode, "thread_id"))) {
            return textValue(eventNode, "thread_id");
        }
        if ("thread.started".equals(eventType) && hasText(textValue(eventNode, "threadId"))) {
            return textValue(eventNode, "threadId");
        }
        if ("turn.completed".equals(eventType)) {
            JsonNode usageNode = eventNode.path("usage");
            if (!usageNode.isMissingNode() && !usageNode.isNull()) {
                return usageNode.toPrettyString();
            }
        }
        return "";
    }

    private String structuredEventBody(String eventType, JsonNode eventNode, JsonNode itemNode) {
        if ("item.started".equals(eventType) || "item.completed".equals(eventType) || "item.updated".equals(eventType)) {
            String itemType = textValue(itemNode, "type");
            if ("reasoning".equals(itemType)) {
                return textValue(itemNode, "text");
            }
            if ("agent_message".equals(itemType)) {
                return textValue(itemNode, "text");
            }
            if ("command_execution".equals(itemType)) {
                String aggregatedOutput = textValue(itemNode, "aggregated_output");
                if (hasText(aggregatedOutput)) {
                    return formatRunOutputJsonBlocks(aggregatedOutput);
                }
                return "No command output captured yet.";
            }
            if ("todo_list".equals(itemType) && itemNode.path("items").isArray()) {
                List<String> items = new ArrayList<>();
                for (JsonNode todoItem : itemNode.path("items")) {
                    items.add((todoItem.path("completed").asBoolean() ? "[x] " : "[ ] ")
                            + textValue(todoItem, "text"));
                }
                return String.join(System.lineSeparator(), items);
            }
            if ("web_search".equals(itemType)) {
                return structuredWebSearchBody(itemNode);
            }
            if ("file_change".equals(itemType)) {
                return structuredFileChangeBody(itemNode);
            }
        }
        if ("thread.started".equals(eventType) && hasText(textValue(eventNode, "thread_id"))) {
            return textValue(eventNode, "thread_id");
        }
        if ("turn.completed".equals(eventType) && eventNode.path("usage").isObject()) {
            return eventNode.path("usage").toPrettyString();
        }
        return eventNode.toPrettyString();
    }

    private String structuredWebSearchBody(JsonNode itemNode) {
        List<String> lines = new ArrayList<>();
        String query = textValue(itemNode, "query");
        if (hasText(query)) {
            lines.add("Query: " + query);
        }

        JsonNode actionNode = itemNode.path("action");
        String actionType = textValue(actionNode, "type");
        if (hasText(actionType)) {
            lines.add("Action: " + actionType.replace('_', ' '));
        }

        JsonNode queriesNode = actionNode.path("queries");
        if (queriesNode.isArray()) {
            for (JsonNode queryNode : queriesNode) {
                String relatedQuery = queryNode.asText("");
                if (hasText(relatedQuery) && !relatedQuery.equals(query)) {
                    lines.add("Related query: " + relatedQuery);
                }
            }
        }

        if (lines.isEmpty()) {
            return "No search query captured yet.";
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String structuredFileChangeTitle(JsonNode itemNode) {
        JsonNode changesNode = itemNode.path("changes");
        if (changesNode.isArray() && changesNode.size() == 1) {
            String fileName = fileNameFromPath(textValue(changesNode.get(0), "path"));
            if (hasText(fileName)) {
                return fileName;
            }
        }
        return changesNode.isArray() && changesNode.size() > 1 ? "File changes" : "File change";
    }

    private String structuredFileChangeBody(JsonNode itemNode) {
        JsonNode changesNode = itemNode.path("changes");
        if (!changesNode.isArray() || changesNode.isEmpty()) {
            return "No file changes captured yet.";
        }

        List<String> lines = new ArrayList<>();
        for (JsonNode changeNode : changesNode) {
            String path = textValue(changeNode, "path");
            String kind = textValue(changeNode, "kind");
            String line = hasText(kind) ? kind.replace('_', ' ') + " " : "";
            line += hasText(path) ? path : changeNode.toPrettyString();
            lines.add(line.strip());
        }
        return String.join(System.lineSeparator(), lines);
    }

    private String fileNameFromPath(String path) {
        if (!hasText(path)) {
            return "";
        }
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash < 0 || lastSlash >= path.length() - 1) {
            return path;
        }
        return path.substring(lastSlash + 1);
    }

    private String eventTypeStatus(String eventType) {
        if (!hasText(eventType)) {
            return "";
        }
        if (eventType.endsWith(".started")) {
            return "in_progress";
        }
        if (eventType.endsWith(".completed")) {
            return "completed";
        }
        return "";
    }

    private String eventAccentStyleClass(String eventType, String itemType, String itemStatus) {
        String normalizedStatus = valueOrEmpty(itemStatus).toLowerCase();
        if ("command_execution".equals(itemType) && "failed".equals(normalizedStatus)) {
            return "run-output-event-failure";
        }
        if ("command_execution".equals(itemType) && "completed".equals(normalizedStatus)) {
            return "run-output-event-success";
        }
        if ("command_execution".equals(itemType) && "in_progress".equals(normalizedStatus)) {
            return "run-output-event-running";
        }
        if ("agent_message".equals(itemType) || "todo_list".equals(itemType)) {
            return "run-output-event-neutral";
        }
        if ("turn.completed".equals(eventType) || "item.completed".equals(eventType)) {
            return "run-output-event-success";
        }
        if (STDERR_SECTION_HEADER.equals(eventType)) {
            return "run-output-event-failure";
        }
        if ("item.started".equals(eventType) || "turn.started".equals(eventType) || "thread.started".equals(eventType)) {
            return "run-output-event-running";
        }
        return "run-output-event-neutral";
    }

    private JsonNode parseJsonLine(String value) {
        if (!hasText(value)) {
            return null;
        }

        String trimmedValue = value.trim();
        if ((!trimmedValue.startsWith("{") || !trimmedValue.endsWith("}"))
                && (!trimmedValue.startsWith("[") || !trimmedValue.endsWith("]"))) {
            return null;
        }
        try {
            return RUN_OUTPUT_OBJECT_MAPPER.readTree(trimmedValue);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        if (valueNode.isValueNode()) {
            return valueNode.asText("");
        }
        return valueNode.toPrettyString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private RunOutputView selectedRunOutputView() {
        if (currentRunOutputProvider() == ExecutionAgentProvider.GITHUB_COPILOT) {
            return RunOutputView.RAW_OUTPUT;
        }
        if (structuredRunOutputViewRadioButton.isSelected()) {
            return RunOutputView.STRUCTURED_OUTPUT;
        }
        if (rawOutputViewRadioButton.isSelected()) {
            return RunOutputView.RAW_OUTPUT;
        }
        if (assistantSummaryViewRadioButton.isSelected()) {
            return RunOutputView.ASSISTANT_SUMMARY;
        }
        return RunOutputView.STRUCTURED_OUTPUT;
    }

    private ExecutionAgentProvider currentRunOutputProvider() {
        return runOutputPresentationState.available()
                ? runOutputPresentationState.provider()
                : selectedExecutionAgentSelection().provider();
    }

    private void updateRunOutputViewAvailability(ExecutionAgentProvider provider) {
        boolean structuredAvailable = provider != ExecutionAgentProvider.GITHUB_COPILOT;
        assistantSummaryViewRadioButton.setVisible(structuredAvailable);
        assistantSummaryViewRadioButton.setManaged(structuredAvailable);
        structuredRunOutputViewRadioButton.setVisible(structuredAvailable);
        structuredRunOutputViewRadioButton.setManaged(structuredAvailable);
        if (!structuredAvailable) {
            rawOutputViewRadioButton.setSelected(true);
        }
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
                + ". " + hostOperatingSystem.nativeProfileLabel() + " runs stay blocked until every check passes.");
        nativePreflightChecksLabel.setText(formatNativeWindowsPreflightChecks(report));
        renderNativePreflightRemediation(report);
    }

    private void renderWslPreflight() {
        if (!hostOperatingSystem.supportsWslProfiles()) {
            clearWslPreflightRemediation();
            return;
        }
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
                .orElse(ExecutionProfile.nativeHost());
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
        ExecutionAgentSelection executionAgentSelection = selectedExecutionAgentSelection();
        if (!executionAgentSelection.provider().executionSupported()) {
            setSingleStorySessionMessage(executionAgentSelection.provider().displayName()
                    + " execution is not implemented yet. Switch provider to Codex.");
            renderSingleStorySession();
            return;
        }

        PresetUseCase presetUseCase = sessionPresetUseCase();
        ActiveProjectService.SingleStorySessionAvailability availability =
                activeProjectService.singleStorySessionAvailability(presetUseCase, executionAgentSelection);
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
        launchStorySessionStep(presetUseCase, availability, executionAgentSelection);
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
                                        ActiveProjectService.SingleStorySessionAvailability availability,
                                        ExecutionAgentSelection executionAgentSelection) {
        singleStorySessionInProgress = true;
        singleStorySessionTaskId = availability.story().taskId();
        activeSessionPresetUseCase = presetUseCase;
        resetLiveRunOutputBuffer();
        setSingleStorySessionMessage(startSessionMessage(availability));
        selectDefaultRunOutputViewForProvider(executionAgentSelection.provider());
        runOutputPresentationState = RunOutputPresentationState.live(
                executionAgentSelection.provider(),
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
                        () -> activeProjectService.startEligibleSingleStory(
                                presetUseCase,
                                executionAgentSelection,
                                runOutputListener
                        ),
                        backgroundExecutor
                )
                .whenComplete((result, throwable) -> Platform.runLater(() ->
                        handleStorySessionStepCompletion(presetUseCase, executionAgentSelection, result, throwable)
                ));
    }

    private void handleStorySessionStepCompletion(PresetUseCase presetUseCase,
                                                  ExecutionAgentSelection executionAgentSelection,
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
                activeProjectService.singleStorySessionAvailability(presetUseCase, executionAgentSelection);
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
            launchStorySessionStep(presetUseCase, nextAvailability, executionAgentSelection);
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
                            launchPlan.agentSelection().provider(),
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
                enqueueLiveStdout(text);
            }

            @Override
            public void onStderr(String text) {
                enqueueLiveStderr(text);
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

    private void resetLiveRunOutputBuffer() {
        synchronized (liveRunOutputLock) {
            liveRunOutputBuffer.setLength(0);
            pendingLiveRunOutputDelta.setLength(0);
            liveRunOutputHasStderrSection = false;
            liveRunOutputFlushScheduled = false;
        }
        liveRawOutputTextAreaInitialized = false;
    }

    private void enqueueLiveStdout(String text) {
        enqueueLiveRunOutput(text, false);
    }

    private void enqueueLiveStderr(String text) {
        enqueueLiveRunOutput(text, true);
    }

    private void enqueueLiveRunOutput(String text, boolean stderr) {
        if (!hasText(text)) {
            return;
        }

        boolean scheduleFlush = false;
        synchronized (liveRunOutputLock) {
            if (stderr) {
                appendLiveStderr(text);
            } else {
                liveRunOutputBuffer.append(text);
                pendingLiveRunOutputDelta.append(text);
            }
            if (!liveRunOutputFlushScheduled) {
                liveRunOutputFlushScheduled = true;
                scheduleFlush = true;
            }
        }

        if (scheduleFlush) {
            Platform.runLater(this::flushLiveRunOutput);
        }
    }

    private void appendLiveStderr(String text) {
        if (!liveRunOutputHasStderrSection) {
            if (liveRunOutputBuffer.length() > 0 && !endsWithLineBreak(liveRunOutputBuffer)) {
                liveRunOutputBuffer.append(System.lineSeparator());
                pendingLiveRunOutputDelta.append(System.lineSeparator());
            }
            if (liveRunOutputBuffer.length() > 0) {
                liveRunOutputBuffer.append(System.lineSeparator());
                pendingLiveRunOutputDelta.append(System.lineSeparator());
            }
            liveRunOutputBuffer.append(STDERR_SECTION_HEADER).append(System.lineSeparator());
            pendingLiveRunOutputDelta.append(STDERR_SECTION_HEADER).append(System.lineSeparator());
            liveRunOutputHasStderrSection = true;
        }
        liveRunOutputBuffer.append(text);
        pendingLiveRunOutputDelta.append(text);
    }

    private void flushLiveRunOutput() {
        String outputSnapshot;
        synchronized (liveRunOutputLock) {
            outputSnapshot = liveRunOutputBuffer.toString();
            pendingLiveRunOutputDelta.setLength(0);
            liveRunOutputFlushScheduled = false;
        }

        if (!runOutputPresentationState.active()) {
            liveRawOutputTextAreaInitialized = false;
            return;
        }

        runOutputPresentationState = runOutputPresentationState.withRawOutput(outputSnapshot);
        RunOutputView selectedView = selectedRunOutputView();
        if (selectedView == RunOutputView.ASSISTANT_SUMMARY) {
            liveRawOutputTextAreaInitialized = false;
            return;
        }
        renderRunOutputView();
        if (selectedView == RunOutputView.STRUCTURED_OUTPUT) {
            return;
        }
        liveRawOutputTextAreaInitialized = hasText(runOutputPresentationState.rawOutput());
        scrollRunOutputTextAreaToBottom();
    }

    private void scrollRunOutputTextAreaToBottom() {
        runOutputTextArea.positionCaret(runOutputTextArea.getLength());
        runOutputTextArea.setScrollTop(Double.MAX_VALUE);
        runOutputTextArea.setScrollLeft(0);
    }

    private void scrollStructuredRunOutputToBottom() {
        if (runOutputStructuredScrollPane == null) {
            return;
        }
        runOutputStructuredScrollPane.setVvalue(1.0);
        Platform.runLater(() -> {
            runOutputStructuredScrollPane.setVvalue(1.0);
            Platform.runLater(() -> runOutputStructuredScrollPane.setVvalue(1.0));
        });
    }

    private void updateStructuredRunOutputPinnedToBottomState() {
        ScrollPositionState scrollPositionState = captureScrollPosition(runOutputStructuredScrollPane);
        structuredRunOutputPinnedToBottom = scrollPositionState == null || scrollPositionState.stickToBottom();
    }

    private ScrollPositionState captureScrollPosition(ScrollPane scrollPane) {
        if (scrollPane == null || scrollPane.getContent() == null) {
            return null;
        }

        double scrollableHeight = Math.max(
                scrollPane.getContent().getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight(),
                0.0
        );
        double offsetFromTop = scrollableHeight <= 0.0 ? 0.0 : scrollPane.getVvalue() * scrollableHeight;
        double offsetFromBottom = Math.max(scrollableHeight - offsetFromTop, 0.0);
        boolean stickToBottom = scrollableHeight <= 0.0 || offsetFromBottom <= SCROLL_BOTTOM_PIN_THRESHOLD_PX;
        return new ScrollPositionState(stickToBottom, offsetFromTop, offsetFromBottom);
    }

    private void restoreScrollPosition(ScrollPane scrollPane,
                                       ScrollPositionState scrollPositionState,
                                       boolean defaultToBottom) {
        if (scrollPane == null) {
            return;
        }
        Platform.runLater(() -> Platform.runLater(() -> {
            if (scrollPane.getContent() == null) {
                return;
            }
            double scrollableHeight = Math.max(
                    scrollPane.getContent().getLayoutBounds().getHeight() - scrollPane.getViewportBounds().getHeight(),
                    0.0
            );
            if (scrollableHeight <= 0.0) {
                scrollPane.setVvalue(0.0);
                return;
            }
            if (scrollPositionState == null) {
                scrollPane.setVvalue(defaultToBottom ? 1.0 : 0.0);
                return;
            }
            if (scrollPositionState.stickToBottom()) {
                scrollPane.setVvalue(1.0);
                return;
            }
            double targetOffsetFromTop = Math.max(scrollableHeight - scrollPositionState.offsetFromBottom(), 0.0);
            scrollPane.setVvalue(Math.min(targetOffsetFromTop / scrollableHeight, 1.0));
        }));
    }

    private void configureRunOutputViewportSizing() {
        if (runOutputTextArea == null || runOutputStructuredScrollPane == null) {
            return;
        }
        applyRunOutputViewportHeight(runOutputTextArea.prefHeight(-1));
        runOutputTextArea.heightProperty().addListener((observable, oldValue, newValue) ->
                applyRunOutputViewportHeight(newValue.doubleValue())
        );
        Platform.runLater(() -> applyRunOutputViewportHeight(runOutputTextArea.getHeight()));
    }

    private void applyRunOutputViewportHeight(double height) {
        double baseHeight = height > 0.0 ? height : DEFAULT_RUN_OUTPUT_VIEWPORT_HEIGHT - STRUCTURED_RUN_OUTPUT_VIEWPORT_EXTRA_HEIGHT;
        double viewportHeight = Math.max(baseHeight + STRUCTURED_RUN_OUTPUT_VIEWPORT_EXTRA_HEIGHT,
                DEFAULT_RUN_OUTPUT_VIEWPORT_HEIGHT);
        runOutputStructuredScrollPane.setMinHeight(viewportHeight);
        runOutputStructuredScrollPane.setPrefHeight(viewportHeight);
        runOutputStructuredScrollPane.setMaxHeight(viewportHeight);
        runOutputStructuredScrollPane.setPrefViewportHeight(viewportHeight);
    }

    private boolean endsWithLineBreak(StringBuilder builder) {
        if (builder == null || builder.isEmpty()) {
            return false;
        }
        char lastCharacter = builder.charAt(builder.length() - 1);
        return lastCharacter == '\n' || lastCharacter == '\r';
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void updateExecutionProfileFieldState() {
        boolean enableWslFields = hostOperatingSystem.supportsWslProfiles() && wslExecutionProfileRadioButton.isSelected();
        wslExecutionProfileFieldsContainer.setVisible(enableWslFields);
        wslDistributionField.setDisable(!enableWslFields);
        windowsPathPrefixField.setDisable(!enableWslFields);
        wslPathPrefixField.setDisable(!enableWslFields);
    }

    private void configureAgentSettingsSelection() {
        configureProviderChoiceComboBox(executionSettingsProviderComboBox);
        configureProviderChoiceComboBox(planningSettingsProviderComboBox);
        configureExecutionModelChoiceComboBox(executionSettingsModelComboBox);
        configureExecutionModelChoiceComboBox(planningSettingsModelComboBox);
        configureThinkingLevelChoiceComboBox(executionThinkingLevelComboBox);
        configureThinkingLevelChoiceComboBox(planningThinkingLevelComboBox);

        executionSettingsProviderComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectDefaultRunOutputViewForProvider(newValue == null ? null : newValue.provider());
            refreshStageModelChoices(
                    StageConfigurationContext.EXECUTION,
                    newValue,
                    "",
                    selectedThinkingLevel(StageConfigurationContext.EXECUTION)
            );
        });
        planningSettingsProviderComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            refreshStageModelChoices(
                    StageConfigurationContext.PLANNING,
                    newValue,
                    "",
                    selectedThinkingLevel(StageConfigurationContext.PLANNING)
            );
        });
        executionSettingsModelComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                refreshThinkingLevelChoices(
                        StageConfigurationContext.EXECUTION,
                        newValue,
                        selectedThinkingLevel(StageConfigurationContext.EXECUTION)
                )
        );
        planningSettingsModelComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                refreshThinkingLevelChoices(
                        StageConfigurationContext.PLANNING,
                        newValue,
                        selectedThinkingLevel(StageConfigurationContext.PLANNING)
                )
        );

        refreshAvailableAgentProviderChoices();
    }

    private void configureProviderChoiceComboBox(ComboBox<ExecutionAgentProviderChoice> comboBox) {
        comboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ExecutionAgentProviderChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
        comboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ExecutionAgentProviderChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
    }

    private void refreshAvailableAgentProviderChoices() {
        availableExecutionAgentProviderChoices = availableProviderChoicesForCurrentProfile();
        applyPersistedAgentSettings();
    }

    private List<ExecutionAgentProviderChoice> availableProviderChoicesForCurrentProfile() {
        return executionAgentModelCatalogService.providers().stream()
                .filter(providerSupport -> providerSupport.provider().executionSupported())
                .map(ExecutionAgentProviderChoice::fromProviderSupport)
                .filter(choice -> providerAvailableForCurrentProfile(choice.provider()))
                .toList();
    }

    private boolean providerAvailableForCurrentProfile(ExecutionAgentProvider provider) {
        if (provider == null || !provider.executionSupported()) {
            return false;
        }
        Optional<ExecutionProfile> executionProfile = activeProjectService.executionProfile();
        if (executionProfile.isEmpty()) {
            return false;
        }
        if (executionProfile.get().type() == ExecutionProfile.ProfileType.WSL) {
            Optional<WslPreflightReport> report = activeProjectService.latestWslPreflightReport();
            return report.isPresent()
                    && report.get().passed("wsl_distribution")
                    && report.get().passed("path_mapping")
                    && report.get().passed(provider.toolingCheckId());
        }
        Optional<NativeWindowsPreflightReport> report = activeProjectService.latestNativeWindowsPreflightReport();
        return report.isPresent() && report.get().passed(provider.toolingCheckId());
    }

    private void applyPersistedAgentSettings() {
        applyStageSelection(StageConfigurationContext.EXECUTION, userPreferencesSettingsService.executionStageSelection());
        applyStageSelection(StageConfigurationContext.PLANNING, userPreferencesSettingsService.planningStageSelection());
    }

    private void applyStageSelection(StageConfigurationContext stageConfigurationContext,
                                     ExecutionAgentSelection persistedSelection) {
        ComboBox<ExecutionAgentProviderChoice> providerComboBox = stageProviderComboBox(stageConfigurationContext);
        providerComboBox.getItems().setAll(availableExecutionAgentProviderChoices);
        ExecutionAgentProviderChoice providerChoice = providerChoiceFor(persistedSelection.provider());
        if (providerChoice == null && !availableExecutionAgentProviderChoices.isEmpty()) {
            providerChoice = availableExecutionAgentProviderChoices.getFirst();
        }
        providerComboBox.getSelectionModel().select(providerChoice);
        refreshStageModelChoices(
                stageConfigurationContext,
                providerChoice,
                persistedSelection.modelId(),
                persistedSelection.thinkingLevel()
        );
    }

    private void configureExecutionModelChoiceComboBox(ComboBox<ExecutionModelChoice> comboBox) {
        comboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ExecutionModelChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
        comboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ExecutionModelChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
    }

    private void configureThinkingLevelChoiceComboBox(ComboBox<ThinkingLevelChoice> comboBox) {
        comboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ThinkingLevelChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
        comboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ThinkingLevelChoice choice, boolean empty) {
                super.updateItem(choice, empty);
                setText(empty || choice == null ? null : choice.label());
            }
        });
    }

    private void refreshStageModelChoices(StageConfigurationContext stageConfigurationContext,
                                          ExecutionAgentProviderChoice providerChoice,
                                          String selectedModelId,
                                          String selectedThinkingLevel) {
        ComboBox<ExecutionModelChoice> modelComboBox = stageModelComboBox(stageConfigurationContext);
        modelComboBox.getItems().setAll(List.of(ExecutionModelChoice.cliDefault()));
        modelComboBox.getSelectionModel().selectFirst();
        if (providerChoice == null) {
            refreshThinkingLevelChoices(stageConfigurationContext, ExecutionModelChoice.cliDefault(), "");
            stageSaveButton(stageConfigurationContext).setDisable(true);
            setStageSettingsStatus(stageConfigurationContext, noAvailableAgentsMessage());
            return;
        }
        stageSaveButton(stageConfigurationContext).setDisable(false);
        setStageSettingsStatus(stageConfigurationContext, "Loading models for " + providerChoice.label() + "...");
        CompletableFuture
                .supplyAsync(() -> executionAgentModelCatalogService.modelsFor(providerChoice.provider()),
                        modelCatalogExecutor)
                .whenCompleteAsync((catalog, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        refreshThinkingLevelChoices(stageConfigurationContext, ExecutionModelChoice.cliDefault(), "");
                        setStageSettingsStatus(stageConfigurationContext, "Unable to load models (" + throwable.getMessage() + ")");
                        return;
                    }
                    if (catalog.models().isEmpty()) {
                        refreshThinkingLevelChoices(stageConfigurationContext, ExecutionModelChoice.cliDefault(), "");
                        setStageSettingsStatus(stageConfigurationContext, catalog.message());
                        return;
                    }
                    List<ExecutionModelChoice> choices = catalog.models().stream()
                            .map(ExecutionModelChoice::fromModelOption)
                            .toList();
                    modelComboBox.getItems().setAll(ExecutionModelChoice.cliDefault());
                    modelComboBox.getItems().addAll(choices);
                    Optional<ExecutionModelChoice> selectedChoice = modelComboBox.getItems().stream()
                            .filter(choice -> choice.modelId().equals(selectedModelId))
                            .findFirst();
                    ExecutionModelChoice appliedChoice = selectedChoice.orElseGet(ExecutionModelChoice::cliDefault);
                    modelComboBox.getSelectionModel().select(appliedChoice);
                    refreshThinkingLevelChoices(stageConfigurationContext, appliedChoice, selectedThinkingLevel);
                    String detail = hasText(providerChoice.detail()) ? providerChoice.detail() : "";
                    setStageSettingsStatus(stageConfigurationContext, joinMessageParts(catalog.message(), detail));
                }), Platform::runLater);
    }

    private void refreshThinkingLevelChoices(StageConfigurationContext stageConfigurationContext,
                                             ExecutionModelChoice modelChoice,
                                             String selectedThinkingLevel) {
        ComboBox<ThinkingLevelChoice> comboBox = stageThinkingLevelComboBox(stageConfigurationContext);
        List<ThinkingLevelChoice> thinkingLevelChoices = new ArrayList<>();
        thinkingLevelChoices.add(ThinkingLevelChoice.cliDefault());
        if (modelChoice != null) {
            thinkingLevelChoices.addAll(modelChoice.thinkingLevels().stream()
                    .map(ThinkingLevelChoice::of)
                    .toList());
        }
        comboBox.getItems().setAll(thinkingLevelChoices);
        ThinkingLevelChoice selectedChoice = thinkingLevelChoices.stream()
                .filter(choice -> choice.value().equalsIgnoreCase(valueOrEmpty(selectedThinkingLevel)))
                .findFirst()
                .orElseGet(ThinkingLevelChoice::cliDefault);
        comboBox.getSelectionModel().select(selectedChoice);
    }

    private String noAvailableAgentsMessage() {
        Optional<ExecutionProfile> executionProfile = activeProjectService.executionProfile();
        if (executionProfile.isEmpty()) {
            return "Save an execution profile and run preflight to detect available agents.";
        }
        return executionProfile.get().type() == ExecutionProfile.ProfileType.WSL
                ? "Run WSL preflight to detect which agents are available in the selected distribution."
                : "Run native preflight to detect which agents are available on this machine.";
    }

    private void selectDefaultRunOutputViewForProvider(ExecutionAgentProvider provider) {
        if (provider == ExecutionAgentProvider.GITHUB_COPILOT) {
            rawOutputViewRadioButton.setSelected(true);
            return;
        }
        if (provider == ExecutionAgentProvider.CODEX) {
            structuredRunOutputViewRadioButton.setSelected(true);
            return;
        }
        if (runOutputViewToggleGroup.getSelectedToggle() == null) {
            structuredRunOutputViewRadioButton.setSelected(true);
        }
    }

    private void configurePresetCatalog() {
        configurePresetToggle(prdCreationPresetRadioButton, PresetUseCase.PRD_CREATION);
        configurePresetToggle(storyImplementationPresetRadioButton, PresetUseCase.STORY_IMPLEMENTATION);
        configurePresetToggle(retryFixPresetRadioButton, PresetUseCase.RETRY_FIX);
        configurePresetToggle(runSummaryPresetRadioButton, PresetUseCase.RUN_SUMMARY);
        presetCatalogToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            PresetUseCase selectedUseCase = selectedPresetUseCase();
            if (selectedUseCase == PresetUseCase.STORY_IMPLEMENTATION
                    || selectedUseCase == PresetUseCase.RETRY_FIX
                    || selectedUseCase == PresetUseCase.RUN_SUMMARY) {
                lastExecutionWorkflowPreset = selectedUseCase;
            }
            renderPresetPreview(selectedUseCase);
            renderSingleStorySession();
        });
        presetPromptPreviewArea.setEditable(false);
        storyImplementationPresetRadioButton.setSelected(true);
        renderPresetPreview(PresetUseCase.STORY_IMPLEMENTATION);
    }

    private void configurePresetToggle(RadioButton radioButton, PresetUseCase useCase) {
        radioButton.setToggleGroup(presetCatalogToggleGroup);
        radioButton.setUserData(useCase);
    }

    private PresetUseCase selectedPresetUseCase() {
        if (presetCatalogToggleGroup.getSelectedToggle() == null) {
            return preferredExecutionWorkflowPreset();
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
        renderRunHistory();
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
                + " planning inputs captured. Start with the prompt, then refine the PRD through clarification rounds.");
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
        if (hostOperatingSystem.supportsWslProfiles() && wslExecutionProfileRadioButton.isSelected()) {
            return new ExecutionProfile(
                    ExecutionProfile.ProfileType.WSL,
                    wslDistributionField.getText(),
                    windowsPathPrefixField.getText(),
                    wslPathPrefixField.getText()
            );
        }

        return ExecutionProfile.nativeHost();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private ExecutionAgentSelection selectedExecutionAgentSelection() {
        ExecutionAgentProvider provider = Optional.ofNullable(executionSettingsProviderComboBox.getValue())
                .map(ExecutionAgentProviderChoice::provider)
                .orElse(ExecutionAgentProvider.CODEX);
        String modelId = Optional.ofNullable(executionSettingsModelComboBox.getValue())
                .map(ExecutionModelChoice::modelId)
                .orElse("");
        return new ExecutionAgentSelection(provider, modelId, selectedThinkingLevel(StageConfigurationContext.EXECUTION));
    }

    private ExecutionAgentSelection selectedPlanningAgentSelection() {
        ExecutionAgentProvider provider = Optional.ofNullable(planningSettingsProviderComboBox.getValue())
                .map(ExecutionAgentProviderChoice::provider)
                .orElse(ExecutionAgentProvider.CODEX);
        String modelId = Optional.ofNullable(planningSettingsModelComboBox.getValue())
                .map(ExecutionModelChoice::modelId)
                .orElse("");
        return new ExecutionAgentSelection(provider, modelId, selectedThinkingLevel(StageConfigurationContext.PLANNING));
    }

    @FXML
    private void saveExecutionAgentSettings() {
        ExecutionAgentSelection savedSelection =
                userPreferencesSettingsService.saveExecutionStageSelection(selectedExecutionAgentSelection());
        refreshThinkingLevelChoices(
                StageConfigurationContext.EXECUTION,
                executionSettingsModelComboBox.getValue(),
                savedSelection.thinkingLevel()
        );
        setStageSettingsStatus(StageConfigurationContext.EXECUTION,
                "Saved execution agent settings for " + savedSelection.provider().displayName() + ".");
        renderSingleStorySession();
        renderRunOutputView();
    }

    @FXML
    private void savePlanningAgentSettings() {
        ExecutionAgentSelection savedSelection =
                userPreferencesSettingsService.savePlanningStageSelection(selectedPlanningAgentSelection());
        refreshThinkingLevelChoices(
                StageConfigurationContext.PLANNING,
                planningSettingsModelComboBox.getValue(),
                savedSelection.thinkingLevel()
        );
        setStageSettingsStatus(StageConfigurationContext.PLANNING,
                "Saved planning agent settings for " + savedSelection.provider().displayName() + ".");
        renderPrdPlanner(currentPrdPlanningSession);
    }

    private void ensurePrdPlannerVisible() {
        if (workspaceScrollPane == null || workspacePane == null || livePrdPlannerCard == null) {
            return;
        }
        Platform.runLater(() -> {
            moveWorkspaceToPrdPlanner();
            Platform.runLater(this::moveWorkspaceToPrdPlanner);
        });
    }

    private void moveWorkspaceToPrdPlanner() {
        if (workspaceScrollPane == null || workspacePane == null || livePrdPlannerCard == null) {
            return;
        }

        Bounds contentBounds = workspacePane.getLayoutBounds();
        Bounds targetBounds = livePrdPlannerCard.getBoundsInParent();
        double availableHeight = workspaceScrollPane.getViewportBounds().getHeight();
        double scrollableHeight = Math.max(contentBounds.getHeight() - availableHeight, 0.0);
        if (scrollableHeight <= 0.0) {
            workspaceScrollPane.setVvalue(0.0);
        } else {
            double targetOffset = Math.max(targetBounds.getMinY() - 12.0, 0.0);
            workspaceScrollPane.setVvalue(Math.min(targetOffset / scrollableHeight, 1.0));
        }
        if (prdPlannerRequestInProgress || prdPlannerInputArea.isDisabled()) {
            prdPlannerTranscriptArea.requestFocus();
            return;
        }
        prdPlannerInputArea.requestFocus();
    }

    private void ensurePrdDocumentVisible() {
        if (workspaceScrollPane == null || workspacePane == null || prdDocumentCard == null) {
            return;
        }
        Platform.runLater(() -> {
            moveWorkspaceToPrdDocument();
            Platform.runLater(this::moveWorkspaceToPrdDocument);
        });
    }

    private void moveWorkspaceToPrdDocument() {
        if (workspaceScrollPane == null || workspacePane == null || prdDocumentCard == null) {
            return;
        }

        Bounds contentBounds = workspacePane.getLayoutBounds();
        Bounds targetBounds = prdDocumentCard.getBoundsInParent();
        double availableHeight = workspaceScrollPane.getViewportBounds().getHeight();
        double scrollableHeight = Math.max(contentBounds.getHeight() - availableHeight, 0.0);
        if (scrollableHeight <= 0.0) {
            workspaceScrollPane.setVvalue(0.0);
        } else {
            double targetOffset = Math.max(targetBounds.getMinY() - 12.0, 0.0);
            workspaceScrollPane.setVvalue(Math.min(targetOffset / scrollableHeight, 1.0));
        }
        prdDocumentPreviewArea.requestFocus();
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

    private void setStageSettingsStatus(StageConfigurationContext stageConfigurationContext, String message) {
        Label statusLabel = stageStatusLabel(stageConfigurationContext);
        boolean hasMessage = hasText(message);
        statusLabel.setText(hasMessage ? message : "");
        statusLabel.setVisible(hasMessage);
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

    private void setPrdPlannerMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        prdPlannerMessageLabel.setText(hasMessage ? message : "");
        prdPlannerMessageLabel.setVisible(hasMessage);
    }

    private Label stageStatusLabel(StageConfigurationContext stageConfigurationContext) {
        return stageConfigurationContext == StageConfigurationContext.PLANNING
                ? planningSettingsStatusLabel
                : executionSettingsStatusLabel;
    }

    private ComboBox<ExecutionModelChoice> stageModelComboBox(StageConfigurationContext stageConfigurationContext) {
        return stageConfigurationContext == StageConfigurationContext.PLANNING
                ? planningSettingsModelComboBox
                : executionSettingsModelComboBox;
    }

    private ComboBox<ExecutionAgentProviderChoice> stageProviderComboBox(
            StageConfigurationContext stageConfigurationContext) {
        return stageConfigurationContext == StageConfigurationContext.PLANNING
                ? planningSettingsProviderComboBox
                : executionSettingsProviderComboBox;
    }

    private ComboBox<ThinkingLevelChoice> stageThinkingLevelComboBox(
            StageConfigurationContext stageConfigurationContext) {
        return stageConfigurationContext == StageConfigurationContext.PLANNING
                ? planningThinkingLevelComboBox
                : executionThinkingLevelComboBox;
    }

    private Button stageSaveButton(StageConfigurationContext stageConfigurationContext) {
        return stageConfigurationContext == StageConfigurationContext.PLANNING
                ? savePlanningAgentSettingsButton
                : saveExecutionAgentSettingsButton;
    }

    private String selectedThinkingLevel(StageConfigurationContext stageConfigurationContext) {
        return Optional.ofNullable(stageThinkingLevelComboBox(stageConfigurationContext).getValue())
                .map(ThinkingLevelChoice::value)
                .orElse("");
    }

    private ExecutionAgentProviderChoice providerChoiceFor(ExecutionAgentProvider provider) {
        return availableExecutionAgentProviderChoices.stream()
                .filter(choice -> choice.provider() == provider)
                .findFirst()
                .orElse(null);
    }

    private String describeAgentSelection(ExecutionAgentSelection executionAgentSelection) {
        List<String> parts = new ArrayList<>();
        parts.add(executionAgentSelection.provider().displayName());
        if (hasText(executionAgentSelection.modelId())) {
            parts.add("Model " + executionAgentSelection.modelId());
        } else {
            parts.add("Model CLI default");
        }
        if (hasText(executionAgentSelection.thinkingLevel())) {
            parts.add("Thinking " + executionAgentSelection.thinkingLevel());
        }
        return String.join(" | ", parts);
    }

    private String agentSelectionDetail(ExecutionAgentSelection executionAgentSelection,
                                        StageConfigurationContext stageConfigurationContext) {
        String reasoningDetail = hasText(executionAgentSelection.thinkingLevel())
                ? "Thinking level " + executionAgentSelection.thinkingLevel() + " will be passed to --reasoning-effort."
                : "Thinking level will use the CLI default.";
        if (executionAgentSelection.provider() == ExecutionAgentProvider.GITHUB_COPILOT) {
            return joinMessageParts(
                    "GitHub Copilot uses raw output only.",
                    reasoningDetail
            );
        }
        String statusText = stageStatusLabel(stageConfigurationContext).getText();
        return hasText(statusText)
                ? joinMessageParts(statusText, reasoningDetail)
                : joinMessageParts("Codex structured output is enabled by default when runs start.", reasoningDetail);
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

    private record PlannerReadinessStatus(int score, String detail) {
    }

    private record ExecutionAgentProviderChoice(ExecutionAgentProvider provider, String label, String detail) {
        private static ExecutionAgentProviderChoice fromProviderSupport(
                ExecutionAgentModelCatalogService.ProviderSupport providerSupport) {
            String displayLabel = providerSupport.enabled()
                    ? providerSupport.displayName()
                    : providerSupport.displayName() + " (Coming Soon)";
            return new ExecutionAgentProviderChoice(providerSupport.provider(), displayLabel, providerSupport.detail());
        }
    }

    private record ExecutionModelChoice(String modelId, String label, List<String> thinkingLevels) {
        private static ExecutionModelChoice cliDefault() {
            return new ExecutionModelChoice("", "CLI Default", List.of());
        }

        private static ExecutionModelChoice fromModelOption(ExecutionAgentModelCatalogService.ModelOption modelOption) {
            String displayLabel = hasText(modelOption.displayName())
                    ? modelOption.displayName() + " (" + modelOption.modelId() + ")"
                    : modelOption.modelId();
            return new ExecutionModelChoice(modelOption.modelId(), displayLabel, modelOption.thinkingLevels());
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    private record ThinkingLevelChoice(String value, String label) {
        private static ThinkingLevelChoice cliDefault() {
            return new ThinkingLevelChoice("", "CLI Default");
        }

        private static ThinkingLevelChoice of(String value) {
            String normalizedValue = value == null ? "" : value.trim().toLowerCase();
            return new ThinkingLevelChoice(normalizedValue, normalizedValue);
        }
    }

    private record StructuredRunOutputEntry(String key,
                                            String badge,
                                            String title,
                                            String body,
                                            String detail,
                                            String accentStyleClass,
                                            boolean commandOutput) {
    }

    private record CommandOutputDisplay(String visibleText, int visibleLineCount, boolean truncated) {
    }

    private record ScrollPositionState(boolean stickToBottom,
                                       double offsetFromTop,
                                       double offsetFromBottom) {
    }

    private record StructuredCommandOutputViewState(boolean expanded, ScrollPositionState scrollPosition) {
        private static StructuredCommandOutputViewState collapsed() {
            return new StructuredCommandOutputViewState(false, null);
        }

        private StructuredCommandOutputViewState withExpanded(boolean replacementExpanded) {
            return new StructuredCommandOutputViewState(replacementExpanded, scrollPosition);
        }

        private StructuredCommandOutputViewState withScrollPosition(ScrollPositionState replacementScrollPosition) {
            return new StructuredCommandOutputViewState(expanded, replacementScrollPosition);
        }
    }

    private enum StageConfigurationContext {
        EXECUTION,
        PLANNING
    }

    private enum RunOutputView {
        ASSISTANT_SUMMARY,
        RAW_OUTPUT,
        STRUCTURED_OUTPUT
    }

    private enum RunHistoryArtifactType {
        PROMPT("Prompt", "Prompt", "OpenPrompt"),
        STDOUT("Stdout", "Stdout Log", "OpenStdout"),
        STDERR("Stderr", "Stderr Log", "OpenStderr"),
        STRUCTURED_EVENTS("Events", "Structured Events", "OpenEvents"),
        ASSISTANT_SUMMARY("Assistant Summary", "Assistant Summary", "OpenAssistantSummary"),
        ATTEMPT_SUMMARY("Artifact Summary", "Artifact Summary", "OpenArtifactSummary");

        private final String buttonLabel;
        private final String viewerLabel;
        private final String idFragment;

        RunHistoryArtifactType(String buttonLabel, String viewerLabel, String idFragment) {
            this.buttonLabel = buttonLabel;
            this.viewerLabel = viewerLabel;
            this.idFragment = idFragment;
        }

        private String buttonLabel() {
            return buttonLabel;
        }

        private String viewerLabel() {
            return viewerLabel;
        }

        private String idFragment() {
            return idFragment;
        }
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
                                              ExecutionAgentProvider provider,
                                              String summary,
                                              String detail,
                                              String assistantSummary,
                                              String rawOutput) {
        private static RunOutputPresentationState empty() {
            return new RunOutputPresentationState(
                    false,
                    false,
                    ExecutionAgentProvider.CODEX,
                    NO_PERSISTED_RUN_OUTPUT_SUMMARY,
                    NO_PERSISTED_RUN_OUTPUT_DETAIL,
                    "",
                    ""
            );
        }

        private static RunOutputPresentationState live(ExecutionAgentProvider provider,
                                                       String summary,
                                                       String detail,
                                                       String assistantSummary,
                                                       String rawOutput) {
            return new RunOutputPresentationState(
                    true,
                    true,
                    provider == null ? ExecutionAgentProvider.CODEX : provider,
                    summary,
                    detail,
                    assistantSummary,
                    rawOutput
            );
        }

        private RunOutputPresentationState withRawOutput(String replacementRawOutput) {
            return new RunOutputPresentationState(
                    available,
                    active,
                    provider,
                    summary,
                    detail,
                    assistantSummary,
                    replacementRawOutput == null ? "" : replacementRawOutput
            );
        }
    }

    private record RunHistoryArtifactViewerState(String summary,
                                                 String path,
                                                 String content) {
        private static RunHistoryArtifactViewerState empty() {
            return new RunHistoryArtifactViewerState(
                    NO_SELECTED_HISTORY_ARTIFACT_SUMMARY,
                    "",
                    NO_SELECTED_HISTORY_ARTIFACT_DETAIL
            );
        }
    }
}
