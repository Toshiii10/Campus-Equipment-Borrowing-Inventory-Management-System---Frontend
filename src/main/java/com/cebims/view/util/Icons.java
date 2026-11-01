package com.cebims.view.util;

import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** Simple line icons drawn as vector paths 24x24 grid */
public final class Icons {
    private Icons() {}

    public static final String HOME      = "M3 11 L12 3 L21 11 M5 9.5 V20 H10 V14 H14 V20 H19 V9.5";
    public static final String BOX       = "M3 7 L12 3 L21 7 L21 17 L12 21 L3 17 Z M3 7 L12 11 L21 7 M12 11 L12 21";
    public static final String SEARCH    = "M10.5 3 A7.5 7.5 0 1 0 10.5 18 A7.5 7.5 0 1 0 10.5 3 Z M16 16 L21 21";
    public static final String DOC       = "M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19 M9 13 H16 M9 17 H16";
    public static final String HELP      = "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M9.5 9.5 A2.5 2.5 0 1 1 13 11.8 C12.2 12.4 12 12.8 12 14 M12 17 L12 17.5";
    public static final String CHECK     = "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M8 12 L11 15 L16 9";
    public static final String BELL      = "M6 16 V11 A6 6 0 0 1 18 11 V16 L20 18 H4 Z M10 21 A2 2 0 0 0 14 21";
    public static final String USER      = "M12 4 A4 4 0 1 0 12 12 A4 4 0 1 0 12 4 Z M4 21 A8 8 0 0 1 20 21";
    public static final String SHIELD    = "M12 3 L20 6 V12 C20 17 16 20 12 21 C8 20 4 17 4 12 V6 Z";
    public static final String CLIPBOARD = "M9 4 H15 V7 H9 Z M7 5.5 H5 V21 H19 V5.5 H17 M8.5 12 H15.5 M8.5 16 H15.5";
    public static final String LOGOUT    = "M10 4 H5 V20 H10 M14 8 L19 12 L14 16 M19 12 H9";
    public static final String SUN       = "M12 7 A5 5 0 1 0 12 17 A5 5 0 1 0 12 7 Z M12 1 V3 M12 21 V23 M4.22 4.22 L5.64 5.64 M18.36 18.36 L19.78 19.78 M1 12 H3 M21 12 H23 M4.22 19.78 L5.64 18.36 M18.36 5.64 L19.78 4.22";
    public static final String MOON      = "M21 12.79 A9 9 0 1 1 11.21 3 A7 7 0 0 0 21 12.79 Z";

    /** An icon in a fixed square box so labels line up. extraClass adds a style class (for example nav-icon). */
    public static StackPane box(String path, String extraClass, double size) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(Color.TRANSPARENT);
        p.setStrokeWidth(2.0);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        p.getStyleClass().add("icon");
        if (extraClass != null) p.getStyleClass().add(extraClass);
        double scale = size / 24.0;
        p.setScaleX(scale);
        p.setScaleY(scale);
        StackPane box = new StackPane(new Group(p));
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }

    /** Creates a vector icon with an explicit stroke color and optional square badge background. */
    public static StackPane badge(String path, String strokeHex, String bgHex, double iconSize, double boxSize, double radius) {
        StackPane b = box(path, null, iconSize);
        SVGPath svg = (SVGPath) ((Group) b.getChildren().get(0)).getChildren().get(0);
        svg.setStroke(Color.web(strokeHex));
        b.setMinSize(boxSize, boxSize);
        b.setPrefSize(boxSize, boxSize);
        b.setMaxSize(boxSize, boxSize);
        if (bgHex != null) {
            b.setStyle("-fx-background-color: " + bgHex + "; -fx-background-radius: " + (int) radius + ";");
        }
        return b;
    }
}