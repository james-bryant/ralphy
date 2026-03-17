package net.uberfoo.ai.ralphy;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

@Component
public class SpringFxmlLoader {
    private final SpringBeanControllerFactory controllerFactory;

    public SpringFxmlLoader(SpringBeanControllerFactory controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    public Parent load(String resourceName) throws IOException {
        FXMLLoader loader = new FXMLLoader(resolveResource(resourceName));
        loader.setControllerFactory(controllerFactory);
        Parent root = loader.load();
        root.getProperties().put("fxml.controller", loader.getController());
        return root;
    }

    private URL resolveResource(String resourceName) {
        URL resource = SpringFxmlLoader.class.getResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException("FXML resource not found: " + resourceName);
        }

        return resource;
    }
}
