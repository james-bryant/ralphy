package net.uberfoo.ai.ralphy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarkdownPrdExchangeLocations(String lastImportedPath, String lastExportedPath) {
    public MarkdownPrdExchangeLocations {
        lastImportedPath = normalize(lastImportedPath);
        lastExportedPath = normalize(lastExportedPath);
    }

    public MarkdownPrdExchangeLocations withLastImportedPath(String replacementPath) {
        return new MarkdownPrdExchangeLocations(replacementPath, lastExportedPath);
    }

    public MarkdownPrdExchangeLocations withLastExportedPath(String replacementPath) {
        return new MarkdownPrdExchangeLocations(lastImportedPath, replacementPath);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
