package net.uberfoo.ai.ralphy;

import javafx.util.Callback;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Documented JavaFX/Spring integration point for controllers and view-models.
 * Register this with {@link javafx.fxml.FXMLLoader#setControllerFactory(Callback)}
 * so constructor-injected collaborators can come from the Spring context.
 */
@Component
public class SpringBeanControllerFactory implements Callback<Class<?>, Object> {
    private final ConfigurableApplicationContext applicationContext;

    public SpringBeanControllerFactory(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object call(Class<?> type) {
        return applicationContext.getAutowireCapableBeanFactory().createBean(type);
    }
}
