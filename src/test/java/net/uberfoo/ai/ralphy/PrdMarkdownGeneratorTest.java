package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdMarkdownGeneratorTest {
    private final PrdMarkdownGenerator generator = new PrdMarkdownGenerator();

    @TempDir
    Path tempDir;

    @Test
    void generateCreatesRequiredSectionsAndOrdersExplicitStoryIds() {
        ActiveProject activeProject = new ActiveProject(tempDir.resolve("prd-generator-repo"));
        PrdInterviewDraft draft = new PrdInterviewDraft(
                0,
                List.of(
                        new PrdInterviewDraft.Answer(
                                "overviewContext",
                                "overview",
                                "Ralphy desktop authoring for repository-owned PRDs."
                        ),
                        new PrdInterviewDraft.Answer(
                                "overviewAudience",
                                "overview",
                                "Developers operating one local repository at a time."
                        ),
                        new PrdInterviewDraft.Answer(
                                "goalsOutcomes",
                                "goals",
                                "Generate Markdown PRDs inside the app.\nKeep stories small and reviewable."
                        ),
                        new PrdInterviewDraft.Answer(
                                "qualityGates",
                                "quality-gates",
                                ".\\mvnw.cmd clean verify jacoco:report\nManual smoke verification on Windows"
                        ),
                        new PrdInterviewDraft.Answer(
                                "userStories",
                                "user-stories",
                                "US-010: Edit the active PRD | Users can refine the generated Markdown.\n"
                                        + "US-002: Generate the active PRD | Interview answers become reviewable Markdown."
                        ),
                        new PrdInterviewDraft.Answer(
                                "scopeIn",
                                "scope-boundaries",
                                "Guided PRD drafting\nRepository-owned Markdown output"
                        ),
                        new PrdInterviewDraft.Answer(
                                "scopeOut",
                                "scope-boundaries",
                                "Remote collaboration\nCloud-hosted orchestration"
                        )
                ),
                "2026-03-15T18:00:00Z",
                "2026-03-15T18:05:00Z"
        );

        String markdown = generator.generate(activeProject, draft);

        assertTrue(markdown.contains("# PRD: Ralphy desktop authoring for repository-owned PRDs"));
        assertTrue(markdown.contains("## Overview"));
        assertTrue(markdown.contains("### Primary Users"));
        assertTrue(markdown.contains("## Goals"));
        assertTrue(markdown.contains("## Quality Gates"));
        assertTrue(markdown.contains("## User Stories"));
        assertTrue(markdown.contains("## Scope Boundaries"));
        assertTrue(markdown.contains("## Functional Requirements"));
        assertTrue(markdown.contains("## Technical Considerations"));
        assertTrue(markdown.contains("## Success Metrics"));
        assertTrue(markdown.contains("## Open Questions"));
        assertTrue(markdown.contains("### In Scope"));
        assertTrue(markdown.contains("### Out of Scope"));
        assertTrue(markdown.indexOf("### US-002: Generate the active PRD")
                < markdown.indexOf("### US-010: Edit the active PRD"));
        assertTrue(markdown.contains("### US-002: Generate the active PRD"));
        assertTrue(markdown.contains("**Description:** As a user, I want Generate the active PRD so that Interview answers become reviewable Markdown."));
        assertTrue(markdown.contains("**Dependencies:** None."));
        assertTrue(markdown.contains("**Acceptance Criteria:**"));
        assertTrue(markdown.contains("- [ ] Interview answers become reviewable Markdown."));
    }

    @Test
    void generateAssignsSequentialStoryIdsWhenTheDraftDoesNotProvideThem() {
        ActiveProject activeProject = new ActiveProject(tempDir.resolve("sequential-story-repo"));
        PrdInterviewDraft draft = new PrdInterviewDraft(
                0,
                List.of(new PrdInterviewDraft.Answer(
                        "userStories",
                        "user-stories",
                        "Generate the active PRD | Turn interview answers into Markdown.\n"
                                + "Regenerate from the latest draft | Overwrite the saved PRD with new answers."
                )),
                "2026-03-15T18:00:00Z",
                "2026-03-15T18:05:00Z"
        );

        String markdown = generator.generate(activeProject, draft);

        assertTrue(markdown.contains("### US-001: Generate the active PRD"));
        assertTrue(markdown.contains("### US-002: Regenerate from the latest draft"));
        assertTrue(markdown.indexOf("### US-001: Generate the active PRD")
                < markdown.indexOf("### US-002: Regenerate from the latest draft"));
        assertTrue(markdown.contains("**Description:** As a user, I want Generate the active PRD so that Turn interview answers into Markdown."));
        assertTrue(markdown.contains("- [ ] Overwrite the saved PRD with new answers."));
    }
}
