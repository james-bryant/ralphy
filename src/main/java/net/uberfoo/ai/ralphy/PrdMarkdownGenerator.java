package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrdMarkdownGenerator {
    private static final Pattern STORY_ID_PATTERN =
            Pattern.compile("(?i)^US[-_ ]?(\\d{1,3})(?:\\s*[:|\\-]\\s*|\\s+)(.+)$");
    private static final Pattern LEADING_LIST_MARKER_PATTERN =
            Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)");

    public String generate(ActiveProject activeProject, PrdInterviewDraft draft) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");

        PrdInterviewDraft normalizedDraft = draft == null ? PrdInterviewDraft.empty() : draft;
        StringBuilder markdown = new StringBuilder();

        markdown.append("# PRD: ").append(deriveTitle(activeProject, normalizedDraft)).append(System.lineSeparator());
        markdown.append(System.lineSeparator());

        appendSection(markdown, "Overview");
        appendParagraph(markdown,
                valueOrFallback(
                        normalizedDraft.answerFor("overviewContext"),
                        "Describe the product, workflow, or repository initiative this PRD defines."
                ));
        markdown.append(System.lineSeparator());
        markdown.append("### Primary Users").append(System.lineSeparator());
        appendParagraph(markdown,
                valueOrFallback(
                        normalizedDraft.answerFor("overviewAudience"),
                        "Primary users or operators have not been captured yet."
                ));

        appendListSection(markdown,
                "Goals",
                normalizedDraft.answerFor("goalsOutcomes"),
                "Goals still need to be captured.");
        appendListSection(markdown,
                "Quality Gates",
                normalizedDraft.answerFor("qualityGates"),
                "Quality gates still need to be captured.");

        appendSection(markdown, "User Stories");
        List<StoryEntry> storyEntries = storyEntries(normalizedDraft.answerFor("userStories"));
        if (storyEntries.isEmpty()) {
            markdown.append("No user stories captured yet.").append(System.lineSeparator());
        } else {
            for (StoryEntry storyEntry : storyEntries) {
                markdown.append("### US-").append(formatStoryId(storyEntry.storyId()))
                        .append(": ").append(storyEntry.title()).append(System.lineSeparator());
                markdown.append("**Outcome:** ").append(storyEntry.outcome()).append(System.lineSeparator());
                markdown.append(System.lineSeparator());
            }
        }

        appendSection(markdown, "Scope Boundaries");
        markdown.append("### In Scope").append(System.lineSeparator());
        appendMarkdownList(markdown,
                normalizedDraft.answerFor("scopeIn"),
                "In-scope work still needs to be captured.");
        markdown.append(System.lineSeparator());
        markdown.append("### Out of Scope").append(System.lineSeparator());
        appendMarkdownList(markdown,
                normalizedDraft.answerFor("scopeOut"),
                "Out-of-scope work still needs to be captured.");

        return markdown.toString().trim() + System.lineSeparator();
    }

    private void appendListSection(StringBuilder markdown, String heading, String rawAnswer, String emptyFallback) {
        appendSection(markdown, heading);
        appendMarkdownList(markdown, rawAnswer, emptyFallback);
        markdown.append(System.lineSeparator());
    }

    private void appendSection(StringBuilder markdown, String heading) {
        markdown.append("## ").append(heading).append(System.lineSeparator());
    }

    private void appendParagraph(StringBuilder markdown, String value) {
        markdown.append(normalizeParagraph(value)).append(System.lineSeparator());
    }

    private void appendMarkdownList(StringBuilder markdown, String rawAnswer, String emptyFallback) {
        List<String> items = markdownListItems(rawAnswer);
        if (items.isEmpty()) {
            markdown.append("- ").append(emptyFallback).append(System.lineSeparator());
            return;
        }

        for (String item : items) {
            markdown.append("- ").append(item).append(System.lineSeparator());
        }
    }

    private List<String> markdownListItems(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (String line : rawAnswer.replace("\r\n", "\n").split("\n")) {
            String normalizedLine = stripLeadingListMarker(line.trim());
            if (!normalizedLine.isBlank()) {
                items.add(normalizedLine);
            }
        }
        return items;
    }

    private List<StoryEntry> storyEntries(String rawAnswer) {
        List<String> storyLines = markdownListItems(rawAnswer);
        if (storyLines.isEmpty()) {
            return List.of();
        }

        List<ParsedStoryLine> parsedLines = storyLines.stream()
                .map(this::parseStoryLine)
                .toList();
        boolean preserveExplicitIds = shouldPreserveExplicitIds(parsedLines);
        List<StoryEntry> storyEntries = new ArrayList<>(parsedLines.size());
        for (int index = 0; index < parsedLines.size(); index++) {
            ParsedStoryLine parsedStoryLine = parsedLines.get(index);
            int storyId = preserveExplicitIds ? parsedStoryLine.explicitStoryId() : index + 1;
            storyEntries.add(new StoryEntry(storyId, parsedStoryLine.title(), parsedStoryLine.outcome()));
        }

        if (preserveExplicitIds) {
            storyEntries.sort((left, right) -> Integer.compare(left.storyId(), right.storyId()));
        }
        return storyEntries;
    }

    private ParsedStoryLine parseStoryLine(String storyLine) {
        Matcher matcher = STORY_ID_PATTERN.matcher(storyLine);
        String storyBody = storyLine;
        Integer explicitStoryId = null;
        if (matcher.matches()) {
            explicitStoryId = Integer.parseInt(matcher.group(1));
            storyBody = matcher.group(2).trim();
        }

        String title = storyBody;
        String outcome = storyBody;
        for (String separator : List.of(" | ", " - ", ": ")) {
            int separatorIndex = storyBody.indexOf(separator);
            if (separatorIndex <= 0 || separatorIndex >= storyBody.length() - separator.length()) {
                continue;
            }

            title = storyBody.substring(0, separatorIndex).trim();
            outcome = storyBody.substring(separatorIndex + separator.length()).trim();
            break;
        }

        if (title.isBlank()) {
            title = "Story";
        }
        if (outcome.isBlank()) {
            outcome = title;
        }
        return new ParsedStoryLine(explicitStoryId, title, outcome);
    }

    private boolean shouldPreserveExplicitIds(List<ParsedStoryLine> parsedLines) {
        Set<Integer> seenStoryIds = new HashSet<>();
        for (ParsedStoryLine parsedLine : parsedLines) {
            if (parsedLine.explicitStoryId() == null || !seenStoryIds.add(parsedLine.explicitStoryId())) {
                return false;
            }
        }
        return true;
    }

    private String deriveTitle(ActiveProject activeProject, PrdInterviewDraft draft) {
        String candidate = firstLine(draft.answerFor("overviewContext"));
        if (candidate.isBlank()) {
            return activeProject.displayName();
        }

        String normalizedCandidate = stripLeadingListMarker(candidate)
                .replaceAll("(?i)^PRD\\s*:?\\s*", "")
                .replaceAll("[.]+$", "")
                .trim();
        if (normalizedCandidate.isBlank()) {
            return activeProject.displayName();
        }
        if (normalizedCandidate.length() <= 80) {
            return normalizedCandidate;
        }
        return normalizedCandidate.substring(0, 77).trim() + "...";
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalizedValue = value.replace("\r\n", "\n");
        int lineBreakIndex = normalizedValue.indexOf('\n');
        return lineBreakIndex >= 0 ? normalizedValue.substring(0, lineBreakIndex).trim() : normalizedValue.trim();
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeParagraph(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
    }

    private String stripLeadingListMarker(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return LEADING_LIST_MARKER_PATTERN.matcher(value).replaceFirst("").trim();
    }

    private String formatStoryId(int storyId) {
        return String.format(Locale.ROOT, "%03d", storyId);
    }

    private record ParsedStoryLine(Integer explicitStoryId, String title, String outcome) {
    }

    private record StoryEntry(int storyId, String title, String outcome) {
    }
}
