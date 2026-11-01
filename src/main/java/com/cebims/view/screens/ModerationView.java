package com.cebims.view.screens;

import com.cebims.view.Reloadable;
import com.cebims.view.dialogs.AuthDialogs;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Staff review of claims (Moderator / Admin only; the backend enforces it too).
 *   GET  /v1/moderation/claims?status=PENDING
 *   POST /v1/moderation/claims/{id}/approve   (note optional)
 *   POST /v1/moderation/claims/{id}/reject    (note required)
 * The detail box puts the claimant's proof next to the item's secret marks so staff can compare.
 */
public class ModerationView extends BorderPane implements Reloadable {
    private final TableView<JsonNode> table = new TableView<>();
    private final ComboBox<String> status = new ComboBox<>();
    private final TextArea details = new TextArea();
    private final Button approve = new Button("Approve");
    { approve.getStyleClass().add("btn-primary"); }
    private final Button reject = new Button("Reject");
    private final Button proofPhoto = new Button("View proof photo");
    private final Button editClaimant = new Button("Edit claimant profile...");

    public ModerationView() {
        setPadding(new Insets(22, 28, 22, 28));

        status.getItems().addAll("PENDING", "APPROVED", "REJECTED", "CANCELLED");
        status.setValue("PENDING");
        status.setOnAction(e -> load());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, new Label("Show claims:"), status, spacer, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setTop(Page.top("Claims review", "Compare each claim's proof with the item, then approve or reject.", bar));

        table.setPlaceholder(new Label("No claims with this status."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(col("Claimant", n -> n.path("claimant").path("name").asText(""), 160));
        table.getColumns().add(col("Student ID", n -> n.path("claimant").path("studentId").asText(""), 110));
        table.getColumns().add(col("Item", n -> n.path("item").path("name").asText(""), 180));
        table.getColumns().add(Ui.statusCol("Status", n -> n.path("status").asText(""), 90));
        table.getColumns().add(col("Submitted", n -> when(n.path("createdAt").asText("")), 140));
        table.setPrefHeight(260);

        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(12);
        details.setPromptText("Select a claim to review it.");

        approve.setDisable(true);
        reject.setDisable(true);
        approve.setOnAction(e -> review(true));
        reject.setOnAction(e -> review(false));
        proofPhoto.setDisable(true);
        editClaimant.setDisable(true);
        proofPhoto.setOnAction(e -> {
            JsonNode c = table.getSelectionModel().getSelectedItem();
            if (c != null) Ui.showImage(getScene().getWindow(), "Claim proof photo",
                    "/v1/claims/" + c.path("id").asText() + "/proof");
        });
        editClaimant.setOnAction(e -> editClaimant());
        HBox actions = new HBox(8, approve, reject, proofPhoto, editClaimant);

        VBox bottom = new VBox(8, new Label("Review"), details, actions);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        VBox.setVgrow(details, Priority.ALWAYS);

        VBox center = new VBox(8, table, bottom);
        VBox.setVgrow(table, Priority.SOMETIMES);
        VBox.setVgrow(bottom, Priority.ALWAYS);
        setCenter(center);

        table.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> show(sel));
        load();
    }

    @Override public void reload() { load(); }

    private static TableColumn<JsonNode, String> col(String title, Function<JsonNode, String> value, double width) {
        TableColumn<JsonNode, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    /** 2026-10-24T13:05:11Z -> 2026-10-24 13:05 */
    private static String when(String iso) {
        return iso.length() >= 16 && iso.charAt(10) == 'T' ? iso.substring(0, 10) + " " + iso.substring(11, 16) : iso;
    }

    private void load() {
        Ui.call(ApiClient.get("/v1/moderation/claims?status=" + status.getValue()), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            List<JsonNode> list = new ArrayList<>();
            r.json().forEach(list::add);
            table.getItems().setAll(list);
            show(null);
        });
    }

    private void show(JsonNode c) {
        if (c == null) {
            details.clear();
            approve.setDisable(true);
            reject.setDisable(true);
            proofPhoto.setDisable(true);
            editClaimant.setDisable(true);
            return;
        }
        JsonNode who = c.path("claimant");
        JsonNode item = c.path("item");
        StringBuilder sb = new StringBuilder();
        sb.append("CLAIM  [").append(c.path("status").asText()).append("]   submitted ")
          .append(when(c.path("createdAt").asText(""))).append("\n\n");
        sb.append("Proof from the claimant:\n").append(c.path("proofDescription").asText("(none)")).append("\n");
        if (!c.path("proofImageUrl").isMissingNode() && !c.path("proofImageUrl").isNull()
                && !c.path("proofImageUrl").asText("").isEmpty()) {
            sb.append("(A proof photo was uploaded.)\n");
        }
        sb.append("\nITEM (as registered by the finder)\n");
        sb.append("Name: ").append(item.path("name").asText("")).append("\n");
        sb.append("Description: ").append(item.path("description").asText("")).append("\n");
        sb.append("Color: ").append(item.path("color").asText("")).append("   Brand: ")
          .append(item.path("brand").asText("")).append("\n");
        sb.append("SECRET marks (staff only): ").append(item.path("distinguishingMarks").asText("")).append("\n");
        sb.append("Found at: ").append(item.path("locationName").asText("")).append("\n");
        sb.append("Left at: ").append(item.path("dropOffPlace").asText("")).append(" (")
          .append(item.path("dropOffPerson").asText("")).append(")\n");
        sb.append("\nCLAIMANT\n");
        sb.append(who.path("name").asText("")).append("   ID ").append(who.path("studentId").asText("")).append("\n");
        sb.append(who.path("email").asText("")).append("   ").append(who.path("phoneNum").asText("")).append("\n");
        String note = c.path("reviewNote").asText("");
        if (!note.isEmpty()) sb.append("\nReview note: ").append(note).append("\n");
        details.setText(sb.toString());

        boolean pending = "PENDING".equals(c.path("status").asText());
        approve.setDisable(!pending);
        reject.setDisable(!pending);
        boolean hasProof = !c.path("proofImageUrl").isNull() && !c.path("proofImageUrl").asText("").isEmpty();
        proofPhoto.setDisable(!hasProof);
        editClaimant.setDisable(who.path("id").asText("").isEmpty());
    }

    private void editClaimant() {
        JsonNode c = table.getSelectionModel().getSelectedItem();
        if (c == null) return;
        JsonNode who = c.path("claimant");
        String[] n = AuthDialogs.splitName(who.path("name").asText(""));
        AuthDialogs.editProfile(getScene().getWindow(), "Edit student profile",
                "Staff can change a student's name and phone number. Email, student ID and password stay as they are. "
                        + "The full name was split at the last space; check first and last name before saving.",
                "/v1/moderation/users/" + who.path("id").asText(), n[0], n[1],
                who.path("phoneNum").asText(""), this::load);
    }

    private void review(boolean approveIt) {
        JsonNode c = table.getSelectionModel().getSelectedItem();
        if (c == null) return;
        TextInputDialog d = new TextInputDialog();
        d.initOwner(getScene().getWindow());
        d.setTitle(approveIt ? "Approve claim" : "Reject claim");
        d.setHeaderText(approveIt ? "Optional note for the claimant:" : "Reason for rejecting (required):");
        Optional<String> result = d.showAndWait();
        if (result.isEmpty()) return;
        String note = result.get().trim();
        if (!approveIt && note.isEmpty()) {
            Ui.error("A reason is required when rejecting a claim.");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        if (!note.isEmpty()) body.put("note", note);
        String path = "/v1/moderation/claims/" + c.path("id").asText() + (approveIt ? "/approve" : "/reject");
        Ui.call(ApiClient.post(path, body), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }
}
