package com.cebims.view.screens;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.view.Reloadable;
import com.cebims.view.dialogs.StaffLostDetailDialog;
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

/** Staff: browse and search all missing equipment reports. */
public class StaffLostView extends BorderPane implements Reloadable {
    private final TableView<JsonNode> table = new TableView<>();
    private final ComboBox<String> status = new ComboBox<>();
    private final TextField search = new TextField();
    private final Label pageLabel = new Label();
    private final Button prev = new Button("< Prev");
    private final Button next = new Button("Next >");
    private int page = 0;
    private int totalPages = 1;

    public StaffLostView() {
        setPadding(new Insets(22, 28, 22, 28));

        status.getItems().addAll("OPEN", "MATCHED", "CLAIMED", "CLOSED", "EXPIRED");
        status.setValue("OPEN");
        status.setOnAction(e -> { page = 0; load(); });
        search.setPromptText("Search missing equipment reports...");
        search.setPrefWidth(260);
        search.setOnAction(e -> { page = 0; load(); });
        Button go = new Button("Search");
        go.setOnAction(e -> { page = 0; load(); });
        Button open = new Button("Inspect Report");
        open.getStyleClass().add("btn-primary");
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(e -> openDetail(table.getSelectionModel().getSelectedItem()));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> load());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, new Label("Status:"), status, search, go, open, spacer, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setTop(Page.top("All Missing Equipment Reports (Staff)",
                "Monitor all missing or unreturned equipment reports across campus with serial numbers and reporter contact info.", bar));

        table.setPlaceholder(new Label("No missing equipment reports with this status."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(Ui.col("Equipment", n -> Ui.t(n, "name"), 170));
        table.getColumns().add(Ui.col("Reporter", n -> n.path("reporter").path("name").asText(""), 150));
        table.getColumns().add(Ui.col("Student ID", n -> n.path("reporter").path("studentId").asText(""), 100));
        table.getColumns().add(Ui.col("Category", n -> Lookups.categoryName(Ui.t(n, "categoryId")), 110));
        table.getColumns().add(Ui.col("Last Seen At", n -> Ui.t(n, "locationName"), 160));
        table.getColumns().add(Ui.col("Date Missing", n -> Ui.t(n, "dateOccurred"), 100));
        table.getColumns().add(Ui.col("Photos", n -> String.valueOf(n.path("images").size()), 60));
        table.getColumns().add(Ui.statusCol("Status", n -> Ui.t(n, "status"), 90));
        table.setRowFactory(tv -> {
            TableRow<JsonNode> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) openDetail(row.getItem());
            });
            return row;
        });
        setCenter(table);

        prev.setOnAction(e -> { if (page > 0) { page--; load(); } });
        next.setOnAction(e -> { if (page + 1 < totalPages) { page++; load(); } });
        HBox paging = new HBox(10, prev, pageLabel, next);
        paging.setAlignment(Pos.CENTER);
        paging.setPadding(new Insets(10, 0, 0, 0));
        setBottom(paging);

        load();
    }

    @Override public void reload() { load(); }

    private void load() {
        String q = search.getText() == null ? "" : search.getText().trim();
        String path = "/v1/moderation/lost-items?status=" + status.getValue() + "&page=" + page + "&size=20"
                + (q.isEmpty() ? "" : "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        Ui.call(ApiClient.get(path), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            JsonNode root = r.json();
            List<JsonNode> list = new ArrayList<>();
            root.path("content").forEach(list::add);
            table.getItems().setAll(list);
            totalPages = Math.max(1, root.path("totalPages").asInt(1));
            pageLabel.setText("Page " + (page + 1) + " of " + totalPages
                    + "   (" + root.path("totalElements").asLong(list.size()) + " reports)");
            prev.setDisable(page == 0);
            next.setDisable(page + 1 >= totalPages);
        });
    }

    private void openDetail(JsonNode report) {
        if (report != null) StaffLostDetailDialog.open(getScene().getWindow(), report, this::load);
    }
}