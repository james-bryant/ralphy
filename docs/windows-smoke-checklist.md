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
26. In the `PRD Interview Engine`, confirm the first question starts on `Product Context`, the sequence includes goals, quality gates, user stories, and scope boundary prompts, and the draft state message references `.ralph-tui/project-metadata.json`.
27. Enter draft answers for at least the first two questions, move forward with `Next`, revisit the first question from the question list, and confirm the earlier answer is restored.
28. Close and relaunch the app, confirm the last active repository is restored, and verify the PRD interview answers and current question are restored with the saved draft summary.
29. Choose `Generate PRD` and confirm the active PRD path points to `.ralph-tui/prds/active-prd.md`, the Markdown editor becomes editable, it shows `Overview`, `Goals`, `Quality Gates`, `User Stories`, and `Scope Boundaries`, and the file is written inside the active repository.
30. Edit the generated Markdown directly, confirm the PRD state message reports unsaved changes, choose `Save PRD`, and verify the saved file reflects the manual edits without collapsing the document structure.
31. Choose `Import PRD`, select an existing external Markdown PRD, and confirm the editor loads that content into `.ralph-tui/prds/active-prd.md` while keeping the editor editable.
32. Choose `Export PRD`, select an external destination, and confirm the chosen file is written with the active Markdown plus `.ralph-tui/project-metadata.json` records the imported and exported file paths.
33. Choose `Import prd.json`, select a compatible external tracker file, and confirm `.ralph-tui/prd-json/prd.json` is rewritten with the reconciled task state, completed task status/history are preserved for stable story IDs, and any Markdown-versus-JSON drift appears clearly in the PRD state message without overwriting the Markdown editor text.
34. Update the `User Stories` interview answer, choose `Generate PRD` again, and confirm the editor plus saved `active-prd.md` reflect the latest draft answers.
35. Close and relaunch the app, reopen `PRD Editor`, and confirm the last saved Markdown edits are restored in the editable surface.
36. Open `Execution` and confirm the workspace title becomes `Execution` and the status bar reads `Execution workspace ready.`
37. In `Story Progress Dashboard`, confirm the card shows `Current Story`, `Overall Counts`, and visible count cells for `Pending`, `Blocked`, `Running`, `Passed`, `Failed`, and `Paused` even when some counts are zero.
38. Seed or restore persisted story state and confirm the dashboard current-story text and counts reflect the saved task status, including a paused/resumable story after restart when run metadata is still `RUNNING`.
39. In `PRD Validation`, confirm the app reports either `PRD ready for execution` for a structurally valid `active-prd.md` or `PRD validation failed` with section/story-specific errors when the saved PRD is malformed.
40. Close the app cleanly.

## Scenario A: Native Windows Execution Profile

Use this scenario now for profile selection, native preflight, and save verification. Keep the run-control steps as future smoke coverage until the execution stories land.

| Area | Action | Expected Result |
| --- | --- | --- |
| Onboarding | Open or create a Git-backed project from the `Projects` workspace. Select the native Windows profile. | The project becomes active, the repository path is shown with a Windows path, new projects gain initial `.ralph-tui/project-metadata.json`, and native preflight reports readiness or clear remediation. |
| PRD Editing | Open `PRD Editor`, switch between the built-in presets, answer several prompts in the PRD interview flow, revisit an earlier answer, generate the active PRD, edit the Markdown directly, save it, import another Markdown PRD, export the active PRD, import a compatible `prd.json`, then update the stories answer and regenerate it. | Preset selection updates the preview without enabling prompt editing, interview answers persist per project and can be revisited before generation, the generated or imported Markdown is saved to `.ralph-tui/prds/active-prd.md`, manual Markdown edits show dirty-state/save behavior without destructive reformatting, import/export paths are tracked in `.ralph-tui/project-metadata.json`, compatible `prd.json` imports reconcile tracker status/history into `.ralph-tui/prd-json/prd.json` while clearly surfacing Markdown-vs-JSON drift, regeneration refreshes the saved PRD from the latest interview draft when requested, and malformed saved PRDs are reflected as blocking errors in `Execution > PRD Validation`. |
| Loop Start | Start one eligible story from `Execution`. | The story moves into a queued or running state, the `Story Progress Dashboard` updates the current story plus pending/running/passed counts, the `Run Output` card begins streaming live output in `Raw Output`, and the launcher uses the native Windows profile. |
| Pause | Request `Pause` while a story is running. | The current step is allowed to finish, the UI distinguishes pause requested from fully paused, and the next story does not start. |
| Log Viewing | Open the latest run artifacts after the story stops and switch between `Assistant Summary` and `Raw Output`. | The final assistant summary remains visible separately from the raw logs, the toggle switches views without reopening files, and the same artifacts remain viewable on disk after restart. |

## Scenario B: WSL Execution Profile

Use this scenario now for WSL profile selection, path-mapping validation, and WSL preflight verification. The desktop app still launches on Windows; only the Codex execution profile changes, and later stories will add the WSL run controls.

| Area | Action | Expected Result |
| --- | --- | --- |
| Onboarding | Open or create a Git-backed project from the `Projects` workspace. Select the WSL profile, choose a distro, save any Windows-to-WSL path mapping, and review the WSL preflight section. | The project becomes active, the selected distro is shown, new projects gain initial `.ralph-tui/project-metadata.json`, path mapping resolves correctly, and WSL preflight reports readiness or actionable remediation. |
| PRD Editing | Open `PRD Editor`, review the built-in preset catalog, answer several prompts in the PRD interview flow, revisit an earlier answer, generate the active PRD, edit and save the Markdown directly, import another Markdown PRD, export the active PRD, import a compatible `prd.json`, then update the stories answer and regenerate it. | Preset previews remain read-only, PRD interview drafts persist per project and restore after restart, generated or imported Markdown authoring behavior matches the native Windows flow because PRD generation still happens in the Windows desktop UI, including dirty-state/save behavior for manual Markdown edits, import/export path tracking in `.ralph-tui/project-metadata.json`, compatible `prd.json` reconciliation into `.ralph-tui/prd-json/prd.json` with clear Markdown-vs-JSON drift messaging, and malformed saved PRDs are reflected as blocking errors in `Execution > PRD Validation`. |
| Loop Start | Start one eligible story from `Execution` while the WSL profile is selected. | The story moves into a queued or running state, the `Story Progress Dashboard` updates the current story plus pending/running/passed counts, the `Run Output` card begins streaming live output in `Raw Output`, and the launcher runs through the saved WSL profile and mapped repository path. |
| Pause | Request `Pause` while a story is running through WSL. | The current step is allowed to finish inside WSL, the UI reaches a paused state, and the next story does not start. |
| Log Viewing | Open the latest run artifacts after the story stops and switch between `Assistant Summary` and `Raw Output`. | The final assistant summary remains visible separately from the raw logs, distro/profile details remain available, and the same artifacts remain viewable on disk after restart. |

## Manual Smoke Record

Capture each run in the PR or progress log using this minimum data:

- Date
- Windows version
- App build or commit under test
- Whether the native Windows scenario passed
- Whether the WSL scenario passed
- Notes for failures, skipped steps, or `N/A` items
