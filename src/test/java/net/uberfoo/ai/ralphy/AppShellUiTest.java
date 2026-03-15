package net.uberfoo.ai.ralphy;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellUiTest {
    private JavaFxUiHarness harness;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        JavaFxUiHarness.startToolkit();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.closeShell();
        }
    }

    @AfterAll
    static void stopJavaFxToolkit() throws Exception {
        JavaFxUiHarness.stopToolkit();
    }

    @Test
    void appShellLaunchHarnessShowsPrimaryStageAndSupportsNavigationInteractions() throws Exception {
        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell();

        AppShellDescriptor shellDescriptor = harness.getRequiredBean(AppShellDescriptor.class);

        assertEquals("Ralphy", shellDescriptor.windowTitle());
        assertEquals(shellDescriptor.windowTitle(), harness.stageTitle());
        assertTrue(harness.isShowing());
        assertEquals(shellDescriptor.appName(), harness.text("#brandLabel"));
        assertEquals(shellDescriptor.shellTagline(), harness.text("#taglineLabel"));
        assertEquals(shellDescriptor.navigationPlaceholder(), harness.text("#navigationPlaceholderLabel"));
        assertEquals(shellDescriptor.workspacePlaceholder(), harness.text("#workspacePlaceholderLabel"));
        assertEquals(shellDescriptor.statusPlaceholder(), harness.text("#statusLabel"));
        assertTrue(harness.sceneHasStylesheet(AppTheme.stylesheetUrl()));
        assertTrue(harness.hasRootStyleClass(AppTheme.rootStyleClass()));
        assertEquals(Color.web("#020617"), harness.backgroundColor("#shellRoot"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#navigationPane"));
        assertEquals(Color.web("#111827"), harness.backgroundColor("#workspacePane"));
        assertEquals(Color.web("#0f172a"), harness.backgroundColor("#statusPane"));
        assertEquals(Color.web("#e5eefc"), harness.textFill("#brandLabel"));
        assertEquals(Color.web("#94a3b8"), harness.textFill("#taglineLabel"));

        harness.clickOn("#prdEditorNavButton");

        assertEquals("PRD Editor", harness.text("#workspaceTitleLabel"));
        assertEquals("Create, refine, and validate the active PRD in this workspace.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("PRD Editor workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#prdEditorNavButton", "shell-nav-button-active"));

        harness.clickOn("#executionNavButton");

        assertEquals("Execution", harness.text("#workspaceTitleLabel"));
        assertEquals("Run controls, current story progress, and live execution output will appear here.",
                harness.text("#workspacePlaceholderLabel"));
        assertEquals("Execution workspace ready.", harness.text("#statusLabel"));
        assertTrue(harness.hasStyleClass("#executionNavButton", "shell-nav-button-active"));
    }

    @Test
    void appShellCanOpenGitRepositoriesAndRejectNonGitFolders() throws Exception {
        Path nonGitDirectory = Files.createDirectory(tempDir.resolve("plain-folder"));
        Path gitRepository = createGitRepository("sample-repo");

        harness = new JavaFxUiHarness();
        harness.launchPrimaryShell();

        RepositoryDirectoryChooser repositoryDirectoryChooser = harness.getRequiredBean(RepositoryDirectoryChooser.class);

        harness.clickOn("#projectsNavButton");
        repositoryDirectoryChooser.queueSelectionForTest(nonGitDirectory);
        harness.clickOn("#openRepositoryButton");

        assertEquals("Selected folder is not a Git repository: " + nonGitDirectory.toAbsolutePath().normalize(),
                harness.text("#projectValidationMessageLabel"));
        assertEquals("No active repository selected.", harness.text("#activeProjectNameLabel"));
        assertEquals("No active project selected.", harness.text("#activeProjectStatusLabel"));

        repositoryDirectoryChooser.queueSelectionForTest(gitRepository);
        harness.clickOn("#openRepositoryButton");

        Path expectedRepositoryPath = gitRepository.toAbsolutePath().normalize();
        assertEquals(gitRepository.getFileName().toString(), harness.text("#activeProjectNameLabel"));
        assertEquals(expectedRepositoryPath.toString(), harness.text("#activeProjectPathLabel"));
        assertEquals("", harness.text("#projectValidationMessageLabel"));
        assertEquals(gitRepository.getFileName().toString(), harness.text("#activeProjectStatusLabel"));
        assertEquals(expectedRepositoryPath,
                harness.getRequiredBean(ActiveProjectService.class).activeProject().orElseThrow().repositoryPath());
    }

    private Path createGitRepository(String directoryName) throws IOException {
        Path repository = Files.createDirectory(tempDir.resolve(directoryName));
        Files.createDirectory(repository.resolve(".git"));
        return repository;
    }
}
