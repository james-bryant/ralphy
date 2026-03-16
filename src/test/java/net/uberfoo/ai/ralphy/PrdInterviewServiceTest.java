package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrdInterviewServiceTest {
    private final PrdInterviewService prdInterviewService = new PrdInterviewService();

    @Test
    void interviewCatalogCoversRequiredPrdSectionsInStableOrder() {
        assertEquals(7, prdInterviewService.questionCount());
        assertEquals("overviewContext", prdInterviewService.questionForIndex(0).questionId());
        assertEquals("scopeOut", prdInterviewService.questionForIndex(6).questionId());

        Set<String> sectionIds = prdInterviewService.questions().stream()
                .map(PrdInterviewQuestion::sectionId)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(sectionIds.contains("overview"));
        assertTrue(sectionIds.contains("goals"));
        assertTrue(sectionIds.contains("quality-gates"));
        assertTrue(sectionIds.contains("user-stories"));
        assertTrue(sectionIds.contains("scope-boundaries"));
    }
}
