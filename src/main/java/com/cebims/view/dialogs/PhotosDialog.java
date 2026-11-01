package com.cebims.view.dialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.view.util.Ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Add or remove the photos (max 3) of one of your own reports.
 *   found:  POST /v1/items/{id}/images , DELETE /v1/items/{id}/images/{position}   (public photos)
 *   lost:   POST /v1/lost-items/{id}/images , DELETE .../images/{position}          (private photos)
 */
public final class PhotosDialog {
    private static final int MAX = 3;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final String base;
    private final Runnable onChanged;
    private final Dialog<Void> dialog = new Dialog<>();
    private final HBox strip = new HBox(14);
    private final Label count = new Label();
    private final Button add = new Button("Add photo...");
    private int photos;

    public static void open(Window owner, String itemId, boolean lost, JsonNode images, Runnable onChanged) {
        new PhotosDialog(owner, itemId, lost, images, onChanged);
    }

    private PhotosDialog(Window owner, String itemId, boolean lost, JsonNode images, Runnable onChanged) {
        this.base = (lost ? "/v1/lost-items/" : "/v1/items/") + itemId + "/images";
        this.onChanged = onChanged;

        dialog.initOwner(owner);
        dialog.setTitle(lost ? "Lost report photos (private)" : "Found item photos");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        add.setOnAction(e -> addPhoto());
        VBox content = new VBox(10, count, strip, add);
        content.setPadding(new Insets(10));
        content.setMinWidth(520);
        content.setMinHeight(260);
        dialog.getDialogPane().setContent(content);

        render(images);
        dialog.show();
    }

    private void render(JsonNode images) {
        strip.getChildren().clear();
        photos = 0;
        if (images != null) {
            for (JsonNode img : images) {
                photos++;
                int position = img.path("position").asInt();
                Button remove = new Button("Remove");
                remove.setOnAction(e -> removePhoto(position));
                strip.getChildren().add(new VBox(6, Ui.thumb(Ui.windowOf(dialog), img.path("url").asText(), 150), remove));
            }
        }
        count.setText(photos + " of " + MAX + " photos");
        add.setDisable(photos >= MAX);
    }

    private void addPhoto() {
        Path file = Ui.pickImage(Ui.windowOf(dialog));
        if (file == null) return;
        try {
            if (Files.size(file) > MAX_BYTES) { Ui.error("That photo is larger than 5 MB."); return; }
            ApiClient.FilePart part = ApiClient.FilePart.fromFile("file", file);
            add.setDisable(true);
            Ui.call(ApiClient.postMultipart(base, Map.of(), part), r -> {
                if (r.ok()) { render(r.json().path("images")); onChanged.run(); }
                else { Ui.error(r.errorMessage()); add.setDisable(photos >= MAX); }
            });
        } catch (IOException ex) {
            Ui.error("Could not read that file.");
        }
    }

    private void removePhoto(int position) {
        if (!Ui.confirm("Remove this photo?")) return;
        Ui.call(ApiClient.delete(base + "/" + position), r -> {
            if (r.ok()) { render(r.json().path("images")); onChanged.run(); }
            else Ui.error(r.errorMessage());
        });
    }
}

