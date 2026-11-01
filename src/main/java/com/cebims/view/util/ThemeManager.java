package com.cebims.view.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;

/**
 * Manages global Light / Dark mode state across LoginView and MainView.
 * Adds or removes the "dark" style class on the Scene's root node.
 */
public final class ThemeManager {
    private static boolean dark = false;

    private ThemeManager() {}

    public static boolean isDark() {
        return dark;
    }

    public static void toggle(Scene scene) {
        dark = !dark;
        apply(scene);
    }

    public static void apply(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        apply(scene.getRoot());
    }

    public static void apply(Parent root) {
        if (root == null) return;
        if (dark) {
            if (!root.getStyleClass().contains("dark")) {
                root.getStyleClass().add("dark");
            }
        } else {
            root.getStyleClass().remove("dark");
        }
    }

    /** Creates a compact theme toggle button (Sun / Moon icon + label) for LoginView and MainView. */
    public static Button createToggleButton(boolean showLabel) {
        Button btn = new Button();
        btn.getStyleClass().add("theme-toggle-btn");
        btn.setTooltip(new Tooltip("Switch between Light and Dark mode"));
        updateButtonVisuals(btn, showLabel);

        btn.setOnAction(e -> {
            toggle(btn.getScene());
            updateButtonVisuals(btn, showLabel);
        });
        return btn;
    }

    private static void updateButtonVisuals(Button btn, boolean showLabel) {
        String iconPath = dark ? Icons.SUN : Icons.MOON;
        StackPane ic = Icons.box(iconPath, "theme-icon", 17);
        btn.setGraphic(ic);
        if (showLabel) {
            btn.setText(dark ? "Light mode" : "Dark mode");
        } else {
            btn.setText("");
        }
    }
}