package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdStructureValidatorTest {
    private final PrdStructureValidator validator = new PrdStructureValidator();

    @Test
    void validateAcceptsPrdsWithRequiredSectionsQualityGatesAndStoryHeaders() {
        PrdValidationReport report = validator.validate("""
                # PRD: Validated execution

                ## Overview
                Validate active PRDs before the execution loop starts.

                ## Goals
                - Block malformed PRDs before execution

                ## Quality Gates
                - .\\mvnw.cmd clean verify jacoco:report
                - Automated JavaFX UI tests

                ## User Stories
                ### US-020: Validate PRD structure before execution
                **Outcome:** Malformed task definitions stay out of the execution loop.

                ## Scope Boundaries
                ### In Scope
                - Structural PRD validation

                ### Out of Scope
                - Codex launch orchestration
                """);

        assertTrue(report.valid());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void validateReportsMissingSectionsAndMalformedStoryHeadersWithSpecificLocations() {
        PrdValidationReport report = validator.validate("""
                # PRD: Broken execution plan

                ## Overview
                Missing key sections and malformed stories.

                ## User Stories
                ### Story 1: Missing US identifier
                **Outcome:** This cannot be executed safely.

                ## Scope Boundaries
                ### In Scope
                - Structural validation

                ### Out of Scope
                - Runtime orchestration
                """);

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error ->
                error.location().equals("Section Goals")
                        && error.message().contains("Missing required heading")));
        assertTrue(report.errors().stream().anyMatch(error ->
                error.location().equals("Section Quality Gates")
                        && error.message().contains("Missing required heading")));
        assertTrue(report.errors().stream().anyMatch(error ->
                error.location().contains("Story heading `### Story 1: Missing US identifier`")
                        && error.message().contains("### US-XXX: Story title")));
    }
}
