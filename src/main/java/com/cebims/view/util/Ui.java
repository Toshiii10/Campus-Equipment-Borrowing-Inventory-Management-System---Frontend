package com.cebims.view.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Small helpers shared by all screens. */
public final class Ui {
    private Ui() {}

    // ---- messages --------------------------------------------------------------------------

    public static void error(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.setHeaderText(null);
        a.show();
    }

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setHeaderText(null);
        a.show();
    }

    public static boolean confirm(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    /** Runs the handler on the JavaFX thread once the request finishes; reports connection failures. */
    public static void call(CompletableFuture<ApiClient.Response> request, Consumer<ApiClient.Response> handler) {
        request.whenComplete((r, e) -> Platform.runLater(() -> {
            if (e != null) error("Cannot reach the server. Is the backend running on " + ApiClient.BASE_URL + "?");
            else handler.accept(r);
        }));
    }

    // ---- JSON / table helpers --------------------------------------------------------------

    public static String t(JsonNode n, String field) {
        return n.path(field).asText("");
    }

    /** 2026-10-24T13:05:11Z -> 2026-10-24 13:05 */
    public static String when(String iso) {
        return iso != null && iso.length() >= 16 && iso.charAt(10) == 'T'
                ? iso.substring(0, 10) + " " + iso.substring(11, 16) : (iso == null ? "" : iso);
    }

    public static TableColumn<JsonNode, String> col(String title, Function<JsonNode, String> value, double width) {
        TableColumn<JsonNode, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    /** Renders a status column as a rounded color-coded pill badge like the CEBIMS dashboard. */
    public static TableColumn<JsonNode, String> statusCol(String title, Function<JsonNode, String> value, double width) {
        TableColumn<JsonNode, String> c = col(title, value, width);
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                String bg = "#f1f5f9", fg = "#475569", prefix = "• ";
                switch (item.toUpperCase()) {
                    case "OPEN", "APPROVED", "CONFIRMED" -> { bg = "#dcfce7"; fg = "#15803d"; prefix = "✓ "; }
                    case "PENDING", "SUGGESTED"          -> { bg = "#fef3c7"; fg = "#b45309"; prefix = "⏳ "; }
                    case "MATCHED", "CLAIMED"            -> { bg = "#dbeafe"; fg = "#1d4ed8"; prefix = "★ "; }
                    case "REJECTED", "EXPIRED", "CANCELLED", "CLOSED" -> { bg = "#fee2e2"; fg = "#b91c1c"; prefix = "✕ "; }
                }
                Label pill = new Label(prefix + item);
                pill.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg
                        + "; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 10 3 10; -fx-background-radius: 999;");
                setGraphic(pill);
                setText(null);
            }
        });
        return c;
    }

    public static Window windowOf(Dialog<?> d) {
        return d.getDialogPane().getScene() == null ? null : d.getDialogPane().getScene().getWindow();
    }

    // ---- photos ----------------------------------------------------------------------------

    /** Opens a file chooser for a photo. Returns null if the user cancels. */
    public static Path pickImage(Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a photo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File f = fc.showOpenDialog(owner);
        return f == null ? null : f.toPath();
    }

    /** A square thumbnail that loads the photo in the background (with the login token) and enlarges on click. */
    public static StackPane thumb(Window owner, String url, double size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        StackPane box = new StackPane(iv);
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setStyle("-fx-background-color: #eeeeee; -fx-border-color: #cccccc;");
        box.setCursor(Cursor.HAND);
        box.setOnMouseClicked(e -> showImage(owner, "Photo", url));
        ApiClient.getBytes(url).whenComplete((bytes, ex) -> Platform.runLater(() -> {
            if (ex != null) return; // leave the grey box
            Image img = new Image(new ByteArrayInputStream(bytes), size * 2, size * 2, true, true);
            if (!img.isError()) iv.setImage(img);
        }));
        return box;
    }

    /** A row of thumbnails for an "images" JSON array ([{url, position}, ...]). */
    public static HBox photoStrip(Window owner, JsonNode images) {
        HBox strip = new HBox(10);
        strip.setPadding(new Insets(4, 0, 4, 0));
        if (images != null) {
            for (JsonNode img : images) strip.getChildren().add(thumb(owner, img.path("url").asText(), 150));
        }
        return strip;
    }

    /** Downloads one photo and shows it full size in a window. */
    public static void showImage(Window owner, String title, String url) {
        ApiClient.getBytes(url).whenComplete((bytes, ex) -> Platform.runLater(() -> {
            if (ex != null) { error("Could not load the photo."); return; }
            Image img = new Image(new ByteArrayInputStream(bytes));
            if (img.isError()) { error("This photo format cannot be previewed here (WebP is not supported)."); return; }
            ImageView iv = new ImageView(img);
            iv.setPreserveRatio(true);
            if (img.getWidth() > 800) iv.setFitWidth(800);
            ScrollPane sp = new ScrollPane(iv);
            sp.setPrefViewportWidth(Math.min(img.getWidth(), 800) + 4);
            sp.setPrefViewportHeight(Math.min(img.getHeight() * Math.min(1.0, 800 / img.getWidth()), 600) + 4);
            Dialog<Void> d = new Dialog<>();
            d.initOwner(owner);
            d.setTitle(title);
            d.getDialogPane().setContent(sp);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.show();
        }));
    }
}
