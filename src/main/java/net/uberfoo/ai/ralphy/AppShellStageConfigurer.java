package net.uberfoo.ai.ralphy;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AppShellStageConfigurer {
    private static final double DEFAULT_HEIGHT = 800;
    private static final double DEFAULT_WIDTH = 1280;
    private static final double MIN_HEIGHT = 640;
    private static final double MIN_WIDTH = 960;

    private final AppShellDescriptor shellDescriptor;
    private final SpringFxmlLoader springFxmlLoader;

    public AppShellStageConfigurer(AppShellDescriptor shellDescriptor, SpringFxmlLoader springFxmlLoader) {
        this.shellDescriptor = shellDescriptor;
        this.springFxmlLoader = springFxmlLoader;
    }

    public void configure(Stage stage) throws IOException {
        stage.setTitle(shellDescriptor.windowTitle());
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(createScene());
    }

    public Scene createScene() throws IOException {
        Scene scene = new Scene(springFxmlLoader.load("app-shell-view.fxml"), DEFAULT_WIDTH, DEFAULT_HEIGHT);
        AppTheme.apply(scene);
        return scene;
    }
}
