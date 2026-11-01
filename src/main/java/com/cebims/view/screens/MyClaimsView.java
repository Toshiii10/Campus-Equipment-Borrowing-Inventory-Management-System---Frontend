package com.cebims.view.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.view.Reloadable;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * CEBIMS Borrowing Requests (backed by GET /v1/claims/mine).
 */
public class MyClaimsView extends BorderPane implements Reloadable {
    private final TableView<JsonNode> table = new TableView<>();
    private final TextArea details = new TextArea();
    private final Button cancel = new Button("Cancel request");
    private final Button proof = new Button("View supporting photo");
    private final Map<String, String> itemNames = new HashMap<>();
    private final Set<String> requested = new HashSet<>();

    public MyClaimsView() {
        setPadding(new Insets(22, 28, 22, 28));

        table.setPlaceholder(new Label("You have no borrowing requests yet. Browse \"Equipment\" to submit one."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(Ui.col("Equipment", n -> itemNames.getOrDefault(Ui.t(n, "itemId"), "..."), 200));
        table.getColumns().add(Ui.statusCol("Status", n -> Ui.t(n, "status"), 100));
        table.getColumns().add(Ui.col("Requested", n -> Ui.when(Ui.t(n, "createdAt")), 140));
        table.getColumns().add(Ui.col("Reviewed", n -> Ui.when(Ui.t(n, "reviewedAt")), 140));
        table.getColumns().add(Ui.col("Staff Note", n -> Ui.t(n, "reviewNote"), 260));
        table.setPrefHeight(260);

        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(8);
        details.setPromptText("Select a request to view pickup instructions and status details.");

        cancel.setDisable(true);
        proof.setDisable(true);
        cancel.setOnAction(e -> cancelClaim());
        proof.setOnAction(e -> Ui.showImage(getScene().getWindow(), "Supporting photo",
                "/v1/claims/" + Ui.t(selected(), "id") + "/proof"));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, cancel, proof, spacer, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setTop(Page.top("Borrowing Requests", "Track your submitted equipment requests and pickup instructions.", bar));

        VBox center = new VBox(8, table, details);
        VBox.setVgrow(details, Priority.ALWAYS);
        setCenter(center);

        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> show(sel));
        load();
    }

    @Override public void reload() { load(); }

    private JsonNode selected() { return table.getSelectionModel().getSelectedItem(); }

    private void load() {
        Ui.call(ApiClient.get("/v1/claims/mine"), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            List<JsonNode> list = new ArrayList<>();
            r.json().forEach(list::add);
            table.getItems().setAll(list);
            show(null);
            for (JsonNode c : list) fetchName(Ui.t(c, "itemId"));
        });
    }

    private void fetchName(String itemId) {
        if (itemId.isEmpty() || !requested.add(itemId)) return;
        ApiClient.get("/v1/items/" + itemId).whenComplete((r, ex) -> Platform.runLater(() -> {
            itemNames.put(itemId, ex == null && r.ok() ? r.json().path("name").asText("(equipment)") : "(no longer listed)");
            table.refresh();
        }));
    }

    private void show(JsonNode c) {
        if (c == null) {
            details.clear();
            cancel.setDisable(true);
            proof.setDisable(true);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(Ui.t(c, "status")).append("\n\n");
        sb.append("Request / Verification details:\n").append(Ui.t(c, "proofDescription")).append("\n");
        String note = Ui.t(c, "reviewNote");
        if (!note.isEmpty()) sb.append("\nStaff note: ").append(note).append("\n");
        JsonNode pickup = c.path("pickup");
        if (pickup.isObject()) {
            sb.append("\nAPPROVED - Collect equipment at:\n");
            sb.append("Location: ").append(Ui.t(pickup, "placeName")).append("\n");
            sb.append("Custodian: ").append(Ui.t(pickup, "personName")).append("\n");
            sb.append("Contact: ").append(Ui.t(pickup, "contact")).append("\n");
        }
        details.setText(sb.toString());
        cancel.setDisable(!"PENDING".equals(Ui.t(c, "status")));
        proof.setDisable(false);
    }

    private void cancelClaim() {
        JsonNode c = selected();
        if (c == null) return;
        if (!Ui.confirm("Cancel this pending equipment request?")) return;
        Ui.call(ApiClient.post("/v1/claims/" + Ui.t(c, "id") + "/cancel", null), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }
}