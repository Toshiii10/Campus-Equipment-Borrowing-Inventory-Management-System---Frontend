package com.cebims.view.dialogs;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.Lookups;
import com.cebims.view.util.Ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Missing equipment report as staff see it: private marks, reporter's contact details, and private photos. */
public final class StaffLostDetailDialog {
    private StaffLostDetailDialog() {}

    public static void open(Window owner, JsonNode report, Runnable onProfileSaved) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Missing equipment report (staff view)");
        ButtonType editType = new ButtonType("Edit reporter profile...", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(editType, ButtonType.CLOSE);

        JsonNode who = report.path("reporter");
        StringBuilder sb = new StringBuilder();
        sb.append("Equipment: ").append(Ui.t(report, "name")).append("   [").append(Ui.t(report, "status")).append("]\n");
        sb.append("Description: ").append(Ui.t(report, "description")).append("\n");
        sb.append("Category: ").append(Lookups.categoryName(Ui.t(report, "categoryId"))).append("\n");
        sb.append("Color: ").append(Ui.t(report, "color")).append("   Brand: ").append(Ui.t(report, "brand")).append("\n");
        sb.append("PRIVATE marks: ").append(Ui.t(report, "distinguishingMarks")).append("\n");
        sb.append("Missing from: ").append(Ui.t(report, "locationName")).append("   on ").append(Ui.t(report, "dateOccurred")).append("\n");
        sb.append("Filed: ").append(Ui.when(Ui.t(report, "createdAt"))).append("\n\n");
        sb.append("REPORTER\n");
        sb.append(who.path("name").asText("")).append("   ID ").append(who.path("studentId").asText("")).append("\n");
        sb.append(who.path("email").asText("")).append("   ").append(who.path("phoneNum").asText(""));

        TextArea text = new TextArea(sb.toString());
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(11);
        text.setPrefWidth(560);
        text.setStyle("-fx-font-family: monospace;");

        VBox content = new VBox(10, text, new Label("Photos (private, click to enlarge)"),
                Ui.photoStrip(owner, report.path("images")));
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Button edit = (Button) dialog.getDialogPane().lookupButton(editType);
        edit.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            String[] n = AuthDialogs.splitName(who.path("name").asText(""));
            AuthDialogs.editProfile(Ui.windowOf(dialog), "Edit user profile",
                    "Staff can change a user's name and phone number. Email, student ID and password stay as they are. "
                            + "The full name was split at the last space; check first and last name before saving.",
                    "/v1/moderation/users/" + who.path("id").asText(), n[0], n[1],
                    who.path("phoneNum").asText(""), onProfileSaved);
        });
        edit.setDisable(who.path("id").asText("").isEmpty());
        dialog.show();
    }
}