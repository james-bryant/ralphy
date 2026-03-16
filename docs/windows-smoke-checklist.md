# Windows Smoke Checklist

This document is the repository-owned manual smoke checklist for Windows-first Ralphy development. Keep it current as onboarding, PRD authoring, execution, pause, and log-viewing features land. Do not replace this file with tribal knowledge or ad hoc chat notes.

## When to Run

- After UI-affecting stories
- Before handing off a Windows build
- Before merging changes that affect project setup, PRD flows, execution profiles, run controls, or artifact viewing

## Preconditions

- Windows host with a working JDK and Git installation
- Optional WSL distribution installed for the WSL execution-profile scenario
- Repository checked out locally
- From the repository root, run `.\mvnw.cmd clean verify jacoco:report`

## Current Baseline Shell Smoke

Run this baseline even when later workflow stories are still incomplete. For any not-yet-shipped flow below, mark the step `N/A` and leave the checklist entry in place.

1. Launch the desktop shell with `.\mvnw.cmd -q -DskipTests javafx:run`.
2. Confirm a top-level window titled `Ralphy` opens.
3. Confirm the shell renders the expected dark theme and the left navigation shows `Projects`, `PRD Editor`, and `Execution`.
4. Open `Projects` and confirm the workspace title becomes `Projects` and the status bar reads `Projects workspace ready.`
5. Use `Open Existing Repository` to choose a known local Git repository.
6. Confirm the `Active Repository` card shows the selected repository name and full path, the status bar `Active Project` value updates, and `.ralph-tui/project-metadata.json` plus the `prds`, `prd-json`, `prompts`, `logs`, and `artifacts` directories exist inside the repository.
7. In the `Execution Profile` section, confirm the summary defaults to `Native Windows PowerShell` and the `Native PowerShell`, `WSL`, `Save Execution Profile`, and `Run Native Preflight` controls are visible.
8. Confirm the `Native Windows Preflight` section shows either `Ready for native execution` or `Native execution blocked`, includes a last-checked timestamp, and lists categorized check results for `Tooling`, `Authentication`, `Git`, and `Quality Gate`.
9. Choose `Run Native Preflight` and confirm the result refreshes in place without clearing the active project.
10. Select `WSL`, enter a distro plus Windows-to-WSL path prefixes, and choose `Save Execution Profile`.
11. Confirm the summary updates to the saved WSL profile and the `Projects` workspace continues to show the active repository.
12. Select `Native PowerShell`, choose `Save Execution Profile`, and confirm the summary returns to `Native Windows PowerShell`.
13. Use `Open Existing Repository` again and choose a non-Git folder.
14. Confirm a validation message states the selected folder is not a Git repository and the previous active project remains unchanged.
15. In the `Create New Repository` field, enter a new folder name and use `Create New Repository` to choose a parent folder.
16. Confirm a new Git repository folder is created, the `Active Repository` card updates to that new repository, and `.ralph-tui/project-metadata.json` plus the `prds`, `prd-json`, `prompts`, `logs`, and `artifacts` directories exist inside the new folder.
17. Close the app cleanly.
18. Relaunch the desktop shell with `.\mvnw.cmd -q -DskipTests javafx:run`.
19. Confirm the last active repository is restored automatically, the `Active Repository` card shows the same repository from the previous launch, the `Execution Profile` summary restores the last saved native or WSL profile, and the latest native preflight result remains visible.
20. Confirm the `Execution Overview` card shows either `No persisted run state` or a resumable/reviewable message when seeded run metadata exists.
21. Using a disposable test repository, move the active repository or temporarily remove/rename its `.git` marker, relaunch the app, and confirm a clear recovery message explains that the last active repository could not be restored.
22. Open `PRD Editor` and confirm the workspace title becomes `PRD Editor` and the status bar reads `PRD Editor workspace ready.`
23. In the `Built-In Preset Catalog`, confirm the workflow list shows `PRD Creation`, `Story Implementation`, `Retry/Fix`, and `Run Summary`.
24. Confirm the default preview shows the `Ralph/Codex PRD Creation` preset, includes a version identifier, and displays recorded skills and operating assumptions.
25. Select `Story Implementation` and confirm the preview updates in place with the implementation preset details while the prompt preview remains read-only.
26. Open `Execution` and confirm the workspace title becomes `Execution` and the status bar reads `Execution workspace ready.`
27. Close the app cleanly.

## Scenario A: Native Windows Execution Profile

Use this scenario now for profile selection, native preflight, and save verification. Keep the run-control steps as future smoke coverage until the execution stories land.

| Area | Action | Expected Result |
| --- | --- | --- |
| Onboarding | Open or create a Git-backed project from the `Projects` workspace. Select the native Windows profile. | The project becomes active, the repository path is shown with a Windows path, new projects gain initial `.ralph-tui/project-metadata.json`, and native preflight reports readiness or clear remediation. |
| PRD Editing | Open `PRD Editor`, switch between the built-in presets, then open the active PRD, make a small Markdown edit, save, and reopen it. | Preset selection updates the preview without enabling prompt editing, and PRD edits persist without destructive reformatting while dirty-state/save behavior is clear. |
| Loop Start | Start one eligible story from `Execution`. | The story moves into a queued or running state, live output begins, and the launcher uses the native Windows profile. |
| Pause | Request `Pause` while a story is running. | The current step is allowed to finish, the UI distinguishes pause requested from fully paused, and the next story does not start. |
| Log Viewing | Open the latest run artifacts after the story stops. | Raw output, summary, and any linked files remain viewable from the app and on disk after restart. |

## Scenario B: WSL Execution Profile

Use this scenario now for WSL profile selection, path-mapping validation, and WSL preflight verification. The desktop app still launches on Windows; only the Codex execution profile changes, and later stories will add the WSL run controls.

| Area | Action | Expected Result |
| --- | --- | --- |
| Onboarding | Open or create a Git-backed project from the `Projects` workspace. Select the WSL profile, choose a distro, save any Windows-to-WSL path mapping, and review the WSL preflight section. | The project becomes active, the selected distro is shown, new projects gain initial `.ralph-tui/project-metadata.json`, path mapping resolves correctly, and WSL preflight reports readiness or actionable remediation. |
| PRD Editing | Open `PRD Editor`, review the built-in preset catalog, then open the active PRD, make a small Markdown edit, save, and reopen it. | Preset previews remain read-only, and PRD editing behavior matches the native Windows flow because authoring still happens in the Windows desktop UI. |
| Loop Start | Start one eligible story from `Execution` while the WSL profile is selected. | The story moves into a queued or running state, live output begins, and the launcher runs through the saved WSL profile and mapped repository path. |
| Pause | Request `Pause` while a story is running through WSL. | The current step is allowed to finish inside WSL, the UI reaches a paused state, and the next story does not start. |
| Log Viewing | Open the latest run artifacts after the story stops. | Raw output, summary, distro/profile details, and linked files remain viewable from the app and on disk after restart. |

## Manual Smoke Record

Capture each run in the PR or progress log using this minimum data:

- Date
- Windows version
- App build or commit under test
- Whether the native Windows scenario passed
- Whether the WSL scenario passed
- Notes for failures, skipped steps, or `N/A` items
