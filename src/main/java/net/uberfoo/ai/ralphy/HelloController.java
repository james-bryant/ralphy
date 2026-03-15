package net.uberfoo.ai.ralphy;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class HelloController {
    private final GreetingService greetingService;

    public HelloController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @FXML
    private Label welcomeText;

    @FXML
    private void initialize() {
        welcomeText.setText(greetingService.defaultGreeting());
    }

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText(greetingService.buttonGreeting());
    }
}
