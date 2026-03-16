# Ralph Progress Log

This file tracks progress across iterations. Agents update this file
after each iteration and it's included in prompts for context.

## Codebase Patterns (Study These First)

*Add reusable patterns discovered during development here.*

- When round-tripping repository files through JavaFX `TextArea` controls, keep the source document's line-ending style as separate state and restore it on save; JavaFX normalizes line breaks in-memory, so raw save logic alone will silently rewrite CRLF files.
- Persist execution diagnostics in `.ralph-tui/project-metadata.json` and restore them through `ActiveProjectService`; when a stored report already exists, render that report on reopen and offer an explicit rerun action instead of immediately overwriting it during startup.
- Put remediation commands on each failed preflight check record and keep the UI passive: render those commands in a dedicated remediation panel with copy buttons and rerun actions, but never execute install or authentication commands from the app itself.
- Keep built-in workflow presets as typed catalog records in code and render previews directly from that catalog; until customization exists, avoid persisting or editing raw prompt bodies in project metadata.
- Persist in-progress PRD authoring in `project-metadata.json` as typed question/answer draft state plus the selected step index, so JavaFX authoring screens can restore users to the same interview prompt after restart without inventing a second persistence path.
- Treat generated PRDs as project-scoped filesystem artifacts: save and restore `.ralph-tui/prds/active-prd.md` through `ActiveProjectService` and render the current file in the UI instead of duplicating Markdown content inside project metadata.
- Model execution prerequisites as typed service-level reports and let JavaFX render those reports directly; keeping PRD validation and future launch gating in `ActiveProjectService` avoids duplicating parser rules in the controller.
- Track external PRD exchange paths in `.ralph-tui/project-metadata.json` with a typed metadata record and feed those paths back into JavaFX file choosers, so import/export workflows restore their last-used locations without inventing app-global state.

---

## 2026-03-15 - US-014
- Implemented WSL preflight presentation in the desktop shell, including stored-report rendering, a manual `Run WSL Preflight` action, and WSL-specific summary/detail/check formatting alongside the existing native diagnostics.
- Fixed Spring construction for `ActiveProjectService` by marking the primary constructor for autowiring, which restored application startup after the WSL service constructor was added.
- Kept saved WSL preflight results stable across reopen by only auto-running WSL preflight when no stored WSL report exists yet, while preserving auto-run on WSL profile save.
- Updated the Windows smoke checklist text so the documented WSL flow matches the implemented diagnostics behavior.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - The project already stores independent `nativeWindowsPreflight` and `wslPreflight` payloads in project metadata; UI stories should usually wire into that persisted state rather than create new storage paths.
  - WSL preflight autoruns are more sensitive than native startup checks because rerunning them on restore can replace the last actionable report with machine-specific startup noise, so restore should prefer stored state plus a manual rerun affordance.
---
## 2026-03-15 - US-015
- Added remediation commands to native and WSL preflight check records, persisted them in project metadata schema v5, and generated passive guidance for Codex install/auth, Git recovery, quality-gate recovery, WSL distro setup, and WSL path mapping failures.
- Rendered dedicated native and WSL remediation panels in the JavaFX shell with copyable command fields, copy buttons, and rerun-preflight actions available directly inside each remediation view.
- Expanded automated coverage to verify remediation command generation, confirm preflight checks do not invoke install/login commands automatically, validate remediation-panel rendering in the UI, and verify native reruns from the remediation panel.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/PreflightRemediationCommand.java`
  - `src/main/java/net/uberfoo/ai/ralphy/NativeWindowsPreflightReport.java`
  - `src/main/java/net/uberfoo/ai/ralphy/WslPreflightReport.java`
  - `src/main/java/net/uberfoo/ai/ralphy/NativeWindowsPreflightService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/WslPreflightService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ProjectMetadataInitializer.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`
  - `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`
  - `src/test/java/net/uberfoo/ai/ralphy/NativeWindowsPreflightServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/WslPreflightServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
- **Learnings:**
  - Persisting remediation commands alongside check results keeps restored diagnostics and freshly rerun diagnostics consistent without adding UI-only remediation heuristics.
  - Native preflight still auto-runs on restore, so remediation rerun tests need to compare the live detail text before and after the rerun instead of assuming a seeded native report survives startup.
  - Copyable command fields plus explicit copy buttons are a safer fit for this shell than action buttons that would directly launch setup commands, because the acceptance criteria require passive guidance only.
---
## 2026-03-15 - US-016
- Implemented a typed built-in preset catalog with versioned presets for PRD creation, story implementation, retry/fix, and run summary, including metadata for required skills and operating assumptions.
- Replaced the placeholder editor surface with a read-only JavaFX preset catalog that lets users switch between workflow presets and preview the selected prompt without arbitrary editing.
- Added catalog unit coverage, JavaFX UI assertions for preset switching and read-only previews, and updated the Windows smoke checklist to include preset-catalog verification steps.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/PresetUseCase.java`
  - `src/main/java/net/uberfoo/ai/ralphy/BuiltInPreset.java`
  - `src/main/java/net/uberfoo/ai/ralphy/PresetCatalogService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`
  - `src/test/java/net/uberfoo/ai/ralphy/PresetCatalogServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - A typed preset catalog keeps prompt text, versioning, and metadata consistent across UI and later execution features without embedding large prompt literals directly in controllers or FXML.
  - Extending the shared JavaFX harness with small capability checks like `isEditable` is enough to cover read-only UI requirements without introducing heavier UI testing frameworks.
  - For this shell, swapping the placeholder workspace card for a catalog surface was the least disruptive way to land new PRD-authoring functionality while preserving the existing navigation and controller structure.
---
## 2026-03-15 - US-017
- Implemented a typed PRD interview engine with sequenced prompts for overview, goals, quality gates, user stories, and scope boundaries, plus per-project draft persistence in `.ralph-tui/project-metadata.json`.
- Added JavaFX PRD interview UI for question sequencing, direct revisit of earlier prompts, draft save/status messaging, and restart restoration while keeping the built-in preset catalog intact.
- Added automated coverage for interview catalog completeness, draft persistence through `ActiveProjectService`, and JavaFX end-to-end draft capture/revisit/restore behavior. Updated the Windows smoke checklist with PRD interview verification steps.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/PrdInterviewQuestion.java`
  - `src/main/java/net/uberfoo/ai/ralphy/PrdInterviewDraft.java`
  - `src/main/java/net/uberfoo/ai/ralphy/PrdInterviewService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ProjectMetadataInitializer.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`
  - `src/test/java/net/uberfoo/ai/ralphy/PrdInterviewServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - Reusing `ProjectMetadataInitializer` for PRD interview drafts keeps authoring state aligned with other project-scoped artifacts and avoids splitting restart behavior between project metadata and session metadata.
  - A small dynamic button list inside the shared JavaFX shell is enough to support sequenced interview navigation and revisit behavior without introducing a separate navigation framework or additional controllers.
  - Storing the selected interview question index alongside answers makes restart restoration materially better because users return to the exact clarification step they were editing rather than only recovering raw text blobs.
---
## 2026-03-15 - US-018
- Implemented `PrdMarkdownGenerator` to turn PRD interview answers into repository-owned Markdown with ordered `US-XXX` story headers, required PRD sections, and placeholder scope text when later prompts are still blank.
- Added active PRD persistence and restore behavior in `ActiveProjectService`, saving generated Markdown to `.ralph-tui/prds/active-prd.md` and reloading the file when the active project is reopened.
- Extended the JavaFX PRD Editor with a `Generate PRD` action, active PRD path display, and read-only Markdown preview. Added unit and UI coverage for generation, regeneration, and saved-file restoration. Updated the Windows smoke checklist to cover PRD generation and regeneration.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/PrdMarkdownGenerator.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-theme.css`
  - `src/test/java/net/uberfoo/ai/ralphy/PrdMarkdownGeneratorTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - Generated Markdown belongs with the other repository-owned artifacts under `.ralph-tui/`; restoring it from disk on project activation keeps preview state accurate without creating a second PRD persistence model.
  - Saving the currently edited interview answer before generation is necessary so regeneration always reflects the latest draft, not only the last explicitly saved question transition.
  - JavaFX text controls can normalize line endings differently from the filesystem on Windows, so UI assertions should compare normalized content when verifying saved Markdown previews.
---
## 2026-03-15 - US-019
- Replaced the read-only PRD preview with an editable Markdown surface that opens the active `active-prd.md` after generation or project restore, adds an explicit `Save PRD` action, and shows dirty-state messaging while users refine the document.
- Preserved user-authored Markdown without destructive reformatting by tracking each loaded document's original line-ending style in the controller and restoring that style on save instead of regenerating Markdown from structured data.
- Added JavaFX coverage for editable-after-generation behavior plus imported-PRD edit/save/reopen round-tripping, and updated the Windows smoke checklist to include dirty-state/save verification for manual Markdown edits.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/test/java/net/uberfoo/ai/ralphy/JavaFxUiHarness.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - JavaFX `TextArea` is a workable Markdown editor for this shell as long as dirty tracking compares against a normalized in-memory baseline rather than the raw file bytes.
  - Blocking PRD regeneration while the editor is dirty avoids silently discarding unsaved manual refinements, which keeps the generated-flow overwrite behavior explicit instead of surprising.
---
## 2026-03-15 - US-020
- Implemented `PrdStructureValidator` plus a shared `ActiveProjectService` execution gate that validates saved PRDs for required sections, a `Quality Gates` section, and `### US-XXX: ...` story headers before execution.
- Added an `Execution > PRD Validation` panel that surfaces blocked vs ready state and shows section/story-specific validation errors directly from the shared validation report.
- Added unit and JavaFX UI coverage for invalid-to-valid PRD transitions, updated the Windows smoke checklist for PRD validation, and completed a Windows smoke launch by starting `.\mvnw.cmd -q -DskipTests javafx:run` and confirming a live `Ralphy` window.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/PrdValidationError.java`
  - `src/main/java/net/uberfoo/ai/ralphy/PrdValidationReport.java`
  - `src/main/java/net/uberfoo/ai/ralphy/PrdStructureValidator.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/test/java/net/uberfoo/ai/ralphy/PrdStructureValidatorTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - A dedicated structural validator is easier to reuse when it returns typed location/message pairs instead of preformatted UI text, because both service gates and JavaFX views can consume the same report.
  - The existing execution workspace can absorb new prerequisites cleanly by adding a focused validation panel, which preserves the separate meaning of run-recovery state versus start-blocking validation state.
---
## 2026-03-15 - US-021
- Implemented Markdown PRD import/export through a new JavaFX file chooser flow, including `Import PRD` and `Export PRD` actions in the PRD editor plus dirty-state protection before import and automatic save-before-export behavior.
- Added typed project metadata tracking for the last imported and exported Markdown PRD paths, restored that state through `ActiveProjectService`, and reused it to seed the import/export chooser locations after reopen.
- Added automated service and JavaFX UI coverage for Markdown PRD import/export, updated the Windows smoke checklist with import/export verification steps, ran `.\mvnw.cmd clean verify jacoco:report`, and completed a Windows smoke launch by starting `.\mvnw.cmd -q -DskipTests javafx:run` and confirming a live `Ralphy` window with visible `Import PRD` and `Export PRD` controls.
- Files changed:
  - `src/main/java/net/uberfoo/ai/ralphy/MarkdownPrdExchangeLocations.java`
  - `src/main/java/net/uberfoo/ai/ralphy/MarkdownPrdFileChooser.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ProjectMetadataInitializer.java`
  - `src/main/java/net/uberfoo/ai/ralphy/ActiveProjectService.java`
  - `src/main/java/net/uberfoo/ai/ralphy/AppShellController.java`
  - `src/main/resources/net/uberfoo/ai/ralphy/app-shell-view.fxml`
  - `src/test/java/net/uberfoo/ai/ralphy/ActiveProjectServiceTest.java`
  - `src/test/java/net/uberfoo/ai/ralphy/AppShellUiTest.java`
  - `docs/windows-smoke-checklist.md`
- **Learnings:**
  - Keeping import/export file locations in project metadata makes chooser defaults restart-safe and repository-scoped, which fits this shell better than storing them in app-session metadata.
  - Import flows need their own dirty-state guard even when save-on-navigation already exists, because importing a new external PRD is an explicit overwrite path rather than a passive view change.
  - Export should persist pending editor edits before writing the external file so the shared PRD and the exported copy cannot silently diverge from each other.
---
