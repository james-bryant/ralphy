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
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final Pattern STORY_HEADING_PATTERN = Pattern.compile("^###\\s+US-\\d{3}:\\s+.+$");

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
                        "Missing required heading `## " + requiredSectionTitle + "`."
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
        String[] lines = userStoriesSection.body().split("\n");
        boolean sawStoryHeading = false;

        for (String line : lines) {
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("###")) {
                continue;
            }

            sawStoryHeading = true;
            if (!STORY_HEADING_PATTERN.matcher(normalizedLine).matches()) {
                errors.add(new PrdValidationError(
                        "Story heading `" + normalizedLine + "`",
                        "Expected the format `### US-XXX: Story title`."
                ));
            }
        }

        if (!sawStoryHeading) {
            errors.add(new PrdValidationError(
                    "Section User Stories",
                    "Add at least one story heading in the format `### US-XXX: Story title`."
            ));
        }
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

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private record Section(String title, String body) {
    }

    private record SectionMatch(String title, int startIndex, int endIndex) {
    }
}
