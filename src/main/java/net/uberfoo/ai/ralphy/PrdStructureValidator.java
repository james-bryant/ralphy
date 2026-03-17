package net.uberfoo.ai.ralphy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrdStructureValidator {
    private static final List<String> REQUIRED_SECTION_TITLES = List.of(
            "Overview",
            "Goals",
            "Quality Gates",
            "User Stories",
            "Scope Boundaries"
    );
    private static final String FLEXIBLE_SECTION_TITLES =
            "Overview|Introduction|Goals|Quality Gates|User Stories|Scope Boundaries|Functional Requirements"
                    + "|Technical Considerations|Success Metrics|Open Questions|Non-Goals|Non Goals";
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile(
            "(?im)^(?:##\\s+(?!US-\\d{3}:)(.+?)\\s*$|\\*\\*((" + FLEXIBLE_SECTION_TITLES + "):?)\\*\\*\\s*$)"
    );
    private static final Pattern STORY_HEADING_PATTERN = Pattern.compile(
            "(?m)^(?:##|###)\\s+(US-\\d{3}):\\s+(.+?)\\s*$"
    );
    private static final Pattern STORY_HEADING_CANDIDATE_PATTERN = Pattern.compile(
            "(?m)^(?:##|###)\\s+.+$"
    );
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("(?im)^\\*\\*(?:Description|Story):\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern OUTCOME_PATTERN =
            Pattern.compile("(?im)^\\*\\*Outcome:\\*\\*\\s*(.+?)\\s*$");
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "(?im)^(?:\\*\\*(?:Dependencies|Prerequisites?|Depends\\s+On):\\*\\*\\s*.+$"
                    + "|(?:\\*\\*(?:Dependencies|Prerequisites?|Depends\\s+On)\\*\\*|Dependencies|Prerequisites?|Depends\\s+On)\\s*:\\s*.+$)"
    );
    private static final Pattern ACCEPTANCE_CRITERIA_HEADING_PATTERN = Pattern.compile(
            "(?im)^\\*\\*Acceptance Criteria:\\*\\*\\s*(.+?)?\\s*$|^####\\s+Acceptance Criteria\\s*$"
    );
    private static final Pattern ACCEPTANCE_CRITERIA_ITEM_PATTERN = Pattern.compile(
            "(?m)^(?:[-*+]\\s+|\\d+[.)]\\s+|\\[[ xX]\\]\\s+).+$"
    );

    public PrdValidationReport validate(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return PrdValidationReport.failure(List.of(new PrdValidationError(
                    "Active PRD",
                    "Generate or save `.ralph-tui/prds/active-prd.md` before execution."
            )));
        }

        String normalizedMarkdown = normalizeLineEndings(markdown);
        List<Section> sections = sections(normalizedMarkdown);
        List<PrdValidationError> errors = new ArrayList<>();

        for (String requiredSectionTitle : REQUIRED_SECTION_TITLES) {
            if (findSection(sections, requiredSectionTitle) == null) {
                errors.add(new PrdValidationError(
                        "Section " + requiredSectionTitle,
                        "Missing required section heading for `" + requiredSectionTitle
                                + "`. Prefer `## " + requiredSectionTitle + "`."
                ));
            }
        }

        Section qualityGatesSection = findSection(sections, "Quality Gates");
        if (qualityGatesSection != null && qualityGatesSection.body().isBlank()) {
            errors.add(new PrdValidationError(
                    "Section Quality Gates",
                    "Add at least one quality gate entry beneath `## Quality Gates`."
            ));
        }

        Section userStoriesSection = findSection(sections, "User Stories");
        if (userStoriesSection != null) {
            validateUserStories(userStoriesSection, errors);
        }

        if (errors.isEmpty()) {
            return PrdValidationReport.empty();
        }
        return PrdValidationReport.failure(errors);
    }

    private void validateUserStories(Section userStoriesSection, List<PrdValidationError> errors) {
        Matcher storyHeadingCandidateMatcher = STORY_HEADING_CANDIDATE_PATTERN.matcher(userStoriesSection.body());
        while (storyHeadingCandidateMatcher.find()) {
            String candidateLine = storyHeadingCandidateMatcher.group().trim();
            if (STORY_HEADING_PATTERN.matcher(candidateLine).matches()) {
                continue;
            }
            errors.add(new PrdValidationError(
                    "Story heading `" + candidateLine + "`",
                    "Expected `## US-XXX: Story title` or `### US-XXX: Story title`."
            ));
        }

        List<StoryBlock> storyBlocks = storyBlocks(userStoriesSection.body());
        if (storyBlocks.isEmpty()) {
            errors.add(new PrdValidationError(
                    "Section User Stories",
                    "Add at least one story heading in the format `## US-XXX: Story title` or `### US-XXX: Story title`."
            ));
            return;
        }

        for (StoryBlock storyBlock : storyBlocks) {
            validateStoryBlock(storyBlock, errors);
        }
    }

    private void validateStoryBlock(StoryBlock storyBlock, List<PrdValidationError> errors) {
        boolean hasDescription = DESCRIPTION_PATTERN.matcher(storyBlock.body()).find();
        boolean hasOutcome = OUTCOME_PATTERN.matcher(storyBlock.body()).find();
        boolean hasDependencies = DEPENDENCY_PATTERN.matcher(storyBlock.body()).find();
        boolean hasAcceptanceCriteriaHeading = ACCEPTANCE_CRITERIA_HEADING_PATTERN.matcher(storyBlock.body()).find();
        boolean hasAcceptanceCriteriaItems = ACCEPTANCE_CRITERIA_ITEM_PATTERN.matcher(storyBlock.body()).find();

        if (!hasDescription && !hasOutcome) {
            errors.add(new PrdValidationError(
                    storyBlock.reference(),
                    "Add `**Description:** ...` or legacy `**Outcome:** ...` beneath the story heading."
            ));
        }
        if (!hasDependencies && !hasOutcome) {
            errors.add(new PrdValidationError(
                    storyBlock.reference(),
                    "Add `**Dependencies:** None.` or list the dependent story IDs."
            ));
        }
        if (!hasAcceptanceCriteriaHeading && !hasOutcome) {
            errors.add(new PrdValidationError(
                    storyBlock.reference(),
                    "Add an `**Acceptance Criteria:**` block with at least one checklist or bullet item."
            ));
        } else if (hasAcceptanceCriteriaHeading && !hasAcceptanceCriteriaItems) {
            errors.add(new PrdValidationError(
                    storyBlock.reference(),
                    "Acceptance criteria must include at least one checklist or bullet item."
            ));
        }
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

    private List<StoryBlock> storyBlocks(String markdown) {
        Matcher matcher = STORY_HEADING_PATTERN.matcher(markdown);
        List<StoryMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new StoryMatch(
                    matcher.group(1).trim(),
                    matcher.group(2).trim(),
                    matcher.start(),
                    matcher.end()
            ));
        }

        List<StoryBlock> storyBlocks = new ArrayList<>(matches.size());
        for (int index = 0; index < matches.size(); index++) {
            StoryMatch match = matches.get(index);
            int bodyStart = match.endIndex();
            int bodyEnd = index + 1 < matches.size() ? matches.get(index + 1).startIndex() : markdown.length();
            storyBlocks.add(new StoryBlock(
                    match.storyId(),
                    match.title(),
                    markdown.substring(bodyStart, bodyEnd).trim()
            ));
        }
        return storyBlocks;
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

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sectionTitle(Matcher matcher) {
        String markdownHeading = matcher.group(1);
        if (markdownHeading != null && !markdownHeading.isBlank()) {
            return markdownHeading.trim();
        }
        String boldHeading = matcher.group(2);
        return boldHeading == null ? "" : boldHeading.trim();
    }

    private record Section(String title, String body) {
    }

    private record SectionMatch(String title, int startIndex, int endIndex) {
    }

    private record StoryMatch(String storyId, String title, int startIndex, int endIndex) {
    }

    private record StoryBlock(String storyId, String title, String body) {
        private String reference() {
            return storyId + ": " + title;
        }
    }
}
