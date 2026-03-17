package net.uberfoo.ai.ralphy;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAgentModelCatalogServiceTest {
    @Test
    void modelsForCodexParsesDynamicModelListResponse() {
        String stdout = """
                {"jsonrpc":"2.0","id":"init-1","result":{"platformFamily":"windows","platformOs":"windows","userAgent":"codex-cli"}}
                {"jsonrpc":"2.0","id":"model-list-1","result":{"data":[{"id":"m-1","model":"gpt-5.4","displayName":"GPT-5.4","description":"Frontier model","isDefault":true,"hidden":false},{"id":"m-2","model":"gpt-5.4-mini","displayName":"GPT-5.4 Mini","description":"Fast model","isDefault":false,"hidden":false}]}}
                """.trim();
        ExecutionAgentModelCatalogService service = new ExecutionAgentModelCatalogService(
                CodexCliSupport.DEFAULT_COMMAND,
                command -> new FakeAppServerProcess(stdout)
        );

        ExecutionAgentModelCatalogService.ModelCatalog modelCatalog = service.modelsFor(ExecutionAgentProvider.CODEX);

        assertTrue(modelCatalog.successful());
        assertEquals(2, modelCatalog.models().size());
        assertEquals("gpt-5.4", modelCatalog.models().getFirst().modelId());
        assertEquals("gpt-5.4-mini", modelCatalog.models().get(1).modelId());
    }

    @Test
    void modelsForUnsupportedProviderReturnsPlannedMessage() {
        ExecutionAgentModelCatalogService service = new ExecutionAgentModelCatalogService(
                CodexCliSupport.DEFAULT_COMMAND,
                command -> new FakeAppServerProcess("")
        );

        ExecutionAgentModelCatalogService.ModelCatalog modelCatalog =
                service.modelsFor(ExecutionAgentProvider.CLAUDE_CODE);

        assertFalse(modelCatalog.successful());
        assertTrue(modelCatalog.message().contains("not implemented"));
        assertTrue(modelCatalog.models().isEmpty());
    }

    @Test
    void providersExposeFutureIntegrationsAsDisabledChoices() {
        ExecutionAgentModelCatalogService service = new ExecutionAgentModelCatalogService(
                CodexCliSupport.DEFAULT_COMMAND,
                command -> new FakeAppServerProcess("")
        );

        List<ExecutionAgentModelCatalogService.ProviderSupport> providers = service.providers();

        assertEquals(3, providers.size());
        assertEquals(ExecutionAgentProvider.CODEX, providers.getFirst().provider());
        assertTrue(providers.getFirst().enabled());
        assertFalse(providers.get(1).enabled());
        assertFalse(providers.get(2).enabled());
    }

    private static final class FakeAppServerProcess implements ExecutionAgentModelCatalogService.AppServerProcess {
        private final BufferedReader stdoutReader;
        private final BufferedReader stderrReader;
        private final BufferedWriter stdinWriter;

        private FakeAppServerProcess(String stdout) {
            this.stdoutReader = new BufferedReader(new StringReader(stdout == null ? "" : stdout));
            this.stderrReader = new BufferedReader(new StringReader(""));
            this.stdinWriter = new BufferedWriter(new StringWriter());
        }

        @Override
        public BufferedWriter stdinWriter() {
            return stdinWriter;
        }

        @Override
        public BufferedReader stdoutReader() {
            return stdoutReader;
        }

        @Override
        public BufferedReader stderrReader() {
            return stderrReader;
        }

        @Override
        public void close() throws IOException {
            stdinWriter.close();
            stdoutReader.close();
            stderrReader.close();
        }
    }
}
