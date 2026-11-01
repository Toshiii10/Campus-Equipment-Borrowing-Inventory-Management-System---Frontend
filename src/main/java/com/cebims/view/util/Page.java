package com.cebims.view.util;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** The page heading (title + one-line help text) shown at the top of each screen, matching the dashboard theme. */
public final class Page {
    private Page() {}

    public static VBox header(String title, String subtitle) {
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        Label s = new Label(subtitle);
        s.setWrapText(true);
        s.getStyleClass().add("page-sub");
        return new VBox(2, t, s);
    }

    /** Heading on top, then the screen's toolbar. */
    public static VBox top(String title, String subtitle, Node toolbar) {
        VBox box = new VBox(14, header(title, subtitle), toolbar);
        box.setPadding(new Insets(0, 0, 12, 0));
        return box;
    }
}