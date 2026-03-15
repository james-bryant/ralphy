package net.uberfoo.ai.ralphy;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public class RalphyApplication extends Application {
    private final JavaFxSpringBridge springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);

    @Override
    public void init() {
        springBridge.start(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage stage) throws IOException {
        springBridge.getRequiredBean(AppShellStageConfigurer.class).configure(stage);
        stage.show();
    }

    @Override
    public void stop() {
        springBridge.close();
    }
}
