package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RalphPrdJsonMapper {
    private static final Pattern DOCUMENT_TITLE_PATTERN = Pattern.compile("(?m)^#\\s+PRD:\\s*(.+?)\\s*$");
    private static final String FLEXIBLE_SECTION_TITLES =
            "Overview|Introduction|Goals|Quality Gates|User Stories|Scope Boundaries|Functional Requirements"
                    + "|Technical Considerations|Success Metrics|Open Questions|Non-Goals|Non Goals";
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile(
            "(?im)^(?:##\\s+(?!US-\\d{3}:)(.+?)\\s*$|\\*\\*((" + FLEXIBLE_SECTION_TITLES + "):?)\\*\\*\\s*$)"
    );
    private static final Pattern STORY_HEADING_PATTERN = Pattern.compile(
            "(?m)^(?:##|###)\\s+(US-\\d{3}):\\s+(.+?)\\s*$"
    );
    private static final Pattern OUTCOME_PATTERN = Pattern.compile("(?im)^\\*\\*Outcome:\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("(?im)^\\*\\*(?:Description|Story):\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern DEPENDENCY_LINE_PATTERN =
            Pattern.compile("(?im)^(?:\\*\\*(?:Dependencies|Prerequisites?|Depends\\s+On):\\*\\*\\s*(.+?)"
                    + "|(?:\\*\\*(?:Dependencies|Prerequisites?|Depends\\s+On)\\*\\*|Dependencies|Prerequisites?|Depends\\s+On)\\s*:\\s*(.+?))\\s*$");
    private static final Pattern ACCEPTANCE_CRITERIA_HEADING_PATTERN =
            Pattern.compile("(?im)^\\*\\*Acceptance Criteria:\\*\\*\\s*(.+?)?\\s*$|^####\\s+Acceptance Criteria\\s*$");
    private static final Pattern STORY_ID_REFERENCE_PATTERN = Pattern.compile("\\bUS-\\d{3}\\b");
    private static final Pattern LEADING_LIST_MARKER_PATTERN =
            Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+|\\[[ xX]\\]\\s+)");

    public RalphPrdJsonDocument toDocument(ActiveProject activeProject, String markdown, PrdTaskState taskState) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(taskState, "taskState must not be null");

        String normalizedMarkdown = normalizeLineEndings(markdown == null ? "" : markdown);
        List<Section> sections = sections(normalizedMarkdown);
        Section overviewSection = findSection(sections, "Overview");
        Section userStoriesSection = findSection(sections, "User Stories");
        List<String> qualityGates = taskState.qualityGates().isEmpty()
                ? List.of()
                : List.copyOf(taskState.qualityGates());
        List<ParsedStory> parsedStories = userStoriesSection == null
                ? List.of()
                : parseStories(userStoriesSection.body(), qualityGates);

        List<RalphPrdJsonUserStory> userStories = new ArrayList<>(taskState.tasks().size());
        for (int index = 0; index < taskState.tasks().size(); index++) {
            PrdTaskRecord task = taskState.tasks().get(index);
            ParsedStory parsedStory = parsedStories.stream()
                    .filter(candidate -> candidate.id().equals(task.taskId()))
                    .findFirst()
                    .orElse(null);
            String outcome = hasText(task.outcome())
                    ? task.outcome()
                    : parsedStory == null ? task.title() : parsedStory.outcome();
            String description = parsedStory == null || !hasText(parsedStory.description())
                    ? synthesizeDescription(task.title(), outcome)
                    : parsedStory.description();

            List<String> acceptanceCriteria = combineAcceptanceCriteria(
                    parsedStory == null ? List.of(outcome) : parsedStory.acceptanceCriteria(),
                    qualityGates
            );
            userStories.add(new RalphPrdJsonUserStory(
                    task.taskId(),
                    task.title(),
                    description,
                    acceptanceCriteria,
                    index + 1,
                    task.status().isPassed(),
                    parsedStory == null ? List.of() : parsedStory.dependsOn(),
                    completionNotes(task),
                    outcome,
                    task.status().storageValue(),
                    task.history(),
                    task.attempts(),
                    task.createdAt(),
                    task.updatedAt()
            ));
        }

        String name = documentTitle(normalizedMarkdown, activeProject.displayName());
        return new RalphPrdJsonDocument(
                name,
                branchName(activeProject, normalizedMarkdown),
                overviewDescription(overviewSection, activeProject.displayName()),
                qualityGates,
                userStories,
                taskState.sourcePrdPath(),
                taskState.createdAt(),
                taskState.updatedAt()
        );
    }

    public String branchName(ActiveProject activeProject, String markdown) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        String normalizedMarkdown = normalizeLineEndings(markdown == null ? "" : markdown);
        return "ralph/" + slugify(documentTitle(normalizedMarkdown, activeProject.displayName()));
    }

    public PrdTaskState toTaskState(ActiveProject activeProject, RalphPrdJsonDocument document) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(document, "document must not be null");

        List<String> qualityGates = document.qualityGates() == null ? List.of() : document.qualityGates();
        String createdAt = firstNonBlank(document.createdAt(), document.updatedAt(), "");
        String updatedAt = firstNonBlank(document.updatedAt(), document.createdAt(), createdAt);
        List<PrdTaskRecord> tasks = new ArrayList<>(document.userStories().size());
        for (RalphPrdJsonUserStory story : document.userStories()) {
            String storyCreatedAt = firstNonBlank(story.createdAt(), createdAt, updatedAt);
            String storyUpdatedAt = firstNonBlank(story.updatedAt(), updatedAt, storyCreatedAt);
            List<PrdTaskHistoryEntry> history = story.history().isEmpty()
                    ? List.of(new PrdTaskHistoryEntry(
                    storyUpdatedAt,
                    story.passes() ? "STATUS_CHANGE" : "CREATED",
                    normalizeStatus(story),
                    story.passes() ? firstNonBlank(story.completionNotes(), "Completed outside Ralphy.")
                            : "Created from the exported prd.json."
            ))
                    : story.history();
            tasks.add(new PrdTaskRecord(
                    story.id(),
                    story.title(),
                    firstNonBlank(story.outcome(), derivedOutcome(story, qualityGates), story.title()),
                    normalizeStatus(story),
                    history,
                    story.attempts(),
                    storyCreatedAt,
                    storyUpdatedAt
            ));
        }

        return new PrdTaskState(
                PrdTaskState.SCHEMA_VERSION,
                firstNonBlank(document.sourcePrdPath(), activeProject.activePrdPath().toString()),
                qualityGates,
                tasks,
                createdAt,
                updatedAt
        );
    }

    private PrdTaskStatus normalizeStatus(RalphPrdJsonUserStory story) {
        String rawStatus = firstNonBlank(story.ralphyStatus(), story.passes()
                ? PrdTaskStatus.COMPLETED.storageValue()
                : PrdTaskStatus.READY.storageValue());
        try {
            PrdTaskStatus parsedStatus = PrdTaskStatus.fromStorageValue(rawStatus);
            if (story.passes() && parsedStatus != PrdTaskStatus.COMPLETED) {
                return PrdTaskStatus.COMPLETED;
            }
            if (!story.passes() && parsedStatus == PrdTaskStatus.COMPLETED) {
                return PrdTaskStatus.READY;
            }
            return parsedStatus;
        } catch (IllegalArgumentException exception) {
            return story.passes() ? PrdTaskStatus.COMPLETED : PrdTaskStatus.READY;
        }
    }

    private String derivedOutcome(RalphPrdJsonUserStory story, List<String> qualityGates) {
        Set<String> qualityGateSet = new LinkedHashSet<>(qualityGates);
        for (String acceptanceCriterion : story.acceptanceCriteria()) {
            if (!qualityGateSet.contains(acceptanceCriterion) && hasText(acceptanceCriterion)) {
                return acceptanceCriterion;
            }
        }
        return hasText(story.description()) ? story.description() : story.title();
    }

    private String completionNotes(PrdTaskRecord task) {
        if (task.status() != PrdTaskStatus.COMPLETED) {
            return "";
        }

        for (int index = task.history().size() - 1; index >= 0; index--) {
            String message = task.history().get(index).message();
            if (hasText(message)) {
                return message;
            }
        }
        return "Completed by Ralphy.";
    }

    private List<String> combineAcceptanceCriteria(List<String> storyCriteria, List<String> qualityGates) {
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        if (storyCriteria != null) {
            storyCriteria.stream().filter(this::hasText).map(String::trim).forEach(combined::add);
        }
        if (qualityGates != null) {
            qualityGates.stream().filter(this::hasText).map(String::trim).forEach(combined::add);
        }
        return List.copyOf(combined);
    }

    private List<ParsedStory> parseStories(String userStoriesBody, List<String> qualityGates) {
        Matcher matcher = STORY_HEADING_PATTERN.matcher(userStoriesBody);
        List<StoryMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new StoryMatch(matcher.group(1).trim(), matcher.group(2).trim(), matcher.start(), matcher.end()));
        }

        List<ParsedStory> stories = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            StoryMatch match = matches.get(index);
            int bodyStart = match.endIndex();
            int bodyEnd = index + 1 < matches.size() ? matches.get(index + 1).startIndex() : userStoriesBody.length();
            String storyBody = userStoriesBody.substring(bodyStart, bodyEnd).trim();
            String outcome = extractOutcome(storyBody, match.title());
            stories.add(new ParsedStory(
                    match.id(),
                    match.title(),
                    outcome,
                    extractDescription(storyBody, match.title(), outcome),
                    extractAcceptanceCriteria(storyBody, outcome, qualityGates),
                    extractDependencies(storyBody)
            ));
        }
        return stories;
    }

    private String extractOutcome(String storyBody, String fallback) {
        Matcher matcher = OUTCOME_PATTERN.matcher(storyBody);
        if (matcher.find() && hasText(matcher.group(1))) {
            return matcher.group(1).trim();
        }

        List<String> acceptanceCriteria = extractAcceptanceCriteria(storyBody, fallback, List.of());
        if (!acceptanceCriteria.isEmpty()) {
            return acceptanceCriteria.getFirst();
        }

        Matcher descriptionMatcher = DESCRIPTION_PATTERN.matcher(storyBody);
        if (descriptionMatcher.find() && hasText(descriptionMatcher.group(1))) {
            return descriptionMatcher.group(1).trim();
        }

        for (String line : normalizeLineEndings(storyBody).split("\n")) {
            String trimmedLine = line.trim();
            if (!hasMeaningfulStoryLine(trimmedLine)) {
                continue;
            }
            String normalizedLine = stripLeadingListMarker(trimmedLine);
            if (hasText(normalizedLine)) {
                return normalizedLine;
            }
        }
        return fallback;
    }

    private String extractDescription(String storyBody, String title, String outcome) {
        Matcher matcher = DESCRIPTION_PATTERN.matcher(storyBody);
        if (matcher.find() && hasText(matcher.group(1))) {
            return matcher.group(1).trim();
        }

        for (String line : normalizeLineEndings(storyBody).split("\n")) {
            String normalizedLine = stripLeadingListMarker(line.trim());
            if (normalizedLine.toLowerCase(Locale.ROOT).startsWith("as a ")) {
                return normalizedLine;
            }
        }
        return synthesizeDescription(title, outcome);
    }

    private List<String> extractAcceptanceCriteria(String storyBody, String outcome, List<String> qualityGates) {
        List<String> criteria = new ArrayList<>();
        String currentSubheading = "";
        for (String line : normalizeLineEndings(storyBody).split("\n")) {
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                continue;
            }
            if (trimmedLine.startsWith("####")) {
                currentSubheading = normalizeHeading(trimmedLine.substring(4));
                continue;
            }
            Matcher acceptanceCriteriaMatcher = ACCEPTANCE_CRITERIA_HEADING_PATTERN.matcher(trimmedLine);
            if (acceptanceCriteriaMatcher.matches()) {
                currentSubheading = "acceptance criteria";
                if (hasText(acceptanceCriteriaMatcher.group(1))) {
                    criteria.add(stripLeadingListMarker(acceptanceCriteriaMatcher.group(1).trim()));
                }
                continue;
            }
            if (trimmedLine.matches("^\\*\\*(?:Outcome|Description|Story):\\*\\*.*$")) {
                continue;
            }
            if (DEPENDENCY_LINE_PATTERN.matcher(trimmedLine).matches()) {
                currentSubheading = "dependencies";
                continue;
            }
            String normalizedLine = stripLeadingListMarker(trimmedLine);
            if (!hasText(normalizedLine)) {
                continue;
            }
            if (currentSubheading.equals("dependencies") || currentSubheading.equals("prerequisites")) {
                continue;
            }
            if (currentSubheading.isBlank() && STORY_ID_REFERENCE_PATTERN.matcher(normalizedLine).matches()) {
                continue;
            }
            criteria.add(normalizedLine);
        }

        List<String> combined = combineAcceptanceCriteria(criteria.isEmpty() ? List.of(outcome) : criteria, qualityGates);
        return combined.isEmpty() ? List.of(outcome) : combined;
    }

    private List<String> extractDependencies(String storyBody) {
        LinkedHashSet<String> dependsOn = new LinkedHashSet<>();
        String currentSubheading = "";
        for (String line : normalizeLineEndings(storyBody).split("\n")) {
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                continue;
            }
            if (trimmedLine.startsWith("####")) {
                currentSubheading = normalizeHeading(trimmedLine.substring(4));
                continue;
            }

            Matcher acceptanceCriteriaMatcher = ACCEPTANCE_CRITERIA_HEADING_PATTERN.matcher(trimmedLine);
            if (acceptanceCriteriaMatcher.matches()) {
                currentSubheading = "acceptance criteria";
                continue;
            }
            Matcher dependencyMatcher = DEPENDENCY_LINE_PATTERN.matcher(trimmedLine);
            if (dependencyMatcher.matches()) {
                currentSubheading = "dependencies";
                collectStoryReferences(dependsOn, dependencyValue(dependencyMatcher));
                continue;
            }

            if (currentSubheading.equals("dependencies") || currentSubheading.equals("prerequisites")) {
                collectStoryReferences(dependsOn, trimmedLine);
            }
        }
        return List.copyOf(dependsOn);
    }

    private void collectStoryReferences(Set<String> dependsOn, String rawValue) {
        Matcher matcher = STORY_ID_REFERENCE_PATTERN.matcher(rawValue == null ? "" : rawValue.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            dependsOn.add(matcher.group());
        }
    }

    private String documentTitle(String markdown, String fallback) {
        Matcher matcher = DOCUMENT_TITLE_PATTERN.matcher(markdown);
        if (matcher.find() && hasText(matcher.group(1))) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    private String overviewDescription(Section overviewSection, String fallback) {
        if (overviewSection == null || !hasText(overviewSection.body())) {
            return fallback;
        }

        for (String line : normalizeLineEndings(overviewSection.body()).split("\n")) {
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine) || trimmedLine.startsWith("###")) {
                continue;
            }
            return stripLeadingListMarker(trimmedLine);
        }
        return fallback;
    }

    private List<Section> sections(String markdown) {
        Matcher matcher = SECTION_HEADING_PATTERN.matcher(markdown);
        List<SectionMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new SectionMatch(sectionTitle(matcher), matcher.start(), matcher.end()));
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

    private String synthesizeDescription(String title, String outcome) {
        if (!hasText(outcome) || outcome.equals(title)) {
            return "As a user, I want " + title + ".";
        }
        return "As a user, I want " + title + " so that " + outcome + ".";
    }

    private String slugify(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.isBlank() ? "prd-export" : normalized;
    }

    private String normalizeHeading(String value) {
        return value == null ? "" : value.trim().replaceAll(":+$", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String stripLeadingListMarker(String value) {
        if (!hasText(value)) {
            return "";
        }
        return LEADING_LIST_MARKER_PATTERN.matcher(value).replaceFirst("").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String dependencyValue(Matcher matcher) {
        if (matcher == null) {
            return "";
        }
        String inlineBoldValue = matcher.group(1);
        if (hasText(inlineBoldValue)) {
            return inlineBoldValue.trim();
        }
        String standardValue = matcher.group(2);
        return standardValue == null ? "" : standardValue.trim();
    }

    private boolean hasMeaningfulStoryLine(String trimmedLine) {
        if (!hasText(trimmedLine)) {
            return false;
        }
        if (trimmedLine.startsWith("####")) {
            return false;
        }
        if (OUTCOME_PATTERN.matcher(trimmedLine).matches()) {
            return false;
        }
        if (DESCRIPTION_PATTERN.matcher(trimmedLine).matches()) {
            return false;
        }
        if (DEPENDENCY_LINE_PATTERN.matcher(trimmedLine).matches()) {
            return false;
        }
        return !ACCEPTANCE_CRITERIA_HEADING_PATTERN.matcher(trimmedLine).matches();
    }

    private String sectionTitle(Matcher matcher) {
        String markdownHeading = matcher.group(1);
        if (hasText(markdownHeading)) {
            return markdownHeading.trim();
        }
        String boldHeading = matcher.group(2);
        return boldHeading == null ? "" : boldHeading.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record Section(String title, String body) {
    }

    private record SectionMatch(String title, int startIndex, int endIndex) {
    }

    private record StoryMatch(String id, String title, int startIndex, int endIndex) {
    }

    private record ParsedStory(String id,
                               String title,
                               String outcome,
                               String description,
                               List<String> acceptanceCriteria,
                               List<String> dependsOn) {
    }
}
