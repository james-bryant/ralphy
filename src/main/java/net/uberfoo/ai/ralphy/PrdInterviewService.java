package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrdInterviewService {
    private final List<PrdInterviewQuestion> questions = List.of(
            new PrdInterviewQuestion(
                    "overviewContext",
                    "overview",
                    "Prompt-First Planning",
                    "Starter Prompt",
                    "What feature, workflow, or repository initiative do you want this PRD to plan?",
                    "Use a short freeform prompt first. Include the problem, the desired outcome, and why the work matters now."
            ),
            new PrdInterviewQuestion(
                    "overviewAudience",
                    "planning-clarification",
                    "Planning Clarifications",
                    "Users, Constraints, and Integration",
                    """
                    Based on the starter prompt, answer the core planning questions:
                    1. Who are the primary users or operators?
                    2. What environment, constraints, or workflows shape the solution?
                    3. Which existing systems, files, or integrations does this need to fit?
                    """,
                    "Answer in short labeled bullets when practical, for example `Users: ...`, `Constraints: ...`, `Integration: ...`."
            ),
            new PrdInterviewQuestion(
                    "goalsOutcomes",
                    "goals",
                    "Goals and Success",
                    "Goals and Success Signals",
                    """
                    What must this plan achieve?
                    1. List the concrete goals.
                    2. List the success signals or outcomes that tell us the work is done well.
                    3. Call out any core functionality that must exist for the PRD to be considered complete.
                    """,
                    "Keep this explicit and testable. One outcome per line is preferred."
            ),
            new PrdInterviewQuestion(
                    "qualityGates",
                    "quality-gates",
                    "Quality Gates",
                    "Quality Gates",
                    """
                    What quality commands and review checks must every story satisfy?
                    1. List the required commands that must pass.
                    2. Note any manual verification that is still required.
                    3. For UI work, say whether visual verification is required.
                    """,
                    "This round is mandatory. Use one line per command or check."
            ),
            new PrdInterviewQuestion(
                    "userStories",
                    "user-stories",
                    "User Stories",
                    "User Stories",
                    """
                    Which small, independently completable user stories should this PRD include?
                    1. Break the work into focused stories.
                    2. Keep each story small enough for one agent session.
                    3. Include the user benefit or outcome for each story.
                    """,
                    "Use one line per story when possible, for example `US-010: Title | Outcome`."
            ),
            new PrdInterviewQuestion(
                    "scopeIn",
                    "scope-boundaries",
                    "Scope and Requirements",
                    "In Scope and Functional Requirements",
                    """
                    What is explicitly in scope for this plan?
                    1. List the capabilities, milestones, or deliverables that belong in the PRD.
                    2. Add any functional requirements or integration requirements that must be honored.
                    3. Call out important technical considerations if they are already known.
                    """,
                    "One line per item works best. Prefix formal requirements with `FR-` when you already know them."
            ),
            new PrdInterviewQuestion(
                    "scopeOut",
                    "scope-boundaries",
                    "Boundaries and Follow-Up",
                    "Non-Goals and Open Questions",
                    """
                    What should stay out of scope, and what still needs clarification?
                    1. List the non-goals or exclusions.
                    2. Add any deferred ideas or guardrails.
                    3. Capture any unresolved planning questions.
                    """,
                    "List exclusions as plain bullets. Put unresolved questions on separate lines ending with `?`."
            )
    );

    public List<PrdInterviewQuestion> questions() {
        return questions;
    }

    public int questionCount() {
        return questions.size();
    }

    public int normalizeQuestionIndex(int questionIndex) {
        if (questions.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(questionIndex, questions.size() - 1));
    }

    public PrdInterviewQuestion questionForIndex(int questionIndex) {
        return questions.get(normalizeQuestionIndex(questionIndex));
    }

    public PrdInterviewDraft normalizeDraft(PrdInterviewDraft draft) {
        if (draft == null) {
            return PrdInterviewDraft.empty();
        }
        return new PrdInterviewDraft(
                normalizeQuestionIndex(draft.selectedQuestionIndex()),
                draft.answers(),
                draft.createdAt(),
                draft.updatedAt()
        );
    }
}
