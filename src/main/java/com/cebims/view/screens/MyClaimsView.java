package com.cebims.view.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cebims.api.ApiClient;
import com.cebims.api.Session;
import com.cebims.view.Reloadable;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * CEBIMS Borrowing Requests (backed by GET /v1/claims/mine) with Digital Equipment Gate Pass.
 */
public class MyClaimsView extends BorderPane implements Reloadable {
    private final TableView<JsonNode> table = new TableView<>();
    private final TextArea details = new TextArea();
    private final Button cancel = new Button("Cancel request");
    private final Button proof = new Button("View supporting photo");
    private final Button gatePassBtn = new Button("View Pickup Gate Pass");
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

        gatePassBtn.getStyleClass().add("btn-primary");
        gatePassBtn.setDisable(true);
        cancel.setDisable(true);
        proof.setDisable(true);

        gatePassBtn.setOnAction(e -> openGatePassDialog(selected()));
        cancel.setOnAction(e -> cancelClaim());
        proof.setOnAction(e -> Ui.showImage(getScene().getWindow(), "Supporting photo",
                "/v1/claims/" + Ui.t(selected(), "id") + "/proof"));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, gatePassBtn, cancel, proof, spacer, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setTop(Page.top("Borrowing Requests", "Track your submitted equipment requests and open your approved Pickup Gate Pass.", bar));

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
            gatePassBtn.setDisable(true);
            return;
        }
        String status = Ui.t(c, "status");
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(status).append("\n\n");
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
        cancel.setDisable(!"PENDING".equals(status));
        proof.setDisable(false);
        gatePassBtn.setDisable(!"APPROVED".equals(status));
    }

    private void openGatePassDialog(JsonNode c) {
        if (c == null) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("CEBIMS — Approved Equipment Gate Pass");

        ButtonType copyType = new ButtonType("Copy Pass Details", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(copyType, ButtonType.CLOSE);

        String eqName = itemNames.getOrDefault(Ui.t(c, "itemId"), Ui.t(c, "itemId"));
        JsonNode pickup = c.path("pickup");
        String place = pickup.isObject() ? Ui.t(pickup, "placeName") : "-";
        String person = pickup.isObject() ? Ui.t(pickup, "personName") : "-";
        String contact = pickup.isObject() ? Ui.t(pickup, "contact") : "-";
        String note = Ui.t(c, "reviewNote").isEmpty() ? "Present your validated campus ID upon pickup." : Ui.t(c, "reviewNote");

        Label badge = new Label("✓ AUTHORIZED FOR RELEASE");
        badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold;"
                + "-fx-font-size: 11px; -fx-padding: 4 12 4 12; -fx-background-radius: 999;");

        Label header = new Label("CEBIMS EQUIPMENT RELEASE SLIP");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        int r = 0;
        r = passRow(grid, r, "Pass Reference ID:", Ui.t(c, "id"));
        r = passRow(grid, r, "Borrower Name:", Session.displayName());
        r = passRow(grid, r, "Equipment Item:", eqName);
        r = passRow(grid, r, "Approved Timestamp:", Ui.when(Ui.t(c, "reviewedAt")));
        r = passRow(grid, r, "Pickup Location:", place);
        r = passRow(grid, r, "Authorized Custodian:", person);
        r = passRow(grid, r, "Custodian Contact:", contact);
        passRow(grid, r, "Release Instructions:", note);

        VBox card = new VBox(12, badge, header, new Separator(), grid);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("card");
        card.setMinWidth(460);
        dialog.getDialogPane().setContent(card);

        Button copyBtn = (Button) dialog.getDialogPane().lookupButton(copyType);
        copyBtn.setOnAction(ev -> {
            ev.consume();
            String passText = "=== CEBIMS EQUIPMENT GATE PASS ===\n"
                    + "Reference ID: " + Ui.t(c, "id") + "\n"
                    + "Borrower: " + Session.displayName() + "\n"
                    + "Equipment: " + eqName + "\n"
                    + "Approved At: " + Ui.when(Ui.t(c, "reviewedAt")) + "\n"
                    + "Pickup Desk: " + place + "\n"
                    + "Custodian: " + person + " (" + contact + ")\n"
                    + "Note: " + note;
            ClipboardContent cc = new ClipboardContent();
            cc.putString(passText);
            Clipboard.getSystemClipboard().setContent(cc);
            Ui.info("Gate pass details copied to clipboard.");
        });

        dialog.show();
    }

    private static int passRow(GridPane g, int r, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value == null || value.isEmpty() ? "-" : value);
        v.setWrapText(true);
        v.setMaxWidth(290);
        g.addRow(r, l, v);
        return r + 1;
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