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
    private static final Pattern LABELED_DESCRIPTION_PATTERN =
            Pattern.compile("(?i)^(?:description|story):\\s*(.+)$");
    private static final Pattern LABELED_OUTCOME_PATTERN =
            Pattern.compile("(?i)^outcome:\\s*(.+)$");
    private static final Pattern LABELED_DEPENDENCIES_PATTERN =
            Pattern.compile("(?i)^(?:dependencies|depends on|prerequisites?):\\s*(.+)$");
    private static final Pattern LABELED_ACCEPTANCE_PATTERN =
            Pattern.compile("(?i)^(?:acceptance criteria|acceptance|criteria):\\s*(.+)$");
    private static final Pattern STORY_REFERENCE_PATTERN =
            Pattern.compile("\\bUS-\\d{3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_LIST_MARKER_PATTERN =
            Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+|\\[[ xX]\\]\\s+)");

    public String generate(ActiveProject activeProject, PrdInterviewDraft draft) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");

        PrdInterviewDraft normalizedDraft = draft == null ? PrdInterviewDraft.empty() : draft;
        StringBuilder markdown = new StringBuilder();

        markdown.append("# PRD: ").append(deriveTitle(activeProject, normalizedDraft)).append(System.lineSeparator());
        markdown.append(System.lineSeparator());

        appendSection(markdown, "Overview");
        appendParagraph(markdown,
                valueOrFallback(
                        answerForAny(normalizedDraft, "overviewContext"),
                        "Describe the product, workflow, or repository initiative this PRD defines."
                ));
        markdown.append(System.lineSeparator());
        markdown.append("### Primary Users").append(System.lineSeparator());
        appendParagraph(markdown,
                valueOrFallback(
                        answerForAny(normalizedDraft, "overviewAudience"),
                        "Primary users or operators have not been captured yet."
                ));

        appendListSection(markdown,
                "Goals",
                answerForAny(normalizedDraft, "goalsOutcomes"),
                "Goals still need to be captured.");
        appendListSection(markdown,
                "Quality Gates",
                answerForAny(normalizedDraft, "qualityGates"),
                "Quality gates still need to be captured.");

        appendSection(markdown, "User Stories");
        List<StoryEntry> storyEntries = storyEntries(answerForAny(normalizedDraft, "userStories"));
        if (storyEntries.isEmpty()) {
            markdown.append("No user stories captured yet.").append(System.lineSeparator());
        } else {
            for (StoryEntry storyEntry : storyEntries) {
                markdown.append("### US-").append(formatStoryId(storyEntry.storyId()))
                        .append(": ").append(storyEntry.title()).append(System.lineSeparator());
                markdown.append("**Description:** ").append(storyEntry.description()).append(System.lineSeparator());
                markdown.append("**Outcome:** ").append(storyEntry.outcome()).append(System.lineSeparator());
                markdown.append("**Dependencies:** ").append(formatDependencies(storyEntry.dependencies()))
                        .append(System.lineSeparator());
                markdown.append("**Acceptance Criteria:**").append(System.lineSeparator());
                for (String acceptanceCriterion : storyEntry.acceptanceCriteria()) {
                    markdown.append("- [ ] ").append(acceptanceCriterion).append(System.lineSeparator());
                }
                markdown.append(System.lineSeparator());
            }
        }

        appendSection(markdown, "Scope Boundaries");
        markdown.append("### In Scope").append(System.lineSeparator());
        appendMarkdownList(markdown,
                answerForAny(normalizedDraft, "scopeIn"),
                "In-scope work still needs to be captured.");
        markdown.append(System.lineSeparator());
        markdown.append("### Out of Scope").append(System.lineSeparator());
        appendMarkdownList(markdown,
                nonQuestionItems(answerForAny(normalizedDraft, "scopeOut")),
                "Out-of-scope work still needs to be captured.");
        markdown.append(System.lineSeparator());

        appendListSection(markdown,
                "Functional Requirements",
                answerForAny(normalizedDraft, "scopeIn"),
                "Functional requirements still need to be captured.");
        appendListSection(markdown,
                "Technical Considerations",
                answerForAny(normalizedDraft, "overviewAudience"),
                "Technical considerations still need to be captured.");
        appendListSection(markdown,
                "Success Metrics",
                answerForAny(normalizedDraft, "goalsOutcomes"),
                "Success metrics still need to be captured.");
        appendListSection(markdown,
                "Open Questions",
                openQuestionItems(answerForAny(normalizedDraft, "scopeOut")),
                "No additional open questions were captured.");

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

    private String answerForAny(PrdInterviewDraft draft, String... questionIds) {
        for (String questionId : questionIds) {
            String answer = draft.answerFor(questionId);
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
        }
        return "";
    }

    private String openQuestionItems(String rawAnswer) {
        List<String> openQuestions = markdownListItems(rawAnswer).stream()
                .filter(item -> item.endsWith("?"))
                .toList();
        return String.join(System.lineSeparator(), openQuestions);
    }

    private String nonQuestionItems(String rawAnswer) {
        List<String> items = markdownListItems(rawAnswer).stream()
                .filter(item -> !item.endsWith("?"))
                .toList();
        return String.join(System.lineSeparator(), items);
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
            storyEntries.add(new StoryEntry(
                    storyId,
                    parsedStoryLine.title(),
                    parsedStoryLine.outcome(),
                    parsedStoryLine.description(),
                    parsedStoryLine.dependencies(),
                    parsedStoryLine.acceptanceCriteria()
            ));
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

        List<String> segments = List.of(storyBody.split("\\s*\\|\\s*"));
        String title = "";
        String outcome = "";
        String description = "";
        List<String> dependencies = new ArrayList<>();
        List<String> acceptanceCriteria = new ArrayList<>();

        if (!segments.isEmpty()) {
            title = segments.getFirst().trim();
        }

        if (segments.size() == 1) {
            StoryTitleOutcome titleOutcome = splitTitleAndOutcome(storyBody);
            title = titleOutcome.title();
            outcome = titleOutcome.outcome();
        } else {
            for (int index = 1; index < segments.size(); index++) {
                String segment = segments.get(index).trim();
                if (segment.isBlank()) {
                    continue;
                }
                Matcher descriptionMatcher = LABELED_DESCRIPTION_PATTERN.matcher(segment);
                if (descriptionMatcher.matches()) {
                    description = descriptionMatcher.group(1).trim();
                    continue;
                }
                Matcher outcomeMatcher = LABELED_OUTCOME_PATTERN.matcher(segment);
                if (outcomeMatcher.matches()) {
                    outcome = outcomeMatcher.group(1).trim();
                    continue;
                }
                Matcher dependenciesMatcher = LABELED_DEPENDENCIES_PATTERN.matcher(segment);
                if (dependenciesMatcher.matches()) {
                    dependencies.addAll(parseDependencies(dependenciesMatcher.group(1)));
                    continue;
                }
                Matcher acceptanceMatcher = LABELED_ACCEPTANCE_PATTERN.matcher(segment);
                if (acceptanceMatcher.matches()) {
                    acceptanceCriteria.addAll(parseAcceptanceCriteria(acceptanceMatcher.group(1)));
                    continue;
                }
                if (description.isBlank() && segment.toLowerCase(Locale.ROOT).startsWith("as a ")) {
                    description = segment;
                    continue;
                }
                if (outcome.isBlank()) {
                    outcome = segment;
                    continue;
                }
                acceptanceCriteria.addAll(parseAcceptanceCriteria(segment));
            }
        }

        if (title.isBlank()) {
            title = "Story";
        }
        if (outcome.isBlank()) {
            outcome = title;
        }
        if (description.isBlank()) {
            description = synthesizeDescription(title, outcome);
        }
        if (acceptanceCriteria.isEmpty()) {
            acceptanceCriteria.add(outcomeAsCriterion(outcome));
        }
        return new ParsedStoryLine(
                explicitStoryId,
                title,
                outcome,
                description,
                List.copyOf(new java.util.LinkedHashSet<>(dependencies)),
                List.copyOf(new java.util.LinkedHashSet<>(acceptanceCriteria))
        );
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
        String candidate = firstLine(answerForAny(draft, "overviewContext"));
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

    private StoryTitleOutcome splitTitleAndOutcome(String storyBody) {
        String title = storyBody;
        String outcome = storyBody;
        for (String separator : List.of(" - ", ": ")) {
            int separatorIndex = storyBody.indexOf(separator);
            if (separatorIndex <= 0 || separatorIndex >= storyBody.length() - separator.length()) {
                continue;
            }

            title = storyBody.substring(0, separatorIndex).trim();
            outcome = storyBody.substring(separatorIndex + separator.length()).trim();
            break;
        }
        return new StoryTitleOutcome(title.isBlank() ? "Story" : title, outcome.isBlank() ? title : outcome);
    }

    private List<String> parseDependencies(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<String> dependencies = new ArrayList<>();
        Matcher matcher = STORY_REFERENCE_PATTERN.matcher(rawValue.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            dependencies.add(matcher.group());
        }
        return dependencies;
    }

    private List<String> parseAcceptanceCriteria(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        List<String> criteria = new ArrayList<>();
        for (String segment : rawValue.split("\\s*;\\s*")) {
            String normalizedSegment = stripLeadingListMarker(segment.trim());
            if (!normalizedSegment.isBlank()) {
                criteria.add(normalizeSentence(normalizedSegment));
            }
        }
        return criteria;
    }

    private String synthesizeDescription(String title, String outcome) {
        if (!hasText(outcome) || outcome.equals(title)) {
            return "As a user, I want " + normalizeSentence(title);
        }
        return "As a user, I want " + title + " so that " + normalizeSentence(outcome);
    }

    private String outcomeAsCriterion(String outcome) {
        return normalizeSentence(outcome);
    }

    private String formatDependencies(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return "None.";
        }
        return String.join(", ", dependencies);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeSentence(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.endsWith(".") || normalized.endsWith("!") || normalized.endsWith("?")) {
            return normalized;
        }
        return normalized + ".";
    }

    private record StoryTitleOutcome(String title, String outcome) {
    }

    private record ParsedStoryLine(Integer explicitStoryId,
                                   String title,
                                   String outcome,
                                   String description,
                                   List<String> dependencies,
                                   List<String> acceptanceCriteria) {
    }

    private record StoryEntry(int storyId,
                              String title,
                              String outcome,
                              String description,
                              List<String> dependencies,
                              List<String> acceptanceCriteria) {
    }
}
