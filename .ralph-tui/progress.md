# Ralph Progress Log

This file tracks progress across iterations. Agents update this file
after each iteration and it's included in prompts for context.

## Codebase Patterns (Study These First)

*Add reusable patterns discovered during development here.*

- Persist execution diagnostics in `.ralph-tui/project-metadata.json` and restore them through `ActiveProjectService`; when a stored report already exists, render that report on reopen and offer an explicit rerun action instead of immediately overwriting it during startup.

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
