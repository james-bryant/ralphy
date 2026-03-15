# Ralph Progress Log

This file tracks progress across iterations. Agents update this file
after each iteration and it's included in prompts for context.

## Codebase Patterns (Study These First)

*Add reusable patterns discovered during development here.*
- For JPMS with Spring on the JavaFX desktop app, explicitly `requires spring.beans` and `requires spring.core` when `module-info.java` opens packages to those modules; relying on `spring.context` alone leaves compiler warnings.
- Bridge JavaFX FXML to Spring through a dedicated controller factory that calls `AutowireCapableBeanFactory#createBean(...)`; this gives each loaded view a fresh controller instance while still enabling constructor injection of Spring beans.

---

## 2026-03-15 - US-001
- Verified and completed the Spring Boot desktop dependency setup using `spring-boot-starter` so DI, typed configuration support, and logging are available without introducing a web server.
- Added JaCoCo Maven plugin wiring so `.\mvnw.cmd clean verify jacoco:report` succeeds as required.
- Updated the Maven wrapper to store its distribution inside the project so the wrapper runs in this workspace without depending on an external home directory.
- Confirmed the JavaFX launcher still starts with the Spring dependencies present via a direct `Launcher` module-path smoke test.
- Files changed: `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, `src/main/java/module-info.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - JPMS + Spring needs explicit `requires` entries for Spring modules referenced in `opens ... to ...`.
    - Keeping Maven wrapper state project-local avoids environment-specific failures during automated verification.
  - Gotchas encountered
    - The required `jacoco:report` goal failed until `org.jacoco:jacoco-maven-plugin` was declared in `pom.xml`.
    - JavaFX startup in this sandbox logs cache-directory warnings under `C:\Users\CodexSandboxOnline\.openjfx`, but the launcher still starts and remains running.
---

## 2026-03-15 - US-002
- Bootstrapped Spring from the JavaFX application lifecycle with a dedicated `JavaFxSpringBridge`, so the Spring context starts in `HelloApplication.init()` before the main FXML view is loaded and closes in `stop()`.
- Added a documented `SpringBeanControllerFactory` plus `SpringFxmlLoader` integration point so FXML controllers can use constructor injection for Spring-managed services, demonstrated with `HelloController` and `GreetingService`.
- Registered a Spring-managed `ralphyBackgroundExecutor` that waits for queued work on shutdown, and added lifecycle tests covering startup, controller autowiring, and executor shutdown.
- Updated Maven Surefire to a JUnit 5-capable version so the new tests actually execute during `verify`.
- Files changed: `pom.xml`, `src/main/java/net/uberfoo/ai/ralphy/BackgroundExecutorConfiguration.java`, `src/main/java/net/uberfoo/ai/ralphy/GreetingService.java`, `src/main/java/net/uberfoo/ai/ralphy/HelloApplication.java`, `src/main/java/net/uberfoo/ai/ralphy/HelloController.java`, `src/main/java/net/uberfoo/ai/ralphy/JavaFxSpringBridge.java`, `src/main/java/net/uberfoo/ai/ralphy/RalphySpringApplication.java`, `src/main/java/net/uberfoo/ai/ralphy/SpringBeanControllerFactory.java`, `src/main/java/net/uberfoo/ai/ralphy/SpringFxmlLoader.java`, `src/test/java/net/uberfoo/ai/ralphy/JavaFxSpringBridgeTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - Starting Spring in `Application.init()` keeps the JavaFX launch sequence intact while guaranteeing the context is ready before any FXML-backed UI is loaded.
    - A thin `SpringFxmlLoader` wrapper keeps controller wiring consistent and gives later views one obvious place to opt into Spring-backed loading.
  - Gotchas encountered
    - Maven's default Surefire version in this project did not execute JUnit 5 tests, so the build needed an explicit `maven-surefire-plugin` upgrade before lifecycle tests would run.
    - Verifying executor shutdown is straightforward with `ThreadPoolTaskExecutor`, but the assertion must happen after the Spring context closes because the pool stays active for the full app lifetime.
---
