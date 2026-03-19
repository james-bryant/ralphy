package net.uberfoo.ai.ralphy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@Component
public class UserPreferencesSettingsService {
    private static final String SETTINGS_NODE_NAME = "settings";
    private static final String EXECUTION_PROFILE_TYPE_KEY = "execution.profile.type";
    private static final String EXECUTION_PROFILE_WSL_DISTRIBUTION_KEY = "execution.profile.wslDistribution";
    private static final String EXECUTION_PROFILE_WINDOWS_PATH_PREFIX_KEY = "execution.profile.windowsPathPrefix";
    private static final String EXECUTION_PROFILE_WSL_PATH_PREFIX_KEY = "execution.profile.wslPathPrefix";

    private final Preferences preferences;

    @Autowired
    public UserPreferencesSettingsService(
            @Value("${ralphy.user.settings.node-path:}") String preferencesNodePath) {
        this(resolvePreferences(preferencesNodePath));
    }

    UserPreferencesSettingsService(Preferences preferences) {
        this.preferences = usablePreferences(Objects.requireNonNull(preferences, "preferences must not be null"));
    }

    public synchronized ExecutionProfile executionProfile() {
        return new ExecutionProfile(
                ExecutionProfile.ProfileType.fromStorageValue(
                        preferences.get(EXECUTION_PROFILE_TYPE_KEY, ExecutionProfile.ProfileType.NATIVE.storageValue())
                ),
                preferences.get(EXECUTION_PROFILE_WSL_DISTRIBUTION_KEY, null),
                preferences.get(EXECUTION_PROFILE_WINDOWS_PATH_PREFIX_KEY, null),
                preferences.get(EXECUTION_PROFILE_WSL_PATH_PREFIX_KEY, null)
        );
    }

    public synchronized ExecutionProfile saveExecutionProfile(ExecutionProfile executionProfile) {
        Objects.requireNonNull(executionProfile, "executionProfile must not be null");
        put(EXECUTION_PROFILE_TYPE_KEY, executionProfile.type().storageValue());
        put(EXECUTION_PROFILE_WSL_DISTRIBUTION_KEY, executionProfile.wslDistribution());
        put(EXECUTION_PROFILE_WINDOWS_PATH_PREFIX_KEY, executionProfile.windowsPathPrefix());
        put(EXECUTION_PROFILE_WSL_PATH_PREFIX_KEY, executionProfile.wslPathPrefix());
        flushPreferences();
        return executionProfile();
    }

    public synchronized ExecutionAgentSelection executionStageSelection() {
        return readStageSelection(Stage.EXECUTION, ExecutionAgentSelection.codexDefault());
    }

    public synchronized ExecutionAgentSelection planningStageSelection() {
        return readStageSelection(Stage.PLANNING, ExecutionAgentSelection.codexDefault());
    }

    public synchronized ExecutionAgentSelection saveExecutionStageSelection(ExecutionAgentSelection executionAgentSelection) {
        return saveStageSelection(Stage.EXECUTION, executionAgentSelection);
    }

    public synchronized ExecutionAgentSelection savePlanningStageSelection(ExecutionAgentSelection executionAgentSelection) {
        return saveStageSelection(Stage.PLANNING, executionAgentSelection);
    }

    synchronized void clearForTest() {
        try {
            preferences.clear();
            flushPreferences();
        } catch (BackingStoreException exception) {
            throw new IllegalStateException("Unable to clear user preferences settings.", exception);
        }
    }

    private ExecutionAgentSelection saveStageSelection(Stage stage, ExecutionAgentSelection executionAgentSelection) {
        Objects.requireNonNull(executionAgentSelection, "executionAgentSelection must not be null");
        put(stage.providerKey(), executionAgentSelection.provider().name());
        put(stage.modelKey(), executionAgentSelection.modelId());
        put(stage.thinkingKey(), executionAgentSelection.thinkingLevel());
        flushPreferences();
        return readStageSelection(stage, executionAgentSelection);
    }

    private ExecutionAgentSelection readStageSelection(Stage stage, ExecutionAgentSelection fallbackSelection) {
        ExecutionAgentProvider provider = fallbackSelection == null
                ? ExecutionAgentProvider.CODEX
                : fallbackSelection.provider();
        String storedProvider = preferences.get(stage.providerKey(), "");
        if (hasText(storedProvider)) {
            try {
                provider = ExecutionAgentProvider.valueOf(storedProvider.trim());
            } catch (IllegalArgumentException ignored) {
                provider = fallbackSelection == null ? ExecutionAgentProvider.CODEX : fallbackSelection.provider();
            }
        }
        return new ExecutionAgentSelection(
                provider,
                preferences.get(stage.modelKey(), fallbackSelection == null ? "" : fallbackSelection.modelId()),
                preferences.get(stage.thinkingKey(), fallbackSelection == null ? "" : fallbackSelection.thinkingLevel())
        );
    }

    private void put(String key, String value) {
        if (!hasText(value)) {
            preferences.remove(key);
            return;
        }
        preferences.put(key, value.trim());
    }

    private void flushPreferences() {
        try {
            preferences.flush();
        } catch (BackingStoreException ignored) {
            // Some test and sandbox environments expose Preferences but do not allow an immediate flush.
        }
    }

    private static Preferences resolvePreferences(String preferencesNodePath) {
        String resolvedNodePath = hasText(preferencesNodePath)
                ? preferencesNodePath.trim()
                : defaultPreferencesNodePath();
        try {
            return usablePreferences(Preferences.userRoot().node(resolvedNodePath));
        } catch (RuntimeException ignored) {
            return InMemoryPreferences.userRoot().node(resolvedNodePath);
        }
    }

    private static Preferences usablePreferences(Preferences preferences) {
        try {
            verifyPreferencesAccess(preferences);
            return preferences;
        } catch (BackingStoreException | RuntimeException ignored) {
            return InMemoryPreferences.userRoot().node(preferences.absolutePath());
        }
    }

    private static void verifyPreferencesAccess(Preferences preferences) throws BackingStoreException {
        String probeKey = "__ralphy_probe__";
        preferences.put(probeKey, "1");
        preferences.remove(probeKey);
        preferences.flush();
    }

    private static String defaultPreferencesNodePath() {
        return "/" + UserPreferencesSettingsService.class.getPackageName().replace('.', '/')
                + "/" + SETTINGS_NODE_NAME;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum Stage {
        EXECUTION("execution"),
        PLANNING("planning");

        private final String prefix;

        Stage(String prefix) {
            this.prefix = prefix;
        }

        private String providerKey() {
            return prefix + ".agent.provider";
        }

        private String modelKey() {
            return prefix + ".agent.model";
        }

        private String thinkingKey() {
            return prefix + ".agent.thinking";
        }
    }

    private static final class InMemoryPreferences extends AbstractPreferences {
        private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, InMemoryPreferences> children = new HashMap<>();

        private InMemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        public static Preferences userRoot() {
            return USER_ROOT;
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
            children.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return children.keySet().toArray(String[]::new);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, childName -> new InMemoryPreferences(this, childName));
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
