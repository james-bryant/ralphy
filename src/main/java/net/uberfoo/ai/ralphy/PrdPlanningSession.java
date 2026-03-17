package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrdPlanningSession(String starterPrompt,
                                 List<Message> messages,
                                 String latestPrdMarkdown,
                                 String createdAt,
                                 String updatedAt) {
    public PrdPlanningSession {
        starterPrompt = normalizeOptionalValue(starterPrompt);
        messages = List.copyOf(messages == null ? List.of() : messages);
        latestPrdMarkdown = latestPrdMarkdown == null ? "" : latestPrdMarkdown;
        createdAt = normalizeTimestamp(createdAt, Instant.now().toString());
        updatedAt = normalizeTimestamp(updatedAt, createdAt);
    }

    public static PrdPlanningSession empty() {
        String timestamp = Instant.now().toString();
        return new PrdPlanningSession("", List.of(), "", timestamp, timestamp);
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    public boolean hasLatestPrdMarkdown() {
        return latestPrdMarkdown != null && !latestPrdMarkdown.isBlank();
    }

    public String lastAssistantMessage() {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if ("assistant".equals(message.role())) {
                return message.content();
            }
        }
        return "";
    }

    public PrdPlanningSession appendUserMessage(String content) {
        return appendMessage("user", content, latestPrdMarkdown);
    }

    public PrdPlanningSession appendAssistantMessage(String content, String replacementPrdMarkdown) {
        return appendMessage("assistant", content, replacementPrdMarkdown);
    }

    public PrdPlanningSession clear() {
        return empty();
    }

    private PrdPlanningSession appendMessage(String role, String content, String replacementPrdMarkdown) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            return this;
        }

        String timestamp = Instant.now().toString();
        List<Message> updatedMessages = new ArrayList<>(messages.size() + 1);
        updatedMessages.addAll(messages);
        updatedMessages.add(new Message(role, normalizedContent, timestamp));
        String resolvedStarterPrompt = starterPrompt;
        if ((resolvedStarterPrompt == null || resolvedStarterPrompt.isBlank()) && "user".equals(role)) {
            resolvedStarterPrompt = normalizedContent;
        }
        return new PrdPlanningSession(
                resolvedStarterPrompt,
                updatedMessages,
                replacementPrdMarkdown == null ? "" : replacementPrdMarkdown,
                createdAt,
                timestamp
        );
    }

    private static String normalizeTimestamp(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeOptionalValue(String value) {
        return value == null ? "" : value.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content, String createdAt) {
        public Message {
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(content, "content must not be null");
            createdAt = normalizeTimestamp(createdAt, Instant.now().toString());
        }
    }
}
