package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrdInterviewDraft(int selectedQuestionIndex,
                                List<Answer> answers,
                                String createdAt,
                                String updatedAt) {
    public PrdInterviewDraft {
        answers = List.copyOf(answers == null ? List.of() : answers);
        createdAt = normalizeTimestamp(createdAt, Instant.now().toString());
        updatedAt = normalizeTimestamp(updatedAt, createdAt);
    }

    public static PrdInterviewDraft empty() {
        String timestamp = Instant.now().toString();
        return new PrdInterviewDraft(0, List.of(), timestamp, timestamp);
    }

    public String answerFor(String questionId) {
        return answers.stream()
                .filter(answer -> answer.questionId().equals(questionId))
                .map(Answer::answer)
                .findFirst()
                .orElse("");
    }

    public boolean hasAnswerFor(String questionId) {
        return !answerFor(questionId).isBlank();
    }

    public long answeredQuestionCount() {
        return answers.stream()
                .filter(answer -> answer.answer() != null && !answer.answer().isBlank())
                .count();
    }

    public PrdInterviewDraft withSelectedQuestionIndex(int replacementIndex) {
        return new PrdInterviewDraft(replacementIndex, answers, createdAt, Instant.now().toString());
    }

    public PrdInterviewDraft withAnswer(PrdInterviewQuestion question, String answer, int replacementIndex) {
        Objects.requireNonNull(question, "question must not be null");

        List<Answer> updatedAnswers = new ArrayList<>(answers.size() + 1);
        boolean replaced = false;
        String normalizedAnswer = answer == null ? "" : answer;
        for (Answer existingAnswer : answers) {
            if (!existingAnswer.questionId().equals(question.questionId())) {
                updatedAnswers.add(existingAnswer);
                continue;
            }

            replaced = true;
            if (!normalizedAnswer.isBlank()) {
                updatedAnswers.add(new Answer(question.questionId(), question.sectionId(), normalizedAnswer));
            }
        }

        if (!replaced && !normalizedAnswer.isBlank()) {
            updatedAnswers.add(new Answer(question.questionId(), question.sectionId(), normalizedAnswer));
        }

        return new PrdInterviewDraft(
                replacementIndex,
                updatedAnswers,
                createdAt,
                Instant.now().toString()
        );
    }

    private static String normalizeTimestamp(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String questionId, String sectionId, String answer) {
        public Answer {
            Objects.requireNonNull(questionId, "questionId must not be null");
            Objects.requireNonNull(sectionId, "sectionId must not be null");
            Objects.requireNonNull(answer, "answer must not be null");
        }
    }
}
