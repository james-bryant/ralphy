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
- For JavaFX flows that use native directory choosers in production, wrap the chooser in a Spring-managed adapter with queued test selections so UI harnesses can cover browse workflows without opening OS dialogs.
- Validate local Git repositories by checking for `.git` metadata existence, not just a `.git` directory, because worktrees expose `.git` as a file.
- When a JavaFX workflow must create a new filesystem folder, pair a text field for the new leaf directory name with a chooser for the existing parent folder; JavaFX `DirectoryChooser` cannot target a path that does not exist yet.
- Keep app-level desktop metadata in a versioned local store outside the repository, and persist updates by writing a temporary JSON file and atomically moving it into place so project/session records survive restarts without partial writes.
- For repo-local PRD and run-artifact storage, compute the `.ralph-tui` layout from the active repository and re-run an idempotent directory bootstrap on every project activation so missing storage folders self-heal before metadata is persisted.
- Restore startup project context by resolving the most recent session-backed project from local metadata and reactivating it through the same service path used for manual open/create flows; that keeps repository validation, storage bootstrapping, and metadata refresh aligned across cold start and interactive onboarding.

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

## 2026-03-15 - US-007
- Implemented an in-memory active-project flow for opening existing local repositories, including a Spring-managed directory chooser, `.git`-based repository validation, and shell UI updates that show the active repository name/path and inline validation failures.
- Expanded the Projects workspace card and status bar to expose `Open Existing Repository`, reject non-Git folders with a clear message, and keep the previously active repository when validation fails.
- Added automated coverage for repository selection through both a service-level validation test and a JavaFX UI test that drives the browse workflow through the reusable chooser seam.
- Updated the Windows smoke checklist baseline to include opening a valid repository and confirming rejection of a non-Git folder.
- Verified `.\mvnw.cmd clean verify jacoco:report` passes, confirmed the JavaFX UI tests pass in that run, and completed a Windows launch smoke by starting `.\mvnw.cmd -q -DskipTests javafx:run` and detecting the live `Ralphy` top-level window before shutdown.
- Files changed: `src/main/java/net/uberfoo/ai/ralphy/ActiveProject.java`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`, `src/main/java/net/uberfoo/ai/ralphy/RepositoryDirectoryChooser.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`, `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `docs/windows-smoke-checklist.md`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - Native chooser workflows stay testable when the chooser itself is abstracted behind a Spring bean that the JavaFX harness can preload with deterministic selections.
    - Showing active project state in a shell-global surface such as the status bar avoids tying project awareness to one workspace tab.
  - Gotchas encountered
    - Git worktrees expose `.git` as a file, so repository validation must treat either a file or directory marker as valid metadata.
    - JavaFX validation labels should bind `managedProperty()` to `visibleProperty()` or hidden error states still reserve layout space and leave awkward gaps in the card.
---

## 2026-03-15 - US-008
- Implemented new-project onboarding in the `Projects` workspace, including a typed folder-name field, parent-folder selection, local folder creation, `git init`, and automatic activation of the new repository as the active project.
- Added initial project metadata bootstrap at `.ralph-tui/project-metadata.json` inside each newly created repository, and rolled back partially created folders when Git initialization or metadata creation fails.
- Expanded automated coverage with service tests for successful creation and rollback-on-failure, plus a JavaFX UI test that validates the create flow end to end through the shell.
- Updated the Windows smoke checklist baseline to include creating a new repository and verifying the metadata artifact, then completed a Windows launch smoke on 2026-03-15 by launching `.\mvnw.cmd -q -DskipTests javafx:run` and detecting the `Ralphy` window.
- Files changed: `src/main/java/net/uberfoo/ai/ralphy/ActiveProject.java`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`, `src/main/java/net/uberfoo/ai/ralphy/GitRepositoryInitializer.java`, `src/main/java/net/uberfoo/ai/ralphy/ProjectMetadataInitializer.java`, `src/main/java/net/uberfoo/ai/ralphy/RepositoryDirectoryChooser.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`, `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`, `docs/windows-smoke-checklist.md`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - New repository onboarding is easiest to keep testable when folder creation, Git initialization, and metadata bootstrap are separate seams orchestrated by the active-project service.
  - Gotchas encountered
    - JavaFX `DirectoryChooser` can only select folders that already exist, so the create flow must choose a parent folder and collect the new repository name separately.
    - Repository creation needs best-effort rollback or failed `git init` and metadata writes leave behind half-created project folders that the next attempt collides with.
---

## 2026-03-15 - US-009
- Implemented a versioned local metadata store that persists projects, sessions, default execution-profile records, and run-metadata buckets to `metadata-store.json` outside the repository so records survive app restarts.
- Wired active-project open/create flows to upsert project records and session state into the store, while keeping repo-local `.ralph-tui/project-metadata.json` bootstrap behavior from US-008.
- Added restart-readability coverage for the metadata store plus service-level assertions that project creation writes the expected persisted records, and isolated Spring/JavaFX UI tests to temp storage directories.
- Verified `.\mvnw.cmd clean verify jacoco:report` passes on 2026-03-15.
- Files changed: `.gitignore`, `pom.xml`, `src/main/java/module-info.java`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`, `src/main/java/net/uberfoo/ai/ralphy/LocalMetadataStorage.java`, `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`, `src/test/java/net/uberfoo/ai/ralphy/LocalMetadataStorageTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - A single versioned app-level JSON store is enough to cover desktop metadata/state early, as long as project flows upsert their records consistently and writes are atomic.
    - JavaFX Spring harnesses should take app args for storage-root overrides so file-backed services stay test-isolated without special production code paths.
  - Gotchas encountered
    - A `@Component` with both a runtime constructor and a test-only construction path needs explicit constructor selection or Spring falls back to default instantiation and fails bean creation.
    - Letting Spring infer a singleton bean destroy method from a public `close()` method can introduce shutdown-only defects; keeping explicit test-only session finalization avoids noisy lifecycle warnings until shutdown persistence is a real requirement.
---

## 2026-03-15 - US-010
- Implemented a managed `.ralph-tui` project storage layout with `prds`, `prd-json`, `prompts`, `logs`, and `artifacts` directories that are created for both newly created and newly opened repositories.
- Upgraded repo-local `project-metadata.json` into a versioned storage manifest and extended the app-level metadata store so project and session records persist the relevant storage paths.
- Added regression coverage for directory recreation, metadata-path persistence, and migration of v1 app metadata into the new storage-path-aware schema; updated the Windows smoke checklist to check the new on-disk layout.
- Verified `.\mvnw.cmd clean verify jacoco:report` passes on 2026-03-15.
- Files changed: `docs/windows-smoke-checklist.md`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProject.java`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`, `src/main/java/net/uberfoo/ai/ralphy/LocalMetadataStorage.java`, `src/main/java/net/uberfoo/ai/ralphy/ProjectMetadataInitializer.java`, `src/main/java/net/uberfoo/ai/ralphy/ProjectStorageInitializer.java`, `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `src/test/java/net/uberfoo/ai/ralphy/LocalMetadataStorageTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - Recomputing repository-local storage paths from `ActiveProject` avoids duplicating path rules across services and keeps both metadata documents and directory bootstrap logic aligned.
    - Backward-compatible metadata upgrades are straightforward when new path fields can be deterministically derived from persisted repository paths during normalization.
  - Gotchas encountered
    - Legacy JSON fixtures that embed Windows paths must escape backslashes or Jackson treats sequences like `\U` as invalid escapes.
    - Jackson's pretty-printed output is stable for field names but not for exact spacing, so assertions against persisted JSON should key off semantic substrings rather than one exact formatting layout.
---

## 2026-03-15 - US-011
- Implemented startup restoration of the last active repository by reading the latest session-backed project from local metadata, validating the repository still exists, and reactivating it through the existing project activation flow so `.ralph-tui` storage self-heals on launch.
- Added persisted run-state lookup and shell messaging so the Execution Overview card now shows resumable or reviewable state for the active project, while missing or moved repositories surface a clear recovery message in the project card.
- Expanded regression coverage with service tests for restore success, missing-repository recovery, and resumable/reviewable run classification plus JavaFX UI tests for restored startup state and missing-repository messaging; updated the Windows smoke checklist and completed a Windows smoke on 2026-03-15 by seeding the default metadata store, launching `.\mvnw.cmd -q -DskipTests javafx:run`, and confirming via UI Automation that the live app restored the last project, showed the resumable run message, and displayed the recovery message after the repo lost its `.git` marker.
- Files changed: `docs/windows-smoke-checklist.md`, `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`, `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`, `src/main/java/net/uberfoo/ai/ralphy/LocalMetadataStorage.java`, `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`, `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`, `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`, `.ralph-tui/progress.md`
- **Learnings:**
  - Patterns discovered
    - Restoring startup project state through the same activation path as interactive repository selection avoids a second code path for metadata refresh and `.ralph-tui` directory bootstrap.
  - Gotchas encountered
    - The JavaFX Maven plugin launch path did not honor the attempted custom storage arg in this environment, so the Windows smoke had to seed the app's default local metadata location to exercise the real startup restore flow.
    - JavaFX labels in the live desktop window were readable through Windows UI Automation, which made it possible to verify restore and recovery copy during manual smoke without adding extra debug surfaces to the app.
---
