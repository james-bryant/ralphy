package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdPlannerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void continueConversationAppendsAssistantQuestionsToTheSession() throws IOException {
        ActiveProject activeProject = new ActiveProject(createRepository("planner-question-repo"));
        AtomicReference<CodexLauncherService.CodexLaunchPlan> executedPlan = new AtomicReference<>();
        PrdPlannerService plannerService = new PrdPlannerService(
                createLauncherService(),
                new PresetCatalogService(),
                (launchPlan, runOutputListener) -> {
                    executedPlan.set(launchPlan);
                    return PrdPlannerService.PlannerExecution.completed(
                            41L,
                            0,
                            """
                            {"role":"assistant","assistant_summary":"1. What is the primary goal?\\n   A. Reduce manual PRD work\\n   B. Improve review quality\\n2. What quality gates must pass?\\n   A. .\\\\mvnw.cmd clean verify jacoco:report\\n   B. Other"}
                            """,
                            ""
                    );
                }
        );

        PrdPlannerService.PlannerTurnResult result = plannerService.continueConversation(
                activeProject,
                ExecutionProfile.nativePowerShell(),
                PrdPlanningSession.empty(),
                "Build a live PRD planning workflow for repository authors.",
                CodexLauncherService.RunOutputListener.noop()
        );

        assertTrue(result.successful());
        assertEquals(2, result.session().messages().size());
        assertEquals("Build a live PRD planning workflow for repository authors.", result.session().starterPrompt());
        assertTrue(result.assistantMessage().contains("What is the primary goal?"));
        assertFalse(result.session().hasLatestPrdMarkdown());
        assertTrue(executedPlan.get().promptText().contains("Conversation Transcript"));
        assertTrue(executedPlan.get().promptText().contains("Always confirm the required quality gates"));
        assertTrue(executedPlan.get().promptText().contains("### US-001: Story Title"));
        assertTrue(executedPlan.get().promptText().contains("**Dependencies:** None."));
    }

    @Test
    void continueConversationExtractsPrdBlocksIntoTheLatestDraft() throws IOException {
        ActiveProject activeProject = new ActiveProject(createRepository("planner-prd-repo"));
        PrdPlannerService plannerService = new PrdPlannerService(
                createLauncherService(),
                new PresetCatalogService(),
                (launchPlan, runOutputListener) -> PrdPlannerService.PlannerExecution.completed(
                        42L,
                        0,
                        """
                        {"role":"assistant","assistant_summary":"[PRD]\\n# PRD: Live Planner\\n\\n## Overview\\nInteractive planning.\\n\\n## Goals\\n- Improve PRD drafting\\n\\n## Quality Gates\\n- .\\\\mvnw.cmd clean verify jacoco:report\\n\\n## User Stories\\n### US-001: Ask follow-up questions\\n**Description:** As a repository author, I want the planner to ask follow-up questions so that the draft closes the important gaps.\\n**Dependencies:** None.\\n**Acceptance Criteria:**\\n- [ ] The planner asks targeted follow-up questions before finalizing the PRD.\\n\\n## Scope Boundaries\\n### In Scope\\n- Live planning\\n\\n### Out of Scope\\n- Code implementation\\n[/PRD]"}
                        """,
                        ""
                )
        );

        PrdPlanningSession existingSession = PrdPlanningSession.empty().appendUserMessage("Plan a live PRD planner.");
        PrdPlannerService.PlannerTurnResult result = plannerService.continueConversation(
                activeProject,
                ExecutionProfile.nativePowerShell(),
                existingSession,
                "Use Codex for iterative questions and draft generation.",
                CodexLauncherService.RunOutputListener.noop()
        );

        assertTrue(result.successful());
        assertTrue(result.latestPrdMarkdown().contains("# PRD: Live Planner"));
        assertTrue(result.session().hasLatestPrdMarkdown());
        assertTrue(result.session().latestPrdMarkdown().contains("## Quality Gates"));
    }

    @Test
    void continueConversationTreatsMarkerlessMarkdownAsAPrdDraft() throws IOException {
        ActiveProject activeProject = new ActiveProject(createRepository("planner-markerless-prd-repo"));
        PrdPlannerService plannerService = new PrdPlannerService(
                createLauncherService(),
                new PresetCatalogService(),
                (launchPlan, runOutputListener) -> PrdPlannerService.PlannerExecution.completed(
                        43L,
                        0,
                        """
                        {"role":"assistant","assistant_summary":"# PRD: Live Planner\\n\\n**Overview**\\nInteractive planning.\\n\\n**Goals**\\n- Improve PRD drafting\\n\\n**Quality Gates**\\n- .\\\\mvnw.cmd clean verify jacoco:report\\n\\n**User Stories**\\n## US-001: Ask follow-up questions\\n**Description:** As a repository author, I want targeted follow-up questions so that the PRD captures the missing context.\\n**Dependencies:** None.\\n**Acceptance Criteria:**\\n- [ ] The planner asks targeted follow-up questions before finalizing the PRD.\\n\\n**Functional Requirements**\\n- FR-1: Capture iterative clarifications.\\n\\n**Scope Boundaries**\\n- No implementation."}
                        """,
                        ""
                )
        );

        PrdPlannerService.PlannerTurnResult result = plannerService.continueConversation(
                activeProject,
                ExecutionProfile.nativePowerShell(),
                PrdPlanningSession.empty(),
                "Create a live PRD planner.",
                CodexLauncherService.RunOutputListener.noop()
        );

        assertTrue(result.successful());
        assertTrue(result.session().hasLatestPrdMarkdown());
        assertTrue(result.latestPrdMarkdown().contains("# PRD: Live Planner"));
    }

    @Test
    void continueConversationFinalizesWhenConversationAlreadyHasEnoughContext() throws IOException {
        ActiveProject activeProject = new ActiveProject(createRepository("planner-finalize-repo"));
        AtomicReference<CodexLauncherService.CodexLaunchPlan> executedPlan = new AtomicReference<>();
        PrdPlannerService plannerService = new PrdPlannerService(
                createLauncherService(),
                new PresetCatalogService(),
                (launchPlan, runOutputListener) -> {
                    executedPlan.set(launchPlan);
                    return PrdPlannerService.PlannerExecution.completed(
                            44L,
                            0,
                            """
                            {"role":"assistant","assistant_summary":"[PRD]\\n# PRD: Finalized\\n[/PRD]"}
                            """,
                            ""
                    );
                }
        );

        PrdPlanningSession seededSession = PrdPlanningSession.empty()
                .appendUserMessage("Build a live planner for repository PRDs.")
                .appendAssistantMessage("1. What are the goals? 2. What quality gates must pass? 3. What is out of scope?", "")
                .appendUserMessage("Goals: iterative planning. Quality gates: .\\\\mvnw.cmd clean verify jacoco:report. Out of scope: implementation. Target users are repo authors. Integrate with the current PRD editor.")
                .appendAssistantMessage("Anything else before I finalize the PRD?", "");

        plannerService.continueConversation(
                activeProject,
                ExecutionProfile.nativePowerShell(),
                seededSession,
                "Finalize it now.",
                CodexLauncherService.RunOutputListener.noop()
        );

        assertTrue(executedPlan.get().promptText().contains("The conversation already has enough context to finalize the PRD."));
    }

    @Test
    void continueConversationIncludesSelectedPlannerModelInCodexCommand() throws IOException {
        ActiveProject activeProject = new ActiveProject(createRepository("planner-model-repo"));
        AtomicReference<CodexLauncherService.CodexLaunchPlan> executedPlan = new AtomicReference<>();
        PrdPlannerService plannerService = new PrdPlannerService(
                createLauncherService(),
                new PresetCatalogService(),
                (launchPlan, runOutputListener) -> {
                    executedPlan.set(launchPlan);
                    return PrdPlannerService.PlannerExecution.completed(
                            45L,
                            0,
                            """
                            {"role":"assistant","assistant_summary":"1. Confirm goals."}
                            """,
                            ""
                    );
                }
        );

        PrdPlannerService.PlannerTurnResult result = plannerService.continueConversation(
                activeProject,
                ExecutionProfile.nativePowerShell(),
                PrdPlanningSession.empty(),
                "Plan PRD updates.",
                "gpt-5.4-mini",
                CodexLauncherService.RunOutputListener.noop()
        );

        assertTrue(result.successful());
        assertTrue(executedPlan.get().command().contains("--model"));
        assertTrue(executedPlan.get().command().contains("gpt-5.4-mini"));
    }

    private CodexLauncherService createLauncherService() {
        return new CodexLauncherService(
                LocalMetadataStorage.forTest(tempDir.resolve("local-storage")),
                Clock.fixed(Instant.parse("2026-03-16T18:00:00Z"), ZoneOffset.UTC),
                () -> "planner-run-123",
                launchPlan -> {
                    throw new AssertionError("PrdPlannerService should use its own executor for process execution.");
                },
                "C:\\tools\\codex.cmd",
                distribution -> "/bin/bash"
        );
    }

    private Path createRepository(String name) throws IOException {
        Path repository = tempDir.resolve(name);
        Files.createDirectories(repository);
        return repository;
    }
}
