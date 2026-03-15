package net.uberfoo.ai.ralphy;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    private final JavaFxSpringBridge springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);

    @Override
    public void init() {
        springBridge.start(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(springBridge.getRequiredBean(SpringFxmlLoader.class).load("hello-view.fxml"), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        springBridge.close();
    }
}
