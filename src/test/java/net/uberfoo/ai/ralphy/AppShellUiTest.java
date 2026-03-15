package net.uberfoo.ai.ralphy;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
    void appShellSceneExposesRalphyNavigationWorkspaceAndStatusRegions() throws Exception {
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
        assertTrue(shellSnapshot.navigationPanePresent());
        assertTrue(shellSnapshot.workspacePanePresent());
        assertTrue(shellSnapshot.statusPanePresent());
    }

    private ShellSnapshot captureShellSnapshot(Scene scene) {
        Parent root = scene.getRoot();

        assertNotNull(root.lookup("#navigationPane"));
        assertNotNull(root.lookup("#workspacePane"));
        assertNotNull(root.lookup("#statusPane"));

        return new ShellSnapshot(
                text(root, "#brandLabel"),
                text(root, "#taglineLabel"),
                text(root, "#navigationPlaceholderLabel"),
                text(root, "#workspacePlaceholderLabel"),
                text(root, "#statusLabel"),
                root.lookup("#navigationPane") != null,
                root.lookup("#workspacePane") != null,
                root.lookup("#statusPane") != null
        );
    }

    private static <T> T onFxThread(ThrowingSupplier<T> supplier)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String text(Parent root, String selector) {
        javafx.scene.control.Labeled labeledNode = (javafx.scene.control.Labeled) root.lookup(selector);
        assertNotNull(labeledNode, "Missing node for selector " + selector);
        return labeledNode.getText();
    }

    private record ShellSnapshot(
            String brandLabel,
            String taglineLabel,
            String navigationPlaceholder,
            String workspacePlaceholder,
            String statusText,
            boolean navigationPanePresent,
            boolean workspacePanePresent,
            boolean statusPanePresent
    ) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
