package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class PresetCatalogService {
    private final List<BuiltInPreset> presets = List.of(
            new BuiltInPreset(
                    "ralph-codex-prd-v1",
                    "Ralph/Codex PRD Creation",
                    "v1",
                    PresetUseCase.PRD_CREATION,
                    "Draft a repository-owned Markdown PRD that can be reviewed, edited, and converted into Ralph tasks.",
                    List.of("ralph-tui-prd"),
                    List.of(
                            "The repository can store authored PRDs and exports under .ralph-tui/.",
                            "The output should stay concise, testable, and organized around independently executable user stories."
                    ),
                    """
                    You are preparing a repository-owned Product Requirements Document for a Ralph loop.
                    Start in planning mode from the user's prompt and current repository context.
                    Ask 3-5 essential clarification questions at a time, adapt follow-up questions based on the answers, and always ask about quality gates.
                    Once there is enough context, produce concise Markdown with canonical markdown headings like `## Overview`, `## Goals`, `## Quality Gates`, `## User Stories`, `## Scope Boundaries`, `## Functional Requirements`, `## Technical Considerations`, `## Success Metrics`, and `## Open Questions`.
                    Under `## User Stories`, format every story as `### US-001: Story Title` and include `**Description:**`, `**Dependencies:**`, and `**Acceptance Criteria:**` with checklist items.
                    Format `## Scope Boundaries` with `### In Scope` and `### Out of Scope` subsections.
                    Every story must be independently executable, include explicit dependencies (`None.` when there are none), and include clear acceptance criteria that can be converted into `prd.json`.
                    Put the important implementation detail in the stories instead of leaving them as thin placeholder titles, and call out assumptions explicitly instead of hiding them inside vague requirements.
                    """
            ),
            new BuiltInPreset(
                    "ralph-codex-implement-v1",
                    "Ralph/Codex Story Implementation",
                    "v1",
                    PresetUseCase.STORY_IMPLEMENTATION,
                    "Implement one approved story in the active repository with targeted changes, tests, and verification.",
                    List.of("springboot-tdd", "springboot-verification"),
                    List.of(
                            "The agent can inspect and edit the active repository locally before proposing changes.",
                            "Use the repository's documented quality gate when available; in Maven-based repos this is often .\\mvnw.cmd clean verify jacoco:report."
                    ),
                    """
                    Implement exactly one approved story in the current repository.
                    Inspect the codebase before editing and keep changes scoped to the requested behavior.
                    Add or update automated coverage for the behavior you are changing, then make the minimum code changes needed.
                    Run the repository quality gate before finishing and report any residual risks or missing manual checks.
                    Do not create git commits unless the user explicitly asks for them.
                    """
            ),
            new BuiltInPreset(
                    "ralph-codex-retry-v1",
                    "Ralph/Codex Retry and Fix",
                    "v1",
                    PresetUseCase.RETRY_FIX,
                    "Recover a failed story attempt by reproducing the failure, fixing the root cause, and rerunning verification.",
                    List.of("springboot-tdd"),
                    List.of(
                            "A previous attempt produced failing tests, logs, or a review finding that can be inspected.",
                            "The retry should stay focused on the active story instead of broad cleanup."
                    ),
                    """
                    Reproduce the current failure from tests, logs, or review notes before changing code.
                    Fix the root cause for the active story attempt with the smallest defensible change set.
                    Add regression coverage for the failure when practical and rerun the failing checks plus the project quality gate.
                    Summarize what failed, how it was fixed, and any remaining concerns that still need human follow-up.
                    """
            ),
            new BuiltInPreset(
                    "ralph-codex-summary-v1",
                    "Ralph/Codex Run Summary",
                    "v1",
                    PresetUseCase.RUN_SUMMARY,
                    "Summarize a completed Ralph run for a human reviewer using stored artifacts and verification outcomes.",
                    List.of(),
                    List.of(
                            "Relevant prompts, logs, and generated artifacts already exist on disk for the completed run.",
                            "The summary should help a human decide whether to accept the run, retry it, or inspect artifacts."
                    ),
                    """
                    Summarize the completed Ralph run for a human reviewer.
                    Include the story identifier, overall outcome, changed files, commands or tests run, and notable artifacts.
                    Call out blockers, manual verification results, and anything that still requires follow-up.
                    Keep the summary concise, factual, and decision-oriented.
                    """
            )
    );

    public List<BuiltInPreset> presets() {
        return presets;
    }

    public BuiltInPreset defaultPreset(PresetUseCase useCase) {
        Objects.requireNonNull(useCase, "useCase must not be null");
        return presets.stream()
                .filter(preset -> preset.useCase() == useCase)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No built-in preset found for " + useCase));
    }
}
