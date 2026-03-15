# Ralph Progress Log

This file tracks progress across iterations. Agents update this file
after each iteration and it's included in prompts for context.

## Codebase Patterns (Study These First)

*Add reusable patterns discovered during development here.*
- For JPMS with Spring on the JavaFX desktop app, explicitly `requires spring.beans` and `requires spring.core` when `module-info.java` opens packages to those modules; relying on `spring.context` alone leaves compiler warnings.
- Bridge JavaFX FXML to Spring through a dedicated controller factory that calls `AutowireCapableBeanFactory#createBean(...)`; this gives each loaded view a fresh controller instance while still enabling constructor injection of Spring beans.
- Keep the JavaFX `Application` class thin by delegating stage setup to a Spring-managed configurer; the launcher stays conventional while tests can load and inspect the primary scene on the FX thread without calling `Application.launch(...)`.
- Apply JavaFX theming at scene creation by attaching one shared stylesheet plus a root style class; descendant controls can then consume looked-up color tokens and reusable surface classes without each view loading CSS separately.
- For deterministic JavaFX smoke tests, start the toolkit once with `Platform.setImplicitExit(false)`, show a real `Stage` through the same Spring-managed stage configurer as production, and drive button actions on the FX thread via a reusable harness.
- Keep Windows smoke guidance in-repo with a "current baseline" section for already shipped shell behavior plus separate native Windows and WSL scenario sections for future workflows; that preserves one canonical checklist across incremental delivery.

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

## 2026-03-15 - US-003
- Replaced the sample hello launch path with a Spring-backed Ralphy shell by introducing `RalphyApplication`, `AppShellStageConfigurer`, `AppShellController`, and `app-shell-view.fxml`.
- The main window now exposes placeholder regions for navigation, workspace content, and status, and both the stage title and in-app branding now read `Ralphy`.
- Added `AppShellDescriptor` for shell copy/title, updated the launcher and FXML loader to target the new shell resources, and removed the old hello sample classes and FXML.
- Added `AppShellUiTest` to load the shell scene on the JavaFX thread and assert the branded navigation, workspace, and status regions; updated `JavaFxSpringBridgeTest` to validate controller injection against the new shell descriptor bean.
- Completed Windows smoke verification by launching `.\mvnw.cmd -q -DskipTests javafx:run`, confirming a top-level window titled `Ralphy`, and shutting the app down cleanly.
- Files changed: `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellDescriptor.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellStageConfigurer.java`, `src/main/java/net/uberfoo/ai/ralphy/Launcher.java`, `src/main/java/net/uberfoo/ai/ralphy/RalphyApplication.java`, `src/main/java/net/uberfoo/ai/ralphy/SpringFxmlLoader.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `src/test/java/net/uberfoo/ai/ralphy/JavaFxSpringBridgeTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - A Spring-managed stage configurer is a clean seam between JavaFX startup and UI smoke tests because it lets tests build the primary scene graph without invoking the one-shot `Application.launch(...)` lifecycle.
  - Gotchas encountered
    - CSS selector lookups in JavaFX tests are more reliable when important nodes have an explicit `id`; relying on `fx:id` alone is a weak test contract.
    - A practical Windows smoke check for JavaFX in this environment is to launch `javafx:run` and poll the spawned `java` or `javaw` process for the expected `MainWindowTitle`.
---

## 2026-03-15 - US-004
- Implemented a shared JavaFX dark theme with reusable surface and text tokens in `app-theme.css`, and attached it once at scene creation through the new `AppTheme` utility.
- Updated the shell FXML to consume shared theme classes instead of inline per-node colors so the navigation rail, workspace, cards, buttons, and status bar all render with the dark palette by default.
- Expanded the JavaFX shell UI test to assert the shared stylesheet is applied and that key shell regions resolve to the expected dark background and text colors.
- Completed Windows smoke verification by launching `.\mvnw.cmd -q -DskipTests javafx:run`, capturing the live `Ralphy` window, and visually confirming the dark shell palette.
- Files changed: `src/main/java/net/uberfoo/ai/ralphy/AppTheme.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellStageConfigurer.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - A scene-level stylesheet plus a root theme class is enough for new JavaFX shell views to inherit default dark text and surface styling without loading CSS per FXML file.
  - Gotchas encountered
    - For extra JavaFX style classes in FXML, nested `<styleClass>` entries are more reliable than a space-delimited `styleClass` attribute when you need to preserve default control styling and have descendant CSS selectors resolve consistently.
    - FX-thread test helpers should catch `Throwable`, not just `Exception`, or failed assertions inside `Platform.runLater(...)` show up as misleading timeouts.
---

## 2026-03-15 - US-005
- Added a reusable JavaFX UI harness that starts the toolkit once, boots the Spring-backed shell into a real `Stage`, and exposes deterministic node lookup, style inspection, and button interaction helpers for future shell smoke tests.
- Reworked `AppShellUiTest` into a launch-level smoke test that verifies the visible primary shell, confirms shared theme application, and drives top-level navigation buttons to assert workspace/status updates and active navigation state.
- Enabled shell navigation interactions in the app by wiring button ids and action handlers in FXML, updating the controller to swap workspace/status placeholder content, and styling the active navigation button.
- Verified `.\mvnw.cmd clean verify jacoco:report` passes and completed the Windows smoke check by launching `.\mvnw.cmd -q -DskipTests javafx:run` and detecting the live `Ralphy` top-level window before shutdown.
- Files changed: `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - A reusable `Stage` harness gives JavaFX UI tests real launch coverage without depending on the one-shot `Application.launch(...)` lifecycle inside JUnit.
  - Gotchas encountered
    - Closing the last JavaFX window during tests will tear down the toolkit unless `Platform.setImplicitExit(false)` is set before the harness starts opening and closing stages.
    - Stable `id` attributes on the shell root, workspace title, and navigation buttons are necessary for reliable smoke assertions once the test moves beyond simple scene construction.
---

## 2026-03-15 - US-006
- Added `docs/windows-smoke-checklist.md` as the repository-owned manual smoke artifact for Windows development, covering onboarding, PRD editing, loop start, pause, and log viewing across native Windows and WSL execution-profile scenarios.
- Anchored the checklist to the current shipped shell with a baseline launch/navigation smoke so the document is usable now while still defining the future native and WSL workflow expectations in one canonical place.
- Verified `.\mvnw.cmd clean verify jacoco:report` passes, confirmed the JavaFX UI tests pass in that run, and completed a Windows launch smoke by starting `.\mvnw.cmd -q -DskipTests javafx:run` and detecting the live `Ralphy` top-level window before shutdown.
- Files changed: `docs/windows-smoke-checklist.md`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - Smoke checklists for incremental desktop delivery work best when they preserve future workflow coverage but explicitly call out the currently shippable baseline and `N/A` handling for not-yet-landed stories.
  - Gotchas encountered
    - `wsl.exe -l -v` returned `E_ACCESSDENIED` in this sandbox, so WSL availability could not be enumerated here even though the repository checklist still documents the required WSL verification path.
---
