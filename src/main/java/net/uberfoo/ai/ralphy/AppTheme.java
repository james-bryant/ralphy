package net.uberfoo.ai.ralphy;

import javafx.scene.Scene;

import java.net.URL;

public final class AppTheme {
    private static final String ROOT_STYLE_CLASS = "ralphy-shell";
    private static final String STYLESHEET = "app-theme.css";

    private AppTheme() {
    }

    public static void apply(Scene scene) {
        if (!scene.getRoot().getStyleClass().contains(ROOT_STYLE_CLASS)) {
            scene.getRoot().getStyleClass().add(ROOT_STYLE_CLASS);
        }

        String stylesheetUrl = stylesheetUrl();
        if (!scene.getStylesheets().contains(stylesheetUrl)) {
            scene.getStylesheets().add(stylesheetUrl);
        }
    }

    public static String rootStyleClass() {
        return ROOT_STYLE_CLASS;
    }

    public static String stylesheetUrl() {
        URL stylesheet = AppTheme.class.getResource(STYLESHEET);
        if (stylesheet == null) {
            throw new IllegalStateException("Missing stylesheet resource " + STYLESHEET);
        }
        return stylesheet.toExternalForm();
    }
}
