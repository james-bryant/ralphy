package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PrdPlannerService {
    private static final String PLANNER_STORY_ID = "PRD-PLANNER";
    private static final Pattern PRD_BLOCK_PATTERN = Pattern.compile("(?is)\\[PRD](.*?)\\[/PRD]");

    private final CodexLauncherService codexLauncherService;
    private final PresetCatalogService presetCatalogService;
    private final PlannerExecutor plannerExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PrdPlannerService(CodexLauncherService codexLauncherService,
                             PresetCatalogService presetCatalogService) {
        this(codexLauncherService,
                presetCatalogService,
                new SystemPlannerExecutor(HostOperatingSystem.detectRuntime()),
                HostOperatingSystem.detectRuntime());
    }

    PrdPlannerService(CodexLauncherService codexLauncherService,
                      PresetCatalogService presetCatalogService,
                      PlannerExecutor plannerExecutor) {
        this(codexLauncherService, presetCatalogService, plannerExecutor, HostOperatingSystem.detectRuntime());
    }

    PrdPlannerService(CodexLauncherService codexLauncherService,
                      PresetCatalogService presetCatalogService,
                      PlannerExecutor plannerExecutor,
                      HostOperatingSystem hostOperatingSystem) {
        this.codexLauncherService = Objects.requireNonNull(codexLauncherService,
                "codexLauncherService must not be null");
        this.presetCatalogService = Objects.requireNonNull(presetCatalogService,
                "presetCatalogService must not be null");
        this.plannerExecutor = Objects.requireNonNull(plannerExecutor, "plannerExecutor must not be null");
    }

    public PlannerTurnResult continueConversation(ActiveProject activeProject,
                                                  ExecutionProfile executionProfile,
                                                  PrdPlanningSession session,
                                                  String userMessage,
                                                  CodexLauncherService.RunOutputListener runOutputListener) {
        return continueConversation(
                activeProject,
                executionProfile,
                session,
                userMessage,
                ExecutionAgentSelection.codexDefault(),
                runOutputListener
        );
    }

    public PlannerTurnResult continueConversation(ActiveProject activeProject,
                                                  ExecutionProfile executionProfile,
                                                  PrdPlanningSession session,
                                                  String userMessage,
                                                  String modelId,
                                                  CodexLauncherService.RunOutputListener runOutputListener) {
        return continueConversation(
                activeProject,
                executionProfile,
                session,
                userMessage,
                new ExecutionAgentSelection(ExecutionAgentProvider.CODEX, modelId, ""),
                runOutputListener
        );
    }

    public PlannerTurnResult continueConversation(ActiveProject activeProject,
                                                  ExecutionProfile executionProfile,
                                                  PrdPlanningSession session,
                                                  String userMessage,
                                                  ExecutionAgentSelection executionAgentSelection,
                                                  CodexLauncherService.RunOutputListener runOutputListener) {
        Objects.requireNonNull(activeProject, "activeProject must not be null");
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");
        ExecutionAgentSelection resolvedAgentSelection = executionAgentSelection == null
                ? ExecutionAgentSelection.codexDefault()
                : executionAgentSelection;
        if (!hasText(userMessage)) {
            return PlannerTurnResult.failure(session == null ? PrdPlanningSession.empty() : session,
                    "Enter a prompt or clarification before sending it to the planner.");
        }

        PrdPlanningSession baseSession = session == null ? PrdPlanningSession.empty() : session;
        PrdPlanningSession sessionWithUserTurn = ensureUserTurn(baseSession, userMessage);
        BuiltInPreset preset = presetCatalogService.defaultPreset(PresetUseCase.PRD_CREATION);
        CodexLauncherService.CodexLaunchRequest launchRequest = new CodexLauncherService.CodexLaunchRequest(
                PLANNER_STORY_ID,
                activeProject,
                executionProfile,
                preset,
                plannerPromptInputs(activeProject, sessionWithUserTurn),
                plannerInstructions(sessionWithUserTurn),
                resolvedAgentSelection,
                plannerCliOptions(resolvedAgentSelection)
        );
        CodexLauncherService.CodexLaunchPlan launchPlan = codexLauncherService.buildLaunch(launchRequest);
        CodexLauncherService.RunOutputListener listener =
                runOutputListener == null ? CodexLauncherService.RunOutputListener.noop() : runOutputListener;
        PlannerExecution execution = plannerExecutor.execute(launchPlan, listener);
        if (!execution.successful()) {
            return PlannerTurnResult.failure(sessionWithUserTurn, plannerFailureMessage(execution));
        }

        String assistantMessage = resolvedAgentSelection.provider() == ExecutionAgentProvider.GITHUB_COPILOT
                ? normalizeSummaryText(execution.stdout())
                : extractFinalAssistantSummary(execution.stdout());
        if (!hasText(assistantMessage)) {
            assistantMessage = extractTrailingPlainTextBlock(execution.stdout());
        }
        if (!hasText(assistantMessage)) {
            return PlannerTurnResult.failure(
                    sessionWithUserTurn,
                    "The planner completed without returning a usable clarification response."
            );
        }

        String latestPrdMarkdown = firstNonBlank(
                extractPrdCandidate(assistantMessage),
                extractPrdCandidate(execution.stdout()),
                sessionWithUserTurn.latestPrdMarkdown()
        );
        PrdPlanningSession updatedSession =
                sessionWithUserTurn.appendAssistantMessage(assistantMessage, latestPrdMarkdown);
        return PlannerTurnResult.success(updatedSession, assistantMessage, latestPrdMarkdown);
    }

    private List<String> plannerCliOptions(ExecutionAgentSelection executionAgentSelection) {
        return ExecutionAgentCliOptions.build(executionAgentSelection);
    }

    private List<CodexLauncherService.PromptInput> plannerPromptInputs(ActiveProject activeProject,
                                                                       PrdPlanningSession session) {
        List<CodexLauncherService.PromptInput> promptInputs = new ArrayList<>();
        promptInputs.add(new CodexLauncherService.PromptInput(
                "Repository",
                activeProject.displayName() + System.lineSeparator() + activeProject.displayPath()
        ));
        promptInputs.add(new CodexLauncherService.PromptInput("Starter Prompt", session.starterPrompt()));
        promptInputs.add(new CodexLauncherService.PromptInput(
                "Conversation Transcript",
                renderConversationTranscript(session.messages())
        ));
        if (session.hasLatestPrdMarkdown()) {
            promptInputs.add(new CodexLauncherService.PromptInput(
                    "Latest Draft Candidate",
                    session.latestPrdMarkdown()
            ));
        }
        return List.copyOf(promptInputs);
    }

    private String plannerInstructions(PrdPlanningSession session) {
        return """
                Continue the repository PRD planning conversation.
                Respond as the assistant's next conversational turn only.
                Work in iterative planning mode similar to a live create-prd chat flow.
                Always confirm the required quality gates before finalizing the PRD.
                Prioritize clarifying the problem, goals, target users, scope boundaries, integration points, success metrics, and repository-specific constraints.
                When you produce the PRD, use canonical markdown headings such as `## Overview`, `## Goals`, `## Quality Gates`, `## User Stories`, `## Scope Boundaries`, `## Functional Requirements`, `## Technical Considerations`, `## Success Metrics`, and `## Open Questions`.
                Under `## User Stories`, format each story exactly as `### US-001: Story Title`.
                For every story, include `**Description:**`, `**Dependencies:**`, and `**Acceptance Criteria:**` with one or more checklist items.
                Use `**Dependencies:** None.` when a story has no prerequisite stories.
                Format `## Scope Boundaries` with `### In Scope` and `### Out of Scope`.
                Make each story independently executable and detailed enough to convert directly into `prd.json` task records.
                Keep the response concise, repository-aware, and focused on planning rather than implementation.
                """
                + System.lineSeparator()
                + System.lineSeparator()
                + stageInstructions(session);
    }

    private String stageInstructions(PrdPlanningSession session) {
        if (session != null && session.hasLatestPrdMarkdown()) {
            return """
                    A PRD draft already exists in the conversation.
                    Revise the draft now and return the updated PRD wrapped in [PRD] and [/PRD] in this turn.
                    Do not repeat earlier clarification questions unless the user's latest message reveals a critical blocker.
                    Record remaining uncertainty in Open Questions instead of delaying the draft.
                    """;
        }

        if (shouldFinalizePrd(session)) {
            return """
                    The conversation already has enough context to finalize the PRD.
                    Do not ask another clarification round unless a critical blocker remains.
                    Return the full PRD wrapped in [PRD] and [/PRD] in this turn.
                    If any detail is still uncertain, capture it in Open Questions rather than asking the user to answer more questions first.
                    """;
        }

        return """
                If more context is needed, ask the next 3-5 clarifying questions with numbered questions and lettered options where helpful.
                Once the key planning gaps are closed, stop asking questions and return the PRD wrapped in [PRD] and [/PRD].
                """;
    }

    private String renderConversationTranscript(List<PrdPlanningSession.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder transcript = new StringBuilder();
        for (PrdPlanningSession.Message message : messages) {
            if (!hasText(message.content())) {
                continue;
            }
            transcript.append(capitalize(message.role()))
                    .append(": ")
                    .append(message.content().trim())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return transcript.toString().trim();
    }

    private String plannerFailureMessage(PlannerExecution execution) {
        if (hasText(execution.failureMessage())) {
            return execution.failureMessage();
        }
        if (hasText(execution.stderr())) {
            return execution.stderr().trim();
        }
        if (hasText(execution.stdout())) {
            return extractTrailingPlainTextBlock(execution.stdout());
        }
        if (execution.exitCode() != null) {
            return "Planner exited with code " + execution.exitCode() + ".";
        }
        return "Planner execution failed.";
    }

    private String extractPrdBlock(String text) {
        if (!hasText(text)) {
            return "";
        }

        Matcher matcher = PRD_BLOCK_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return normalizeSummaryText(matcher.group(1));
    }

    private String extractPrdCandidate(String text) {
        String prdBlock = extractPrdBlock(text);
        if (hasText(prdBlock)) {
            return prdBlock;
        }

        String normalizedText = normalizeSummaryText(text);
        if (!hasText(normalizedText)) {
            return "";
        }

        int prdHeadingIndex = normalizedText.toLowerCase().indexOf("# prd");
        String candidate = prdHeadingIndex >= 0
                ? normalizedText.substring(prdHeadingIndex).trim()
                : normalizedText;
        return looksLikePrd(candidate) ? candidate : "";
    }

    private String extractFinalAssistantSummary(String stdout) {
        if (!hasText(stdout)) {
            return "";
        }

        List<String> completeCandidates = new ArrayList<>();
        StringBuilder deltaCandidate = new StringBuilder();
        for (String line : stdout.lines().toList()) {
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                continue;
            }

            JsonNode eventNode;
            try {
                eventNode = objectMapper.readTree(trimmedLine);
            } catch (IOException ignored) {
                continue;
            }

            String completeCandidate = extractCompleteAssistantText(eventNode);
            if (hasText(completeCandidate)) {
                completeCandidates.add(completeCandidate.trim());
            }

            String deltaText = extractAssistantDeltaText(eventNode);
            if (hasText(deltaText)) {
                deltaCandidate.append(deltaText);
            }
        }

        if (!completeCandidates.isEmpty()) {
            return normalizeSummaryText(completeCandidates.get(completeCandidates.size() - 1));
        }

        String deltaSummary = normalizeSummaryText(deltaCandidate.toString());
        if (hasText(deltaSummary)) {
            return deltaSummary;
        }

        return extractTrailingPlainTextBlock(stdout);
    }

    private String extractCompleteAssistantText(JsonNode rootNode) {
        List<String> candidates = new ArrayList<>();
        collectAssistantTextCandidates(rootNode, false, false, candidates, 0);
        return candidates.isEmpty() ? "" : candidates.get(candidates.size() - 1);
    }

    private String extractAssistantDeltaText(JsonNode rootNode) {
        List<String> deltaCandidates = new ArrayList<>();
        collectAssistantTextCandidates(rootNode, false, true, deltaCandidates, 0);
        if (deltaCandidates.isEmpty()) {
            return "";
        }
        return String.join("", deltaCandidates);
    }

    private void collectAssistantTextCandidates(JsonNode node,
                                                boolean assistantContext,
                                                boolean deltaOnly,
                                                List<String> candidates,
                                                int depth) {
        if (node == null || node.isNull() || depth > 8) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode childNode : node) {
                collectAssistantTextCandidates(childNode, assistantContext, deltaOnly, candidates, depth + 1);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        if (!deltaOnly) {
            addCandidate(candidates, readPreferredText(node, List.of("assistant_summary", "final_summary")));
        }

        boolean currentAssistantContext = assistantContextForNode(node, assistantContext);
        if (currentAssistantContext) {
            if (deltaOnly) {
                addCandidate(candidates, readPreferredText(node, List.of("delta", "text_delta", "output_text_delta")));
            } else {
                addCandidate(candidates, readPreferredText(node, List.of(
                        "summary",
                        "output_text",
                        "text",
                        "message"
                )));
                addCandidate(candidates, extractContentText(node.path("content")));
                addCandidate(candidates, extractContentText(node.path("output")));
            }
        }

        node.fields().forEachRemaining(entry ->
                collectAssistantTextCandidates(entry.getValue(), currentAssistantContext, deltaOnly, candidates, depth + 1)
        );
    }

    private boolean assistantContextForNode(JsonNode node, boolean assistantContext) {
        String explicitRole = explicitRole(node);
        if (isAssistantRole(explicitRole)) {
            return true;
        }
        if (hasText(explicitRole)) {
            return false;
        }

        String type = node.path("type").asText("");
        String event = node.path("event").asText("");
        if (isKnownNonAssistantEventType(type) || isKnownNonAssistantEventType(event)) {
            return false;
        }

        return assistantContext
                || isAssistantEventType(type)
                || isAssistantEventType(event)
                || isResponseEventType(type)
                || isResponseEventType(event);
    }

    private String explicitRole(JsonNode node) {
        return firstNonBlank(
                node.path("role").asText(""),
                node.path("author").path("role").asText(""),
                node.path("sender").path("role").asText(""),
                node.path("message").path("role").asText(""),
                node.path("item").path("role").asText("")
        );
    }

    private boolean isAssistantRole(String role) {
        return "assistant".equalsIgnoreCase(role);
    }

    private boolean isAssistantEventType(String value) {
        if (!hasText(value)) {
            return false;
        }

        String normalizedValue = value.toLowerCase();
        return normalizedValue.contains("assistant") || normalizedValue.contains("agent_message");
    }

    private boolean isResponseEventType(String value) {
        return hasText(value) && value.toLowerCase().startsWith("response");
    }

    private boolean isKnownNonAssistantEventType(String value) {
        if (!hasText(value)) {
            return false;
        }

        String normalizedValue = value.toLowerCase();
        return normalizedValue.contains("user")
                || normalizedValue.contains("command_execution")
                || normalizedValue.contains("reasoning")
                || normalizedValue.contains("todo_list")
                || normalizedValue.contains("web_search")
                || normalizedValue.contains("file_change")
                || normalizedValue.contains("tool");
    }

    private String readPreferredText(JsonNode node, List<String> preferredFields) {
        for (String fieldName : preferredFields) {
            if (!node.has(fieldName)) {
                continue;
            }

            String extractedText = extractContentText(node.get(fieldName));
            if (hasText(extractedText)) {
                return extractedText;
            }
        }
        return "";
    }

    private String extractContentText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode childNode : node) {
                addCandidate(parts, extractContentText(childNode));
            }
            return String.join(System.lineSeparator(), parts);
        }

        if (!node.isObject()) {
            return "";
        }

        String directText = readPreferredText(node, List.of("text", "value", "message", "content", "output_text"));
        if (hasText(directText)) {
            return directText;
        }

        List<String> nestedParts = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey().toLowerCase();
            if ("type".equals(fieldName)
                    || "event".equals(fieldName)
                    || "role".equals(fieldName)
                    || "id".equals(fieldName)
                    || "status".equals(fieldName)) {
                return;
            }
            addCandidate(nestedParts, extractContentText(entry.getValue()));
        });
        return String.join(System.lineSeparator(), nestedParts);
    }

    private void addCandidate(List<String> candidates, String value) {
        if (hasText(value)) {
            candidates.add(value.trim());
        }
    }

    private String normalizeSummaryText(String value) {
        if (!hasText(value)) {
            return "";
        }

        return value
                .replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String extractTrailingPlainTextBlock(String stdout) {
        List<String> trailingLines = new ArrayList<>();
        List<String> lines = stdout.lines().toList();
        for (int index = lines.size() - 1; index >= 0; index--) {
            String line = lines.get(index);
            String trimmedLine = line.trim();
            if (!hasText(trimmedLine)) {
                if (!trailingLines.isEmpty()) {
                    break;
                }
                continue;
            }

            if (isStructuredEventLine(trimmedLine)) {
                if (!trailingLines.isEmpty()) {
                    break;
                }
                continue;
            }

            trailingLines.add(0, line);
        }

        return normalizeSummaryText(String.join(System.lineSeparator(), trailingLines));
    }

    private boolean isStructuredEventLine(String line) {
        try {
            return objectMapper.readTree(line).isContainerNode();
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String capitalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private PrdPlanningSession ensureUserTurn(PrdPlanningSession session, String userMessage) {
        if (session == null) {
            return PrdPlanningSession.empty().appendUserMessage(userMessage);
        }

        String normalizedUserMessage = userMessage == null ? "" : userMessage.trim();
        List<PrdPlanningSession.Message> messages = session.messages();
        if (!messages.isEmpty()) {
            PrdPlanningSession.Message lastMessage = messages.get(messages.size() - 1);
            if ("user".equals(lastMessage.role()) && normalizedUserMessage.equals(lastMessage.content())) {
                return session;
            }
        }
        return session.appendUserMessage(userMessage);
    }

    private boolean shouldFinalizePrd(PrdPlanningSession session) {
        if (session == null || !session.hasMessages()) {
            return false;
        }

        String transcript = renderConversationTranscript(session.messages()).toLowerCase();
        int coveredTopics = 0;
        if (containsAny(transcript, "quality gate", "quality gates", "must pass", "verify")) {
            coveredTopics++;
        }
        if (containsAny(transcript, "goal", "goals", "outcome", "success")) {
            coveredTopics++;
        }
        if (containsAny(transcript, "scope", "out of scope", "non-goal", "non goal", "boundary")) {
            coveredTopics++;
        }
        if (containsAny(transcript, "user story", "user stories", "target user", "persona", "who is this for")) {
            coveredTopics++;
        }
        if (containsAny(transcript, "integration", "existing", "repository", "fit with")) {
            coveredTopics++;
        }
        return coveredTopics >= 4 || session.messages().size() >= 6;
    }

    private boolean looksLikePrd(String text) {
        if (!hasText(text)) {
            return false;
        }

        int matchedSections = 0;
        if (containsAny(text, "# prd", "## overview", "## introduction", "**overview**", "**introduction**")) {
            matchedSections++;
        }
        if (containsAny(text, "## goals", "**goals**")) {
            matchedSections++;
        }
        if (containsAny(text, "## quality gates", "**quality gates**")) {
            matchedSections++;
        }
        if (containsAny(text, "## user stories", "**user stories**", "### us-", "## us-")) {
            matchedSections++;
        }
        if (containsAny(text,
                "## functional requirements",
                "## non-goals",
                "## non goals",
                "## scope boundaries",
                "**functional requirements**",
                "**non-goals**",
                "**scope boundaries**")) {
            matchedSections++;
        }
        return matchedSections >= 4;
    }

    private boolean containsAny(String value, String... candidates) {
        if (!hasText(value) || candidates == null || candidates.length == 0) {
            return false;
        }

        String normalizedValue = value.toLowerCase();
        for (String candidate : candidates) {
            if (hasText(candidate) && normalizedValue.contains(candidate.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    interface PlannerExecutor {
        PlannerExecution execute(CodexLauncherService.CodexLaunchPlan launchPlan,
                                 CodexLauncherService.RunOutputListener runOutputListener);
    }

    record PlannerExecution(Long processId,
                            Integer exitCode,
                            String stdout,
                            String stderr,
                            String failureMessage) {
        static PlannerExecution completed(long processId, int exitCode, String stdout, String stderr) {
            return new PlannerExecution(processId, exitCode, stdout == null ? "" : stdout, stderr == null ? "" : stderr, "");
        }

        static PlannerExecution failure(String failureMessage) {
            return new PlannerExecution(null, null, "", "", failureMessage == null ? "" : failureMessage);
        }

        boolean successful() {
            return !hasText(failureMessage) && exitCode != null && exitCode == 0;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record PlannerTurnResult(boolean successful,
                                    PrdPlanningSession session,
                                    String assistantMessage,
                                    String latestPrdMarkdown,
                                    String message) {
        private static PlannerTurnResult success(PrdPlanningSession session,
                                                 String assistantMessage,
                                                 String latestPrdMarkdown) {
            return new PlannerTurnResult(true, session, assistantMessage, latestPrdMarkdown, "");
        }

        private static PlannerTurnResult failure(PrdPlanningSession session, String message) {
            return new PlannerTurnResult(false, session, "", "", message == null ? "" : message);
        }
    }

    private static final class SystemPlannerExecutor implements PlannerExecutor {
        private final HostOperatingSystem hostOperatingSystem;

        private SystemPlannerExecutor(HostOperatingSystem hostOperatingSystem) {
            this.hostOperatingSystem = hostOperatingSystem == null
                    ? HostOperatingSystem.detectRuntime()
                    : hostOperatingSystem;
        }

        @Override
        public PlannerExecution execute(CodexLauncherService.CodexLaunchPlan launchPlan,
                                        CodexLauncherService.RunOutputListener runOutputListener) {
            ProcessBuilder processBuilder = new ProcessBuilder(launchPlan.command());
            if (launchPlan.processWorkingDirectory() != null) {
                processBuilder.directory(launchPlan.processWorkingDirectory().toFile());
            }
            if (launchPlan.executionProfile().type() == ExecutionProfile.ProfileType.NATIVE) {
                CodexCliSupport.prependNativePathEntries(processBuilder.environment(), hostOperatingSystem);
            }

            try {
                runOutputListener.onLaunchStarted(launchPlan);
                Process process = processBuilder.start();
                StringBuilder stdoutBuffer = new StringBuilder();
                StringBuilder stderrBuffer = new StringBuilder();
                java.util.concurrent.CompletableFuture<String> stdoutFuture = java.util.concurrent.CompletableFuture
                        .supplyAsync(() -> readFully(process.getInputStream(), stdoutBuffer, runOutputListener::onStdout));
                java.util.concurrent.CompletableFuture<String> stderrFuture = java.util.concurrent.CompletableFuture
                        .supplyAsync(() -> readFully(process.getErrorStream(), stderrBuffer, runOutputListener::onStderr));

                writePrompt(process, launchPlan.promptText());
                int exitCode = process.waitFor();
                return PlannerExecution.completed(process.pid(), exitCode, stdoutFuture.join(), stderrFuture.join());
            } catch (IOException exception) {
                return PlannerExecution.failure(exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return PlannerExecution.failure("Planner execution was interrupted.");
            }
        }

        private static void writePrompt(Process process, String promptText) throws IOException {
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(promptText.getBytes(StandardCharsets.UTF_8));
            }
        }

        private static String readFully(InputStream inputStream,
                                        StringBuilder buffer,
                                        java.util.function.Consumer<String> consumer) {
            try (InputStream stream = inputStream;
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                char[] chunk = new char[1024];
                int readCount;
                while ((readCount = reader.read(chunk)) >= 0) {
                    String textChunk = new String(chunk, 0, readCount);
                    synchronized (buffer) {
                        buffer.append(textChunk);
                    }
                    safeNotify(consumer, textChunk);
                }
                synchronized (buffer) {
                    return buffer.toString();
                }
            } catch (IOException ignored) {
                return "";
            }
        }

        private static void safeNotify(java.util.function.Consumer<String> consumer, String chunk) {
            try {
                consumer.accept(chunk);
            } catch (RuntimeException ignored) {
                // UI listeners should never break process output capture.
            }
        }
    }
}
