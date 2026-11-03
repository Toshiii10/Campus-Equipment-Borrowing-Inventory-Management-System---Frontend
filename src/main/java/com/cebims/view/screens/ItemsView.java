package com.cebims.view.screens;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.view.Reloadable;
import com.cebims.view.dialogs.ItemDetailDialog;
import com.cebims.view.dialogs.ItemFormDialog;
import com.cebims.view.dialogs.PhotosDialog;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

/**
 * CEBIMS Equipment Inventory View (backed by /v1/items and /v1/items/mine) with CSV Export.
 */
public class ItemsView extends BorderPane implements Reloadable {
    private final boolean mine;
    private final TableView<JsonNode> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<Lookups.Option> category = new ComboBox<>();
    private final Label pageLabel = new Label();
    private final Button prev = new Button("< Prev");
    private final Button next = new Button("Next >");
    private int page = 0;
    private int totalPages = 1;

    public ItemsView(boolean mine) {
        this.mine = mine;
        setPadding(new Insets(22, 28, 22, 28));

        table.setPlaceholder(new Label("No equipment found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(Ui.col("Equipment", n -> Ui.t(n, "name"), 180));
        table.getColumns().add(Ui.col("Category", n -> Lookups.categoryName(Ui.t(n, "categoryId")), 120));
        table.getColumns().add(Ui.col("Color", n -> Ui.t(n, "color"), 90));
        table.getColumns().add(Ui.col("Brand / Model", n -> Ui.t(n, "brand"), 110));
        table.getColumns().add(Ui.col("Location", n -> Ui.t(n, "locationName"), 160));
        table.getColumns().add(Ui.col("Pickup Desk", n -> Lookups.dropName(Ui.t(n, "dropLocationId")), 140));
        table.getColumns().add(Ui.col("Date Logged", n -> Ui.t(n, "dateOccurred"), 100));
        table.getColumns().add(Ui.col("Photos", n -> String.valueOf(n.path("images").size()), 60));
        table.getColumns().add(Ui.statusCol("Status", n -> Ui.t(n, "status"), 90));
        setCenter(table);

        Button exportCsv = new Button("Export CSV");
        exportCsv.setOnAction(e -> exportToCsv());

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));

        if (mine) {
            Button add = new Button("+ Add Equipment");
            add.getStyleClass().add("btn-primary");
            Button edit = new Button("Edit");
            Button photos = new Button("Photos");
            Button del = new Button("Delete");
            for (Button b : List.of(edit, photos, del)) {
                b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            }
            add.setOnAction(e -> ItemFormDialog.open(getScene().getWindow(), null, false, this::load));
            edit.setOnAction(e -> ItemFormDialog.open(getScene().getWindow(),
                    table.getSelectionModel().getSelectedItem(), false, this::load));
            photos.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem();
                PhotosDialog.open(getScene().getWindow(), Ui.t(sel, "id"), false, sel.path("images"), this::load);
            });
            del.setOnAction(e -> delete(table.getSelectionModel().getSelectedItem()));
            bar.getChildren().addAll(add, edit, photos, del, spacer, exportCsv, refresh);
        } else {
            search.setPromptText("Search equipment by name, brand, or color...");
            search.setPrefWidth(280);
            category.getItems().add(new Lookups.Option(null, "All categories"));
            category.getItems().addAll(Lookups.categories());
            category.setValue(category.getItems().get(0));
            category.setOnAction(e -> { page = 0; load(); });
            Button go = new Button("Search");
            go.setOnAction(e -> { page = 0; load(); });
            search.setOnAction(e -> { page = 0; load(); });
            Button view = new Button("View / Request");
            view.getStyleClass().add("btn-primary");
            view.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            view.setOnAction(e -> openDetail(table.getSelectionModel().getSelectedItem()));
            bar.getChildren().addAll(search, category, go, view, spacer, exportCsv, refresh);

            table.setRowFactory(tv -> {
                TableRow<JsonNode> row = new TableRow<>();
                row.setOnMouseClicked(ev -> {
                    if (ev.getClickCount() == 2 && !row.isEmpty()) openDetail(row.getItem());
                });
                return row;
            });

            prev.setOnAction(e -> { if (page > 0) { page--; load(); } });
            next.setOnAction(e -> { if (page + 1 < totalPages) { page++; load(); } });
            HBox paging = new HBox(10, prev, pageLabel, next);
            paging.setAlignment(Pos.CENTER);
            paging.setPadding(new Insets(10, 0, 0, 0));
            setBottom(paging);
        }
        setTop(Page.top(
                mine ? "My Listed Equipment" : "Campus Equipment Inventory",
                mine ? "Equipment items you registered. Edit or remove them while they are open with no pending request."
                     : "Browse available campus equipment. Double-click an item to inspect photos and submit a request.",
                bar));
        load();
    }

    @Override
    public void reload() {
        if (!mine) {
            Lookups.Option prevSel = category.getValue();
            category.getItems().setAll(new Lookups.Option(null, "All categories"));
            category.getItems().addAll(Lookups.categories());
            category.setValue(prevSel != null ? prevSel : category.getItems().get(0));
        }
        load();
    }

    public void searchFor(String q) {
        search.setText(q == null ? "" : q.trim());
        page = 0;
        load();
    }

    private void load() {
        String path;
        if (mine) {
            path = "/v1/items/mine";
        } else {
            String q = search.getText() == null ? "" : search.getText().trim();
            path = "/v1/items?page=" + page + "&size=20"
                    + (q.isEmpty() ? "" : "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
            Lookups.Option c = category.getValue();
            if (c != null && c.id() != null) path += "&categoryId=" + URLEncoder.encode(c.id(), StandardCharsets.UTF_8);
        }
        Ui.call(ApiClient.get(path), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            JsonNode root = r.json();
            JsonNode rows = mine ? root : root.path("content");
            List<JsonNode> list = new ArrayList<>();
            rows.forEach(list::add);
            table.getItems().setAll(list);
            if (!mine) {
                totalPages = Math.max(1, root.path("totalPages").asInt(1));
                pageLabel.setText("Page " + (page + 1) + " of " + totalPages
                        + "   (" + root.path("totalElements").asLong(list.size()) + " items)");
                prev.setDisable(page == 0);
                next.setDisable(page + 1 >= totalPages);
            }
        });
    }

    private void exportToCsv() {
        if (table.getItems().isEmpty()) {
            Ui.info("There are no equipment rows in the table to export.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Equipment Inventory to CSV");
        fc.setInitialFileName(mine ? "my-listed-equipment.csv" : "cebims-equipment-inventory.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Equipment,Category,Color,Brand/Model,Location,Pickup Desk,Date Logged,Photos,Status\n");
        for (JsonNode n : table.getItems()) {
            sb.append(csvCell(Ui.t(n, "name"))).append(',')
              .append(csvCell(Lookups.categoryName(Ui.t(n, "categoryId")))).append(',')
              .append(csvCell(Ui.t(n, "color"))).append(',')
              .append(csvCell(Ui.t(n, "brand"))).append(',')
              .append(csvCell(Ui.t(n, "locationName"))).append(',')
              .append(csvCell(Lookups.dropName(Ui.t(n, "dropLocationId")))).append(',')
              .append(csvCell(Ui.t(n, "dateOccurred"))).append(',')
              .append(n.path("images").size()).append(',')
              .append(csvCell(Ui.t(n, "status"))).append('\n');
        }
        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            Ui.info("Exported " + table.getItems().size() + " equipment records to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            Ui.error("Could not save CSV file: " + ex.getMessage());
        }
    }

    private static String csvCell(String val) {
        String safe = val == null ? "" : val.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private void openDetail(JsonNode item) {
        if (item != null) ItemDetailDialog.open(getScene().getWindow(), item);
    }

    private void delete(JsonNode item) {
        if (item == null) return;
        if (!Ui.confirm("Delete \"" + Ui.t(item, "name") + "\"?\nThis only works while it is OPEN with no pending request.")) return;
        Ui.call(ApiClient.delete("/v1/items/" + Ui.t(item, "id")), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }
}