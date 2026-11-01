package com.cebims.view.screens;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.view.Reloadable;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * CEBIMS In-app notifications & alerts.
 */
public class NotificationsView extends BorderPane implements Reloadable {
    private final TableView<JsonNode> table = new TableView<>();
    private final Label pageLabel = new Label();
    private final Button prev = new Button("< Prev");
    private final Button next = new Button("Next >");
    private final Runnable onChanged;
    private int page = 0;
    private int totalPages = 1;

    public NotificationsView(Runnable onChanged) {
        this.onChanged = onChanged;
        setPadding(new Insets(22, 28, 22, 28));

        table.setPlaceholder(new Label("No alerts or notifications."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(Ui.col("", n -> n.path("read").asBoolean(true) ? "" : "\u25CF", 30));
        table.getColumns().add(Ui.col("Timestamp", n -> Ui.when(Ui.t(n, "createdAt")), 140));
        table.getColumns().add(Ui.col("Category", n -> "MATCH_FOUND".equals(Ui.t(n, "type")) ? "Inventory Match" : "Request Update", 130));
        table.getColumns().add(Ui.col("Alert Message", n -> n.path("payload").path("message").asText(""), 560));
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(JsonNode n, boolean empty) {
                super.updateItem(n, empty);
                boolean unread = !empty && n != null && !n.path("read").asBoolean(true);
                setStyle(unread ? "-fx-font-weight: bold;" : "");
            }
        });

        Button markRead = new Button("Mark as read");
        Button markAll = new Button("Mark all as read");
        Button refresh = new Button("Refresh");
        markRead.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        markRead.setOnAction(e -> markRead());
        markAll.setOnAction(e -> markAll());
        refresh.setOnAction(e -> load());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, markRead, markAll, spacer, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));
        setTop(Page.top("Notifications & Alerts",
                "Real-time updates on your equipment borrowing requests and inventory match alerts.", bar));
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
        Ui.call(ApiClient.get("/v1/notifications?page=" + page + "&size=20"), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            JsonNode root = r.json();
            List<JsonNode> list = new ArrayList<>();
            root.path("content").forEach(list::add);
            table.getItems().setAll(list);
            totalPages = Math.max(1, root.path("totalPages").asInt(1));
            pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
            prev.setDisable(page == 0);
            next.setDisable(page + 1 >= totalPages);
            onChanged.run();
        });
    }

    private void markRead() {
        JsonNode n = table.getSelectionModel().getSelectedItem();
        if (n == null) return;
        Ui.call(ApiClient.post("/v1/notifications/" + Ui.t(n, "id") + "/read", null), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }

    private void markAll() {
        Ui.call(ApiClient.post("/v1/notifications/read-all", null), r -> {
            if (r.ok()) load();
            else Ui.error(r.errorMessage());
        });
    }
}