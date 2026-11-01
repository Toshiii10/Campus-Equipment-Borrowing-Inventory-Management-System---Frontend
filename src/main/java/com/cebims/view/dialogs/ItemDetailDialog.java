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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Public details and photos of equipment, with a Request button. */
public final class ItemDetailDialog {
    private ItemDetailDialog() {}

    public static void open(Window owner, JsonNode item) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Equipment Details");
        ButtonType claimType = new ButtonType("Request Equipment...", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(claimType, ButtonType.CLOSE);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(6);
        int r = 0;
        r = row(g, r, "Equipment", Ui.t(item, "name"));
        r = row(g, r, "Description", Ui.t(item, "description"));
        r = row(g, r, "Category", Lookups.categoryName(Ui.t(item, "categoryId")));
        r = row(g, r, "Color", Ui.t(item, "color"));
        r = row(g, r, "Brand/Model", Ui.t(item, "brand"));
        r = row(g, r, "Location", Ui.t(item, "locationName"));
        r = row(g, r, "Pickup Desk", Lookups.dropName(Ui.t(item, "dropLocationId")));
        r = row(g, r, "Date logged", Ui.t(item, "dateOccurred"));
        row(g, r, "Status", Ui.t(item, "status"));

        VBox content = new VBox(10, g, new Label("Photos"), Ui.photoStrip(owner, item.path("images")));
        content.setPadding(new Insets(10));
        content.setMinWidth(520);
        dialog.getDialogPane().setContent(content);

        Button claim = (Button) dialog.getDialogPane().lookupButton(claimType);
        claim.setDisable(!"OPEN".equals(Ui.t(item, "status")));
        claim.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume(); // keep the detail window open behind the claim form
            ClaimDialog.open(Ui.windowOf(dialog), Ui.t(item, "id"), Ui.t(item, "name"), null);
        });
        dialog.show();
    }

    private static int row(GridPane g, int r, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-font-weight: bold;");
        Label v = new Label(value);
        v.setWrapText(true);
        v.setMaxWidth(380);
        g.addRow(r, l, v);
        return r + 1;
    }
}