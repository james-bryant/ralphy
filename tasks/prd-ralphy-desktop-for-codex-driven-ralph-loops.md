# PRD: Ralphy Desktop for Codex-Driven Ralph Loops

## Overview
Build a single-user, Windows-first JavaFX desktop application that lets a developer create a PRD, edit it in-app, convert it into executable task state, and run Ralph-style loops against one local Git repository at a time using OpenAI Codex CLI.

This revision intentionally splits the work into smaller Ralph-sized iterations so each story is narrow, testable, and realistic for one focused AI agent session.

## Goals
- Let a user create a structured PRD interactively inside the desktop app.
- Let a user edit the generated PRD before execution.
- Run one-story-at-a-time Ralph loops with Codex CLI only.
- Support Windows execution with a per-project choice of native PowerShell or WSL.
- Show live AI output, current story, current phase, and overall progress during execution.
- Persist project state, run state, logs, and artifacts locally across restarts.
- Export and import Ralph-compatible Markdown PRDs and `prd.json`.

## Quality Gates

These commands must pass for every user story:
- `.\mvnw.cmd clean verify jacoco:report` - Build, tests, verification, and coverage report

For UI stories, also include:
- Automated JavaFX UI tests
- Manual smoke verification on Windows

## User Stories

### Foundation

### US-001: Add Spring Boot Desktop Dependencies
**Description:** As a developer, I want Spring Boot dependencies added to the JavaFX project so that non-UI services can use dependency injection and typed configuration.

**Acceptance Criteria:**
- [ ] `pom.xml` includes Spring Boot dependencies needed for DI, configuration, and logging without adding a web server.
- [ ] `src/main/java/module-info.java` compiles with the added modules.
- [ ] The existing launcher still starts after the dependency change.

### US-002: Bridge JavaFX Startup to Spring Context
**Description:** As a developer, I want the JavaFX launcher to bootstrap a Spring context so that controllers and services can use Spring-managed beans.

**Acceptance Criteria:**
- [ ] The primary application startup creates a Spring context before loading the main UI.
- [ ] JavaFX controllers or view-models can resolve Spring-managed dependencies through a documented integration point.
- [ ] Application shutdown closes the Spring context and background executors cleanly.

### US-003: Replace the Hello Sample with an App Shell
**Description:** As a user, I want the sample hello screen replaced with a real application shell so that the app can host project, editor, and execution workflows.

**Acceptance Criteria:**
- [ ] The primary launch path no longer uses the sample hello screen as the main experience.
- [ ] The main window includes placeholders for navigation, workspace content, and status.
- [ ] Window title and branding reflect Ralphy rather than the sample scaffold.

### US-004: Apply a Dark Theme to the Shell
**Description:** As a user, I want the app shell to use a dark theme so that the desktop experience matches the product direction.

**Acceptance Criteria:**
- [ ] Shared theme styles or tokens exist for core surfaces and text.
- [ ] The main shell renders with a readable dark palette by default.
- [ ] New shell views inherit the dark theme without per-view duplication.

### US-005: Add a JavaFX UI Test Harness
**Description:** As a developer, I want automated UI test support so that major JavaFX regressions are caught early.

**Acceptance Criteria:**
- [ ] Automated UI tests can launch the primary shell.
- [ ] The build fails when the shell smoke UI test fails.
- [ ] The test harness can interact with top-level navigation or main regions.

### US-006: Add a Windows Smoke Checklist
**Description:** As a developer, I want a manual smoke checklist so that native Windows and WSL workflows can be verified consistently.

**Acceptance Criteria:**
- [ ] The repository contains a Windows smoke checklist covering onboarding, PRD editing, loop start, pause, and log viewing.
- [ ] The checklist includes both native Windows and WSL execution-profile scenarios.
- [ ] The checklist is maintained as a repository artifact rather than tribal knowledge.

### Project Setup and Persistence

### US-007: Open an Existing Git Repository
**Description:** As a user, I want to open an existing local Git repository so that I can run Ralphy against a repo I already use.

**Acceptance Criteria:**
- [ ] The app can browse for and select a local Git repository.
- [ ] Non-Git folders are rejected with a clear validation message.
- [ ] The selected repository becomes the active project.

### US-008: Create a New Local Git Repository
**Description:** As a user, I want to create a new local project folder and initialize Git so that I can start a Ralphy project from scratch.

**Acceptance Criteria:**
- [ ] The app can create a new local folder and initialize Git within it.
- [ ] The new repository is registered as the active project after creation.
- [ ] Initial project metadata is created for the new project.

### US-009: Persist Project and Session Metadata
**Description:** As a user, I want project metadata and session state persisted locally so that I can close and reopen the app without losing context.

**Acceptance Criteria:**
- [ ] Embedded local storage tracks projects, sessions, profiles, and run metadata.
- [ ] The storage schema is versioned for future migrations.
- [ ] Persisted records are readable after an app restart.

### US-010: Store PRDs, Logs, and Artifacts on Disk
**Description:** As a user, I want PRDs and run artifacts stored as files so that I can inspect and back them up outside the app.

**Acceptance Criteria:**
- [ ] Each project has directories for PRDs, `prd.json`, prompts, logs, and execution artifacts.
- [ ] Missing directories are recreated automatically when needed.
- [ ] Stored file paths are linked from the persisted project/session metadata.

### US-011: Restore the Last Active Project
**Description:** As a user, I want the last active project restored on startup so that I can continue where I left off.

**Acceptance Criteria:**
- [ ] On startup, the app can reopen the last active project when it still exists.
- [ ] Incomplete run state is shown as resumable or reviewable.
- [ ] Missing or moved repositories are handled with a clear recovery message.

### Codex Setup and Diagnostics

### US-012: Save a Per-Project Execution Profile
**Description:** As a user, I want each project to store its Codex execution mode so that native Windows and WSL runs are configured explicitly.

**Acceptance Criteria:**
- [ ] A project can store either a native PowerShell profile or a WSL profile.
- [ ] WSL profiles include the selected distribution and any required path-mapping metadata.
- [ ] Profiles can be edited and saved from the UI.

### US-013: Run Native Windows Codex Preflight Checks
**Description:** As a user, I want native Windows preflight diagnostics so that I know whether Codex is runnable before starting a loop.

**Acceptance Criteria:**
- [ ] Native preflight checks verify `codex` availability, auth presence, Git readiness, and quality-gate command availability.
- [ ] Failures are categorized and shown before a run can start.
- [ ] The latest native preflight result is stored with the project.

### US-014: Run WSL Codex Preflight Checks
**Description:** As a user, I want WSL preflight diagnostics so that I know whether the selected WSL environment is runnable before starting a loop.

**Acceptance Criteria:**
- [ ] WSL preflight checks verify distro availability and Codex/Git/auth readiness inside WSL.
- [ ] Windows-to-WSL repository path mapping is validated.
- [ ] The latest WSL preflight result is stored with the project.

### US-015: Show Guided Remediation for Failed Preflight
**Description:** As a user, I want guided remediation steps when diagnostics fail so that I can fix setup issues quickly.

**Acceptance Criteria:**
- [ ] The UI shows copyable remediation commands for each failed preflight check.
- [ ] The app does not attempt to install or authenticate Codex automatically.
- [ ] The user can rerun preflight directly from the remediation view.

### PRD Authoring

### US-016: Add a Built-In Preset Catalog
**Description:** As a user, I want built-in Ralph/Codex presets so that I can use the workflow without authoring prompts manually.

**Acceptance Criteria:**
- [ ] Versioned presets exist for PRD creation, story implementation, retry/fix, and run summary.
- [ ] Preset metadata can record required skills or operating assumptions.
- [ ] v1 exposes preset selection and preview only, not arbitrary prompt editing.

### US-017: Build the PRD Interview Engine
**Description:** As a user, I want a guided interview flow for PRD creation so that I can define requirements interactively inside the app.

**Acceptance Criteria:**
- [ ] The app can ask sequenced clarification questions and store answers in draft state.
- [ ] The interview supports overview, goals, quality gates, user stories, and scope boundaries.
- [ ] Draft answers can be revisited before final generation.

### US-018: Generate Markdown PRD from Interview Answers
**Description:** As a user, I want the interview answers turned into a Markdown PRD so that I can review a concrete document before execution.

**Acceptance Criteria:**
- [ ] The generator creates Markdown with required sections and ordered `US-XXX` story IDs.
- [ ] The generated PRD is saved to the active project.
- [ ] The generator can regenerate the PRD from the latest draft answers.

### US-019: Add an In-App Markdown Editor
**Description:** As a user, I want to edit the PRD directly in the app so that I can refine the plan before running loops.

**Acceptance Criteria:**
- [ ] The active PRD opens in an editable Markdown surface after generation or import.
- [ ] The editor supports save and dirty-state tracking.
- [ ] User edits are preserved without destructive reformatting.

### US-020: Validate PRD Structure Before Execution
**Description:** As a user, I want structural validation of the PRD so that malformed task definitions do not reach the execution loop.

**Acceptance Criteria:**
- [ ] Validation checks required sections, story header format, and a Quality Gates section.
- [ ] Validation errors identify the specific section or story that failed.
- [ ] Execution is blocked while validation errors remain.

### US-021: Import and Export Markdown PRDs
**Description:** As a user, I want Markdown PRD import/export so that I can move PRDs between Ralphy and external tools.

**Acceptance Criteria:**
- [ ] The app can import an existing Markdown PRD into the active project.
- [ ] The app can export the active Markdown PRD to a user-selected location.
- [ ] Imported and exported file locations are tracked in project metadata.

### Task Sync and Ralph Interoperability

### US-022: Sync PRD Stories into Internal Task State
**Description:** As a user, I want valid PRD stories converted into internal task records so that the execution engine can track progress reliably.

**Acceptance Criteria:**
- [ ] Valid `US-XXX` stories are parsed into internal task records with stable IDs and statuses.
- [ ] Re-sync preserves status and history when story IDs remain unchanged.
- [ ] Destructive remaps require explicit user confirmation.

### US-023: Export Ralph-Compatible `prd.json`
**Description:** As a user, I want to export `prd.json` so that my PRD can interoperate with Ralph-style trackers.

**Acceptance Criteria:**
- [ ] The app can export the active PRD/task state as compatible `prd.json`.
- [ ] Exported JSON includes the story structure and quality-gate data needed by the tracker.
- [ ] The exported file passes a compatibility validation step.

### US-024: Import `prd.json` and Reconcile It Safely
**Description:** As a user, I want to import compatible `prd.json` so that external tracker changes can be brought back into the app safely.

**Acceptance Criteria:**
- [ ] The app can import compatible `prd.json` into the active project.
- [ ] Conflicts between Markdown and JSON views are surfaced clearly.
- [ ] Completed history is preserved when reconciliation is non-destructive.

### Codex Execution

### US-025: Build a Native and WSL Codex Launcher
**Description:** As a developer, I want one launcher service that can invoke Codex in native Windows or WSL mode so that execution logic stays consistent.

**Acceptance Criteria:**
- [ ] The launcher can build commands for both native PowerShell and WSL profiles.
- [ ] Prompts and preset inputs are passed non-interactively.
- [ ] Exit code and process metadata are captured for each launch.

### US-026: Capture Structured Events and Raw Logs
**Description:** As a user, I want structured Codex events and raw logs captured so that each run can be inspected and audited later.

**Acceptance Criteria:**
- [ ] Structured event output is persisted when available from Codex.
- [ ] Raw stdout, stderr, prompt text, and summary artifacts are stored per story attempt.
- [ ] Stored logs remain viewable after app restart.

### US-027: Execute a Single Story Session
**Description:** As a user, I want to run one story at a time so that each loop step is isolated and recoverable.

**Acceptance Criteria:**
- [ ] The app can start an eligible single story against the active repository.
- [ ] Story state transitions are persisted through queued, running, passed, or failed states.
- [ ] Each attempt records timestamps, preset used, and outcome.

### US-028: Show Story Progress in a Dashboard
**Description:** As a user, I want a dashboard of story states so that I can see what is pending, running, blocked, or done.

**Acceptance Criteria:**
- [ ] The dashboard shows pending, blocked, running, passed, failed, and paused states.
- [ ] The current story and overall counts are visible during execution.
- [ ] Dashboard state can be restored from persisted metadata after restart.

### US-029: Stream Live Output and Display a Final Summary
**Description:** As a user, I want to see live Codex output and the final assistant summary so that I can follow the run without opening raw log files.

**Acceptance Criteria:**
- [ ] The run view streams live output during execution.
- [ ] The final assistant summary is stored and displayed separately from raw logs.
- [ ] The user can switch between summary and raw output views.

### US-030: Pause After the Current Step Completes
**Description:** As a user, I want pause behavior that waits for the current step to finish so that the repo is not interrupted mid-step.

**Acceptance Criteria:**
- [ ] `Pause` prevents the next story from starting after the current step completes.
- [ ] The UI distinguishes between pause requested and fully paused.
- [ ] Pausing does not kill the active process mid-step by default.

### US-031: Auto-Advance Across Ready Stories
**Description:** As a user, I want the loop to continue automatically across ready stories so that I do not need to restart each one manually.

**Acceptance Criteria:**
- [ ] `Play` starts from the next eligible story and continues automatically.
- [ ] Blocked or invalid stories are skipped with a visible reason.
- [ ] Execution stops when paused, failed after retry, or complete.

### US-032: Retry Once and Support Resume
**Description:** As a user, I want failed stories retried once automatically and then left resumable so that transient failures are handled but control returns to me when needed.

**Acceptance Criteria:**
- [ ] The runner retries a failed story once automatically.
- [ ] A second failure stops the run and marks the story failed.
- [ ] The failed story can be resumed later without rebuilding project state.

### Git Automation and History

### US-033: Create or Switch to a Feature Branch
**Description:** As a user, I want the run to execute on a feature branch so that automated changes stay isolated from my main branch.

**Acceptance Criteria:**
- [ ] Starting a run creates or switches to a branch for the active PRD session.
- [ ] Branch naming follows a deterministic convention.
- [ ] The branch action is recorded in run metadata.

### US-034: Commit Each Completed Story
**Description:** As a user, I want each completed story committed automatically so that implementation history is traceable story by story.

**Acceptance Criteria:**
- [ ] A story is marked complete only after validation passes and a commit exists.
- [ ] Commit messages begin with the story ID.
- [ ] The commit hash is stored with the story result.

### US-035: View Run History and Artifacts
**Description:** As a user, I want a run-history view so that I can inspect prior attempts, commits, and stored artifacts from the UI.

**Acceptance Criteria:**
- [ ] The history view lists story attempts with timestamps, branch, commit, preset, and result.
- [ ] Stored prompts, logs, summaries, and exported artifacts can be opened from the UI.
- [ ] Run history persists across restarts and across multiple runs of the same project.

## Functional Requirements
1. FR-1: The system must support single-user local desktop usage for one active Git repository at a time.
2. FR-2: The system must use JavaFX for the desktop UI and Spring Boot for dependency injection, configuration, and service orchestration.
3. FR-3: The system must allow users to open an existing local Git repository or create a new Git-initialized project folder.
4. FR-4: The system must persist project and session metadata locally and store PRDs, logs, prompts, exports, and artifacts on disk.
5. FR-5: The system must provide a per-project execution profile with explicit native Windows or WSL selection.
6. FR-6: The system must run preflight validation before execution, including Codex availability, auth presence, Git readiness, path mapping where applicable, and quality-gate command availability.
7. FR-7: The system must ship built-in Ralph/Codex presets for PRD generation and story execution without a free-form prompt editor in v1.
8. FR-8: The system must support an in-app interview flow to generate a Markdown PRD.
9. FR-9: The system must display the generated or imported PRD in an editable Markdown surface.
10. FR-10: The system must validate PRD structure and block execution when the PRD is invalid.
11. FR-11: The system must sync valid `US-XXX` stories into internal task state with stable identifiers.
12. FR-12: The system must import and export Markdown PRDs and Ralph-compatible `prd.json`.
13. FR-13: The system must execute Codex in non-interactive automation mode and persist run metadata for each story attempt.
14. FR-14: The system must display live output, current story, and overall run progress during execution.
15. FR-15: The system must honor pause by allowing the current step to finish and then preventing the next story from starting.
16. FR-16: The system must auto-advance across ready stories until paused, failed after retry, or complete.
17. FR-17: The system must retry a failed story once automatically and then stop if the retry also fails.
18. FR-18: The system must create or switch to a feature branch before execution and require a commit per completed story.
19. FR-19: The system must retain prompts, logs, summaries, exports, and history for every story execution.
20. FR-20: The system must present a dark theme across primary application surfaces.
21. FR-21: The system must support Codex execution on Windows with native PowerShell preferred when available and WSL as a first-class fallback.

## Non-Goals
- Multi-user collaboration or shared team sessions
- Multiple active repositories or parallel project workspaces in one app session
- Support for non-Codex agents or non-OpenAI providers in v1
- Free-form prompt editing, skill editing, or profile scripting in v1
- Automatic installation or authentication of Codex CLI
- Cloud-hosted orchestration, remote worktrees, or pull request automation
- Direct Beads integration in v1
- Theme customization beyond the required dark theme

## Technical Considerations
- The current scaffold is the JavaFX sample app in `src/main/java/net/uberfoo/ai/ralphy/HelloApplication.java`, `src/main/java/net/uberfoo/ai/ralphy/HelloController.java`, `src/main/resources/net/uberfoo/ai/ralphy/hello-view.fxml`, and `src/main/java/module-info.java`.
- The current Maven setup uses JavaFX 21 and Java 25 compiler settings in `pom.xml`; the first stories should evolve that scaffold rather than replace it.
- Spring Boot should act as a desktop service container, not as a web app. Prefer constructor injection, typed configuration properties, and explicit lifecycle management between JavaFX and Spring.
- A hybrid persistence model is preferred: embedded SQLite or equivalent for metadata/state, filesystem storage for PRDs, `prd.json`, prompts, logs, summaries, and exported artifacts.
- The execution engine should assume Git-backed work by default and avoid non-repository execution except for diagnostics.
- Preset design should align with Ralph-style workflows: small stories, clear quality gates, stable story IDs, persistent task state, and reproducible run artifacts.
- Based on the previously reviewed Codex CLI and Ralph references, native Windows support should be first-class where available, with WSL retained as a first-class fallback because Windows support may still vary by machine and setup.

## Success Metrics
- A user can open or create a repo, generate a PRD, edit it, and start execution without leaving the app.
- Exported `prd.json` files interoperate with Ralph-compatible JSON tracking without manual schema fixes.
- Pause behavior consistently stops execution before the next story begins during Windows smoke tests.
- Every completed story has a linked commit hash, prompt record, and artifact bundle.
- Both native Windows and WSL profiles pass onboarding and execution smoke verification on supported Windows machines.
- Automated UI tests cover the core flows for shell launch, project onboarding, PRD authoring, and run controls.

## Open Questions
- Should the Windows distribution target `jpackage`, MSI, zip, or more than one packaging format in v1?
- Should the project keep Java 25 as the baseline, or align to a more common LTS target such as Java 21?
- Should the app expose a read-only prompt preview in the run screen, or keep prompt visibility limited to stored artifacts and history?