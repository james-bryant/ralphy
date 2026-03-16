package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalphPrdJsonCompatibilityValidatorTest {
    private final RalphPrdJsonCompatibilityValidator validator = new RalphPrdJsonCompatibilityValidator();

    @Test
    void validateAcceptsFlatRalphCompatibleDocuments() {
        RalphPrdJsonCompatibilityValidator.ValidationReport report = validator.validate("""
                {
                  "name": "Ralphy Export",
                  "branchName": "ralph/ralphy-export",
                  "description": "Exports active PRD state.",
                  "qualityGates": [
                    ".\\\\mvnw.cmd clean verify jacoco:report passes"
                  ],
                  "userStories": [
                    {
                      "id": "US-023",
                      "title": "Export Ralph-compatible prd.json",
                      "description": "As a user, I want to export prd.json so that the tracker can read it.",
                      "acceptanceCriteria": [
                        "The tracker-compatible file is written.",
                        ".\\\\mvnw.cmd clean verify jacoco:report passes"
                      ],
                      "priority": 1,
                      "passes": false,
                      "dependsOn": [
                        "US-022"
                      ],
                      "completionNotes": "",
                      "ralphyStatus": "READY"
                    }
                  ]
                }
                """);

        assertTrue(report.valid());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void validateRejectsWrapperObjectsAndLegacyStatusFields() {
        RalphPrdJsonCompatibilityValidator.ValidationReport report = validator.validate("""
                {
                  "prd": {
                    "name": "Wrapped export"
                  },
                  "userStories": [
                    {
                      "id": "US-023",
                      "title": "Export prd json",
                      "description": "Wrapped incorrectly.",
                      "acceptanceCriteria": [
                        "Write the file"
                      ],
                      "priority": 1,
                      "passes": false,
                      "status": "open",
                      "dependsOn": []
                    }
                  ]
                }
                """);

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("flat object")));
        assertTrue(report.errors().stream().anyMatch(error -> error.contains("status")));
    }
}
