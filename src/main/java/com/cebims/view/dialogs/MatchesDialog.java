package com.cebims.view.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.view.util.Ui;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Automated CEBIMS Inventory Matches (up to 5, best first): GET /v1/lost-items/{id}/matches.
 */
public final class MatchesDialog {
    private final String lostId;
    private final Dialog<Void> dialog = new Dialog<>();
    private final TableView<JsonNode> table = new TableView<>();

    public static void open(Window owner, String lostId, String lostName) {
        new MatchesDialog(owner, lostId, lostName);
    }

    private MatchesDialog(Window owner, String lostId, String lostName) {
        this.lostId = lostId;
        dialog.initOwner(owner);
        dialog.setTitle("Suggested Inventory Matches for: " + lostName);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label scoringBanner = new Label(
                "Smart Matching Scoring (100 pts max):  Category (15%)  •  Color (20%)  •  Brand/Model (20%)  •  Keywords (30%)  •  Date Proximity (15%)");
        scoringBanner.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1d4ed8; -fx-font-weight: bold;"
                + "-fx-font-size: 11.5px; -fx-padding: 8 12 8 12; -fx-background-radius: 8;");
        scoringBanner.setMaxWidth(Double.MAX_VALUE);

        table.setPlaceholder(new Label("No matching equipment found yet. We automatically re-check whenever new equipment is logged."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(Ui.col("Match Score", n -> n.path("score").asInt() + "%", 90));
        table.getColumns().add(Ui.col("Equipment", n -> Ui.t(n.path("foundItem"), "name"), 170));
        table.getColumns().add(Ui.col("Color", n -> Ui.t(n.path("foundItem"), "color"), 80));
        table.getColumns().add(Ui.col("Brand / Model", n -> Ui.t(n.path("foundItem"), "brand"), 110));
        table.getColumns().add(Ui.col("Location", n -> Ui.t(n.path("foundItem"), "locationName"), 150));
        table.getColumns().add(Ui.col("Date Logged", n -> Ui.t(n.path("foundItem"), "dateOccurred"), 95));
        table.getColumns().add(Ui.col("Pickup Desk", n -> Lookups.dropName(Ui.t(n.path("foundItem"), "dropLocationId")), 130));
        table.setRowFactory(tv -> {
            TableRow<JsonNode> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) view(row.getItem());
            });
            return row;
        });

        Button view = new Button("Inspect / Request Equipment");
        view.getStyleClass().add("btn-primary");
        Button dismiss = new Button("Dismiss Match");
        view.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        dismiss.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        view.setOnAction(e -> view(table.getSelectionModel().getSelectedItem()));
        dismiss.setOnAction(e -> dismiss(table.getSelectionModel().getSelectedItem()));

        VBox content = new VBox(10, scoringBanner, table, new HBox(8, view, dismiss));
        content.setPadding(new Insets(10));
        content.setPrefSize(850, 380);
        dialog.getDialogPane().setContent(content);

        load();
        dialog.show();
    }

    private void load() {
        Ui.call(ApiClient.get("/v1/lost-items/" + lostId + "/matches"), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            List<JsonNode> list = new ArrayList<>();
            r.json().forEach(list::add);
            table.getItems().setAll(list);
        });
    }

    private void view(JsonNode match) {
        if (match != null) ItemDetailDialog.open(Ui.windowOf(dialog), match.path("foundItem"));
    }

    private void dismiss(JsonNode match) {
        if (match == null) return;
        if (!Ui.confirm("Dismiss this inventory match suggestion? You will not see it again.")) return;
        Ui.call(ApiClient.post("/v1/lost-items/" + lostId + "/matches/" + Ui.t(match, "id") + "/dismiss", null), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }
}