package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrdTaskSynchronizer {
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern STORY_HEADING_PATTERN = Pattern.compile("(?m)^###\\s+(US-\\d{3}):\\s+(.+?)\\s*$");
    private static final Pattern OUTCOME_PATTERN = Pattern.compile("(?im)^\\*\\*Outcome:\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern LEADING_LIST_MARKER_PATTERN =
            Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)");

    public SyncPlan plan(String markdown, PrdTaskState existingState) {
        ParsedPrd parsedPrd = parse(markdown);
        return plan(parsedPrd.stories(), existingState);
    }

    public PrdTaskState synchronize(ActiveProject activeProject,
                                    String markdown,
                                    PrdTaskState existingState,
                                    String timestamp) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        ParsedPrd parsedPrd = parse(markdown);
        Map<String, PrdTaskRecord> existingTasksById = indexExistingTasks(existingState);
        List<PrdTaskRecord> synchronizedTasks = new ArrayList<>(parsedPrd.stories().size());
        for (ParsedStory parsedStory : parsedPrd.stories()) {
            PrdTaskRecord existingTask = existingTasksById.get(parsedStory.taskId());
            if (existingTask == null) {
                synchronizedTasks.add(PrdTaskRecord.created(
                        parsedStory.taskId(),
                        parsedStory.title(),
                        parsedStory.outcome(),
                        timestamp
                ));
                continue;
            }

            synchronizedTasks.add(existingTask.withStoryDefinition(
                    parsedStory.title(),
                    parsedStory.outcome(),
                    timestamp
            ));
        }

        String createdAt = existingState == null ? timestamp : existingState.createdAt();
        return new PrdTaskState(
                PrdTaskState.SCHEMA_VERSION,
                activeProject.activePrdPath().toString(),
                parsedPrd.qualityGates(),
                synchronizedTasks,
                createdAt,
                timestamp
        );
    }

    private SyncPlan plan(List<ParsedStory> parsedStories, PrdTaskState existingState) {
        Map<String, PrdTaskRecord> existingTasksById = indexExistingTasks(existingState);
        Set<String> parsedTaskIds = new LinkedHashSet<>();
        List<String> addedTaskIds = new ArrayList<>();
        List<String> updatedTaskIds = new ArrayList<>();

        for (ParsedStory parsedStory : parsedStories) {
            parsedTaskIds.add(parsedStory.taskId());
            PrdTaskRecord existingTask = existingTasksById.get(parsedStory.taskId());
            if (existingTask == null) {
                addedTaskIds.add(parsedStory.taskId());
                continue;
            }

            if (!existingTask.title().equals(parsedStory.title())
                    || !existingTask.outcome().equals(parsedStory.outcome())) {
                updatedTaskIds.add(parsedStory.taskId());
            }
        }

        List<String> removedTaskIds = new ArrayList<>();
        for (PrdTaskRecord existingTask : existingTasksById.values()) {
            if (!parsedTaskIds.contains(existingTask.taskId())) {
                removedTaskIds.add(existingTask.taskId());
            }
        }

        return new SyncPlan(addedTaskIds, updatedTaskIds, removedTaskIds);
    }

    private Map<String, PrdTaskRecord> indexExistingTasks(PrdTaskState existingState) {
        Map<String, PrdTaskRecord> indexedTasks = new LinkedHashMap<>();
        if (existingState == null) {
            return indexedTasks;
        }

        for (PrdTaskRecord existingTask : existingState.tasks()) {
            indexedTasks.put(existingTask.taskId(), existingTask);
        }
        return indexedTasks;
    }

    private ParsedPrd parse(String markdown) {
        String normalizedMarkdown = normalizeLineEndings(markdown == null ? "" : markdown);
        List<Section> sections = sections(normalizedMarkdown);
        Section qualityGatesSection = findSection(sections, "Quality Gates");
        Section userStoriesSection = findSection(sections, "User Stories");
        return new ParsedPrd(
                qualityGatesSection == null ? List.of() : markdownListItems(qualityGatesSection.body()),
                userStoriesSection == null ? List.of() : parseStories(userStoriesSection.body())
        );
    }

    private List<ParsedStory> parseStories(String userStoriesBody) {
        Matcher matcher = STORY_HEADING_PATTERN.matcher(userStoriesBody);
        List<StoryMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new StoryMatch(
                    normalizeTaskId(matcher.group(1)),
                    matcher.group(2).trim(),
                    matcher.start(),
                    matcher.end()
            ));
        }

        List<ParsedStory> parsedStories = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            StoryMatch match = matches.get(index);
            int bodyStart = match.endIndex();
            int bodyEnd = index + 1 < matches.size() ? matches.get(index + 1).startIndex() : userStoriesBody.length();
            String storyBody = userStoriesBody.substring(bodyStart, bodyEnd).trim();
            parsedStories.add(new ParsedStory(
                    match.taskId(),
                    match.title(),
                    extractOutcome(storyBody, match.title())
            ));
        }
        return parsedStories;
    }

    private String extractOutcome(String storyBody, String fallback) {
        Matcher matcher = OUTCOME_PATTERN.matcher(storyBody);
        if (matcher.find()) {
            String outcome = matcher.group(1).trim();
            if (!outcome.isBlank()) {
                return outcome;
            }
        }

        for (String line : normalizeLineEndings(storyBody).split("\n")) {
            String normalizedLine = stripLeadingListMarker(line.trim());
            if (!normalizedLine.isBlank()) {
                return normalizedLine;
            }
        }

        return fallback;
    }

    private List<String> markdownListItems(String rawSectionBody) {
        if (rawSectionBody == null || rawSectionBody.isBlank()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (String line : normalizeLineEndings(rawSectionBody).split("\n")) {
            String normalizedLine = stripLeadingListMarker(line.trim());
            if (!normalizedLine.isBlank()) {
                items.add(normalizedLine);
            }
        }
        return items;
    }

    private List<Section> sections(String markdown) {
        Matcher matcher = SECTION_HEADING_PATTERN.matcher(markdown);
        List<SectionMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new SectionMatch(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }

        List<Section> sections = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            SectionMatch match = matches.get(index);
            int bodyStart = match.endIndex();
            int bodyEnd = index + 1 < matches.size() ? matches.get(index + 1).startIndex() : markdown.length();
            sections.add(new Section(match.title(), markdown.substring(bodyStart, bodyEnd).trim()));
        }
        return sections;
    }

    private Section findSection(List<Section> sections, String title) {
        String normalizedTitle = normalizeHeading(title);
        return sections.stream()
                .filter(section -> normalizeHeading(section.title()).equals(normalizedTitle))
                .findFirst()
                .orElse(null);
    }

    private String normalizeHeading(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll(":+$", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeTaskId(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String stripLeadingListMarker(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return LEADING_LIST_MARKER_PATTERN.matcher(value).replaceFirst("").trim();
    }

    public record SyncPlan(List<String> addedTaskIds, List<String> updatedTaskIds, List<String> removedTaskIds) {
        public SyncPlan {
            addedTaskIds = List.copyOf(addedTaskIds);
            updatedTaskIds = List.copyOf(updatedTaskIds);
            removedTaskIds = List.copyOf(removedTaskIds);
        }

        public boolean destructive() {
            return !removedTaskIds.isEmpty();
        }
    }

    private record ParsedPrd(List<String> qualityGates, List<ParsedStory> stories) {
    }

    private record ParsedStory(String taskId, String title, String outcome) {
    }

    private record StoryMatch(String taskId, String title, int startIndex, int endIndex) {
    }

    private record Section(String title, String body) {
    }

    private record SectionMatch(String title, int startIndex, int endIndex) {
    }
}
