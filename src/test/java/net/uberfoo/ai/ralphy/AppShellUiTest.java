package net.uberfoo.ai.ralphy;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellUiTest {
    private static final long FX_TIMEOUT_SECONDS = 10;

    private JavaFxSpringBridge springBridge;

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        System.setProperty("prism.order", "sw");

        CompletableFuture<Void> startupFuture = new CompletableFuture<>();
        try {
            Platform.startup(() -> startupFuture.complete(null));
        } catch (IllegalStateException alreadyStarted) {
            startupFuture.complete(null);
        }

        startupFuture.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (springBridge != null) {
            springBridge.close();
        }
    }

    @AfterAll
    static void stopJavaFxToolkit() {
        Platform.exit();
    }

    @Test
    void appShellSceneUsesSharedDarkThemeAndExposesRalphyNavigationWorkspaceAndStatusRegions() throws Exception {
        springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);
        springBridge.start(new String[0]);

        AppShellDescriptor shellDescriptor = springBridge.getRequiredBean(AppShellDescriptor.class);
        AppShellStageConfigurer stageConfigurer = springBridge.getRequiredBean(AppShellStageConfigurer.class);

        ShellSnapshot shellSnapshot = onFxThread(() -> captureShellSnapshot(stageConfigurer.createScene()));

        assertEquals("Ralphy", shellDescriptor.windowTitle());
        assertEquals(shellDescriptor.appName(), shellSnapshot.brandLabel());
        assertEquals(shellDescriptor.shellTagline(), shellSnapshot.taglineLabel());
        assertEquals(shellDescriptor.navigationPlaceholder(), shellSnapshot.navigationPlaceholder());
        assertEquals(shellDescriptor.workspacePlaceholder(), shellSnapshot.workspacePlaceholder());
        assertEquals(shellDescriptor.statusPlaceholder(), shellSnapshot.statusText());
        assertTrue(shellSnapshot.themeStylesheetPresent());
        assertTrue(shellSnapshot.themeRootStyleClassPresent());
        assertTrue(shellSnapshot.navigationPanePresent());
        assertTrue(shellSnapshot.workspacePanePresent());
        assertTrue(shellSnapshot.statusPanePresent());
        assertEquals(Color.web("#020617"), shellSnapshot.shellBackground());
        assertEquals(Color.web("#0f172a"), shellSnapshot.navigationBackground());
        assertEquals(Color.web("#111827"), shellSnapshot.workspaceBackground());
        assertEquals(Color.web("#0f172a"), shellSnapshot.statusBackground());
        assertEquals(Color.web("#e5eefc"), shellSnapshot.brandTextFill());
        assertEquals(Color.web("#94a3b8"), shellSnapshot.taglineTextFill());
    }

    private ShellSnapshot captureShellSnapshot(Scene scene) {
        Parent root = scene.getRoot();
        root.applyCss();

        Region navigationPane = region(root, "#navigationPane");
        Region workspacePane = region(root, "#workspacePane");
        Region statusPane = region(root, "#statusPane");
        Region shellRoot = (Region) root;

        return new ShellSnapshot(
                text(root, "#brandLabel"),
                text(root, "#taglineLabel"),
                text(root, "#navigationPlaceholderLabel"),
                text(root, "#workspacePlaceholderLabel"),
                text(root, "#statusLabel"),
                scene.getStylesheets().contains(AppTheme.stylesheetUrl()),
                shellRoot.getStyleClass().contains(AppTheme.rootStyleClass()),
                true,
                true,
                true,
                backgroundColor(shellRoot),
                backgroundColor(navigationPane),
                backgroundColor(workspacePane),
                backgroundColor(statusPane),
                textFill(root, "#brandLabel"),
                textFill(root, "#taglineLabel")
        );
    }

    private static <T> T onFxThread(ThrowingSupplier<T> supplier)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String text(Parent root, String selector) {
        Labeled labeledNode = labeled(root, selector);
        return labeledNode.getText();
    }

    private Labeled labeled(Parent root, String selector) {
        Labeled labeledNode = (Labeled) root.lookup(selector);
        assertNotNull(labeledNode, "Missing node for selector " + selector);
        return labeledNode;
    }

    private Region region(Parent root, String selector) {
        Region region = (Region) root.lookup(selector);
        assertNotNull(region, "Missing node for selector " + selector);
        return region;
    }

    private Color backgroundColor(Region region) {
        assertNotNull(region.getBackground(), "Expected background for region " + region.getId());
        return (Color) region.getBackground().getFills().get(0).getFill();
    }

    private Color textFill(Parent root, String selector) {
        return (Color) labeled(root, selector).getTextFill();
    }

    private record ShellSnapshot(
            String brandLabel,
            String taglineLabel,
            String navigationPlaceholder,
            String workspacePlaceholder,
            String statusText,
            boolean themeStylesheetPresent,
            boolean themeRootStyleClassPresent,
            boolean navigationPanePresent,
            boolean workspacePanePresent,
            boolean statusPanePresent,
            Color shellBackground,
            Color navigationBackground,
            Color workspaceBackground,
            Color statusBackground,
            Color brandTextFill,
            Color taglineTextFill
    ) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
