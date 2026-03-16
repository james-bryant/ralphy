package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrdInterviewService {
    private final List<PrdInterviewQuestion> questions = List.of(
            new PrdInterviewQuestion(
                    "overviewContext",
                    "overview",
                    "Overview",
                    "Product Context",
                    "What product, workflow, or repository initiative is this PRD defining?",
                    "Describe the current context, the problem being addressed, and why the work matters now."
            ),
            new PrdInterviewQuestion(
                    "overviewAudience",
                    "overview",
                    "Overview",
                    "Primary Users",
                    "Who are the primary users or operators affected by this PRD?",
                    "Call out the main user groups, their environment, and any constraints that shape the solution."
            ),
            new PrdInterviewQuestion(
                    "goalsOutcomes",
                    "goals",
                    "Goals",
                    "Goals",
                    "What outcomes must this PRD achieve?",
                    "Focus on the concrete user, delivery, or business outcomes that define success."
            ),
            new PrdInterviewQuestion(
                    "qualityGates",
                    "quality-gates",
                    "Quality Gates",
                    "Quality Gates",
                    "What automated or manual quality gates must every implementation story satisfy?",
                    "List the commands, validations, smoke checks, or review expectations that should stay true story by story."
            ),
            new PrdInterviewQuestion(
                    "userStories",
                    "user-stories",
                    "User Stories",
                    "User Stories",
                    "Which user stories should this PRD include?",
                    "Use one line per story when possible. Include a stable ID, a short title, and the outcome the story should deliver."
            ),
            new PrdInterviewQuestion(
                    "scopeIn",
                    "scope-boundaries",
                    "Scope Boundaries",
                    "In Scope",
                    "What work is explicitly in scope for this PRD?",
                    "Capture the capabilities, milestones, or deliverables the team should plan to implement."
            ),
            new PrdInterviewQuestion(
                    "scopeOut",
                    "scope-boundaries",
                    "Scope Boundaries",
                    "Out of Scope",
                    "What should stay out of scope for this PRD?",
                    "List exclusions, deferred ideas, or guardrails that prevent the PRD from expanding unpredictably."
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
