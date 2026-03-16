package net.uberfoo.ai.ralphy;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class JavaFxUiHarness {
    private static final long FX_TIMEOUT_SECONDS = 10;

    private static boolean toolkitStarted;

    private JavaFxSpringBridge springBridge;
    private Stage stage;

    static synchronized void startToolkit() throws Exception {
        if (toolkitStarted) {
            return;
        }

        System.setProperty("prism.order", "sw");

        CompletableFuture<Void> startupFuture = new CompletableFuture<>();
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                startupFuture.complete(null);
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                startupFuture.complete(null);
            });
        }

        startupFuture.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        toolkitStarted = true;
    }

    static synchronized void stopToolkit() throws Exception {
        if (!toolkitStarted) {
            return;
        }

        CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            Platform.exit();
            shutdownFuture.complete(null);
        });
        shutdownFuture.get(FX_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        toolkitStarted = false;
    }

    void launchPrimaryShell() throws Exception {
        launchPrimaryShell(null);
    }

    void launchPrimaryShell(Path storageDirectory) throws Exception {
        springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);
        springBridge.start(storageDirectory == null
                ? new String[0]
                : new String[]{"--ralphy.storage.directory=" + storageDirectory.toAbsolutePath().normalize()});

        AppShellStageConfigurer stageConfigurer = springBridge.getRequiredBean(AppShellStageConfigurer.class);
        stage = onFxThread(() -> {
            Stage shellStage = new Stage();
            stageConfigurer.configure(shellStage);
            shellStage.show();
            shellStage.getScene().getRoot().applyCss();
            shellStage.getScene().getRoot().layout();
            return shellStage;
        });
    }

    void closeShell() throws Exception {
        if (stage != null) {
            Stage shellStage = stage;
            onFxThread(() -> {
                shellStage.close();
                return null;
            });
            stage = null;
        }

        if (springBridge != null) {
            springBridge.close();
            springBridge = null;
        }
    }

    <T> T getRequiredBean(Class<T> beanType) {
        assertNotNull(springBridge, "Spring bridge must be started before looking up beans.");
        return springBridge.getRequiredBean(beanType);
    }

    boolean hasRootStyleClass(String styleClass) throws Exception {
        return onFxThread(() -> root().getStyleClass().contains(styleClass));
    }

    boolean hasStyleClass(String selector, String styleClass) throws Exception {
        return onFxThread(() -> requiredNode(selector).getStyleClass().contains(styleClass));
    }

    boolean isShowing() throws Exception {
        return onFxThread(() -> stage != null && stage.isShowing());
    }

    boolean sceneHasStylesheet(String stylesheetUrl) throws Exception {
        return onFxThread(() -> scene().getStylesheets().contains(stylesheetUrl));
    }

    Color backgroundColor(String selector) throws Exception {
        return onFxThread(() -> {
            Region region = requiredNode(selector, Region.class);
            assertNotNull(region.getBackground(), "Expected background for region " + selector);
            return (Color) region.getBackground().getFills().get(0).getFill();
        });
    }

    void clickOn(String selector) throws Exception {
        onFxThread(() -> {
            ButtonBase button = requiredNode(selector, ButtonBase.class);
            button.fire();
            root().applyCss();
            root().layout();
            return null;
        });
    }

    void enterText(String selector, String value) throws Exception {
        onFxThread(() -> {
            TextInputControl textInputControl = requiredNode(selector, TextInputControl.class);
            textInputControl.setText(value);
            root().applyCss();
            root().layout();
            return null;
        });
    }

    String stageTitle() throws Exception {
        return onFxThread(() -> stage.getTitle());
    }

    String text(String selector) throws Exception {
        return onFxThread(() -> {
            Node node = requiredNode(selector);
            if (node instanceof Labeled labeled) {
                return labeled.getText();
            }
            if (node instanceof TextInputControl textInputControl) {
                return textInputControl.getText();
            }
            throw new IllegalArgumentException("Node does not expose text for selector " + selector);
        });
    }

    String textContent(String selector) throws Exception {
        return onFxThread(() -> {
            StringBuilder builder = new StringBuilder();
            appendTextContent(requiredNode(selector), builder);
            return builder.toString();
        });
    }

    boolean isVisible(String selector) throws Exception {
        return onFxThread(() -> requiredNode(selector).isVisible());
    }

    boolean isEditable(String selector) throws Exception {
        return onFxThread(() -> requiredNode(selector, TextInputControl.class).isEditable());
    }

    boolean isDisabled(String selector) throws Exception {
        return onFxThread(() -> requiredNode(selector).isDisable());
    }

    Color textFill(String selector) throws Exception {
        return onFxThread(() -> (Color) requiredNode(selector, Labeled.class).getTextFill());
    }

    private Parent root() {
        return scene().getRoot();
    }

    private Scene scene() {
        assertNotNull(stage, "Shell stage has not been launched.");
        return stage.getScene();
    }

    private Node requiredNode(String selector) {
        Node node = root().lookup(selector);
        assertNotNull(node, "Missing node for selector " + selector);
        return node;
    }

    private <T extends Node> T requiredNode(String selector, Class<T> nodeType) {
        Node node = requiredNode(selector);
        return nodeType.cast(node);
    }

    private void appendTextContent(Node node, StringBuilder builder) {
        if (node instanceof Labeled labeled) {
            appendText(builder, labeled.getText());
        } else if (node instanceof TextInputControl textInputControl) {
            appendText(builder, textInputControl.getText());
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                appendTextContent(child, builder);
            }
        }
    }

    private void appendText(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(value);
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

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
