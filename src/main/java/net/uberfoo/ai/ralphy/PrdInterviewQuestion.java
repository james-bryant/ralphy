package net.uberfoo.ai.ralphy;

import java.util.Objects;

public record PrdInterviewQuestion(String questionId,
                                   String sectionId,
                                   String sectionTitle,
                                   String title,
                                   String prompt,
                                   String guidance) {
    public PrdInterviewQuestion {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(sectionId, "sectionId must not be null");
        Objects.requireNonNull(sectionTitle, "sectionTitle must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
        Objects.requireNonNull(guidance, "guidance must not be null");
    }
}
