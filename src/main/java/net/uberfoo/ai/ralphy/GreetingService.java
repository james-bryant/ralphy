package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String defaultGreeting() {
        return "Spring context ready.";
    }

    public String buttonGreeting() {
        return "Welcome to the Spring-powered JavaFX application!";
    }
}
