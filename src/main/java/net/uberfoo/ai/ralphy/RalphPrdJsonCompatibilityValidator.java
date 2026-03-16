package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RalphPrdJsonCompatibilityValidator {
    private static final Pattern STORY_ID_PATTERN = Pattern.compile("US-\\d{3}");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ValidationReport validate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ValidationReport(false, List.of("Exported prd.json must not be blank."));
        }

        final JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(rawJson);
        } catch (IOException exception) {
            return new ValidationReport(false, List.of("Exported prd.json is not valid JSON: " + exception.getMessage()));
        }

        return validate(rootNode);
    }

    ValidationReport validate(JsonNode rootNode) {
        List<String> errors = new ArrayList<>();
        if (rootNode == null || !rootNode.isObject()) {
            return new ValidationReport(false, List.of("The export must be a flat object at the root."));
        }

        if (rootNode.has("prd")) {
            errors.add("The export must stay a flat object at the root and must not use a `prd` wrapper.");
        }
        if (rootNode.has("tasks")) {
            errors.add("The export must use `userStories` at the root instead of `tasks`.");
        }
        if (!hasText(rootNode.path("name"))) {
            errors.add("The export must include a non-empty root `name`.");
        }
        if (!hasText(rootNode.path("branchName"))) {
            errors.add("The export must include a non-empty root `branchName`.");
        }
        if (!hasText(rootNode.path("description"))) {
            errors.add("The export must include a non-empty root `description`.");
        }

        JsonNode userStoriesNode = rootNode.path("userStories");
        if (!userStoriesNode.isArray() || userStoriesNode.isEmpty()) {
            errors.add("The export must include a non-empty `userStories` array.");
        } else {
            for (int index = 0; index < userStoriesNode.size(); index++) {
                validateStory(userStoriesNode.get(index), index, errors);
            }
        }

        return new ValidationReport(errors.isEmpty(), errors);
    }

    private void validateStory(JsonNode storyNode, int index, List<String> errors) {
        String location = "userStories[" + index + "]";
        if (!storyNode.isObject()) {
            errors.add(location + " must be an object.");
            return;
        }

        if (storyNode.has("status")) {
            errors.add(location + " must not use the legacy `status` field; use `passes` instead.");
        }
        if (!hasText(storyNode.path("id")) || !STORY_ID_PATTERN.matcher(storyNode.path("id").asText()).matches()) {
            errors.add(location + " must include an `id` in `US-XXX` format.");
        }
        if (!hasText(storyNode.path("title"))) {
            errors.add(location + " must include a non-empty `title`.");
        }
        if (!hasText(storyNode.path("description"))) {
            errors.add(location + " must include a non-empty `description`.");
        }
        JsonNode acceptanceCriteriaNode = storyNode.path("acceptanceCriteria");
        if (!acceptanceCriteriaNode.isArray() || acceptanceCriteriaNode.isEmpty()) {
            errors.add(location + " must include a non-empty `acceptanceCriteria` array.");
        }
        if (!storyNode.path("priority").canConvertToInt() || storyNode.path("priority").asInt() <= 0) {
            errors.add(location + " must include a positive integer `priority`.");
        }
        if (!storyNode.has("passes") || !storyNode.path("passes").isBoolean()) {
            errors.add(location + " must include boolean `passes`.");
        }
        if (!storyNode.path("dependsOn").isArray()) {
            errors.add(location + " must include array `dependsOn`.");
        }
    }

    private boolean hasText(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank();
    }

    public record ValidationReport(boolean valid, List<String> errors) {
        public ValidationReport {
            errors = List.copyOf(errors);
        }
    }
}
