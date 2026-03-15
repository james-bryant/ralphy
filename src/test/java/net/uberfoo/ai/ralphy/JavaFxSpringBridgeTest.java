package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxSpringBridgeTest {
    private JavaFxSpringBridge springBridge;

    @AfterEach
    void tearDown() {
        if (springBridge != null) {
            springBridge.close();
        }
    }

    @Test
    void startCreatesSpringContextBeforeViewsLoad() {
        springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);

        springBridge.start(new String[0]);

        assertTrue(springBridge.isActive());
        assertNotNull(springBridge.getRequiredBean(SpringFxmlLoader.class));
    }

    @Test
    void controllerFactoryAutowiresSpringManagedDependencies() {
        springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);
        springBridge.start(new String[0]);

        SpringBeanControllerFactory controllerFactory = springBridge.getRequiredBean(SpringBeanControllerFactory.class);
        InjectedController controller = (InjectedController) controllerFactory.call(InjectedController.class);

        assertSame(springBridge.getRequiredBean(GreetingService.class), controller.greetingService());
    }

    @Test
    void closeStopsTheSpringContextAndBackgroundExecutor() {
        springBridge = new JavaFxSpringBridge(RalphySpringApplication.class);
        springBridge.start(new String[0]);

        ThreadPoolTaskExecutor executor = springBridge.getApplicationContext()
                .getBean("ralphyBackgroundExecutor", ThreadPoolTaskExecutor.class);

        springBridge.close();

        assertFalse(springBridge.isActive());
        assertTrue(executor.getThreadPoolExecutor().isShutdown());
    }

    static final class InjectedController {
        private final GreetingService greetingService;

        InjectedController(GreetingService greetingService) {
            this.greetingService = greetingService;
        }

        GreetingService greetingService() {
            return greetingService;
        }
    }
}
