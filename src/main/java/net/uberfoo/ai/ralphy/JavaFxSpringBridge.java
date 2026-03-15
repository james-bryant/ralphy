package net.uberfoo.ai.ralphy;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

final class JavaFxSpringBridge implements AutoCloseable {
    private final Class<?> applicationClass;
    private ConfigurableApplicationContext applicationContext;

    JavaFxSpringBridge(Class<?> applicationClass) {
        this.applicationClass = applicationClass;
    }

    void start(String[] args) {
        if (applicationContext != null && applicationContext.isActive()) {
            throw new IllegalStateException("Spring context is already active.");
        }

        applicationContext = new SpringApplicationBuilder(applicationClass)
                .headless(false)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    <T> T getRequiredBean(Class<T> type) {
        return getApplicationContext().getBean(type);
    }

    ConfigurableApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring context has not been started.");
        }

        return applicationContext;
    }

    boolean isActive() {
        return applicationContext != null && applicationContext.isActive();
    }

    @Override
    public void close() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }
}
