package com.cebims.view.screens;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Session;
import com.cebims.view.Reloadable;
import com.cebims.view.dialogs.ItemDetailDialog;
import com.cebims.view.util.Icons;
import com.cebims.view.util.Ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;

/**
 * CEBIMS Home screen: welcome header, four stat cards, equipment table, donut chart,
 * recent requests/transactions, and notifications.
 */
public class DashboardView extends ScrollPane implements Reloadable {
    private final boolean staff = Session.isStaff();
    private final Consumer<String> navigate;

    private final StatCard[] cards = new StatCard[4];
    private final TableView<JsonNode> recentItems = new TableView<>();
    private final TableView<JsonNode> bottomTable = new TableView<>();
    private final StackPane donutHolder = new StackPane();
    private final VBox legend = new VBox(8);
    private final VBox notifBox = new VBox(10);

    public DashboardView(Consumer<String> navigate) {
        this.navigate = navigate;
        URL css = DashboardView.class.getResource("/dashboard.css");
        if (css != null) getStylesheets().add(css.toExternalForm());
        getStyleClass().add("db-scroll");
        setFitToWidth(true);

        // ----- header
        String first = Session.displayName() == null ? "" : Session.displayName().split(" ")[0];
        Label welcome = new Label("Welcome back, " + first + "!");
        welcome.getStyleClass().add("db-welcome");
        Label sub = new Label("Here's what's happening with your equipment today.");
        sub.getStyleClass().add("db-welcome-sub");
        Label date = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        date.getStyleClass().add("db-date");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(new VBox(2, welcome, sub), sp, date);
        header.setAlignment(Pos.TOP_LEFT);

        // ----- stat cards (CEBIMS labels)
        if (staff) {
            cards[0] = new StatCard(Icons.BOX, "db-tile-blue", "Total Equipment", "On track", "db-pill-blue");
            cards[1] = new StatCard(Icons.CLIPBOARD, "db-tile-orange", "Pending Requests", "In queue", "db-pill-orange");
            cards[2] = new StatCard(Icons.CHECK, "db-tile-green", "My Active Requests", "On track", "db-pill-green");
            cards[3] = new StatCard(Icons.HELP, "db-tile-red", "Missing Reports", "Needs action", "db-pill-red");
        } else {
            cards[0] = new StatCard(Icons.BOX, "db-tile-blue", "Total Equipment", "On track", "db-pill-blue");
            cards[1] = new StatCard(Icons.DOC, "db-tile-green", "My Listed Equipment", "Active", "db-pill-green");
            cards[2] = new StatCard(Icons.HELP, "db-tile-orange", "Missing Reports", "In queue", "db-pill-orange");
            cards[3] = new StatCard(Icons.CHECK, "db-tile-blue", "Borrowing Requests", "On track", "db-pill-blue");
        }
        HBox cardsRow = new HBox(16, cards);
        for (StatCard c : cards) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
            c.setPrefWidth(200);
        }

        // ----- tables
        setupTable(recentItems);
        recentItems.getColumns().add(Ui.col("EQUIPMENT", n -> Ui.t(n, "name"), 170));
        recentItems.getColumns().add(Ui.col("LOCATION", n -> Ui.t(n, "locationName"), 150));
        recentItems.getColumns().add(Ui.col("DATE", n -> Ui.t(n, "dateOccurred"), 90));
        recentItems.getColumns().add(Ui.statusCol("STATUS", n -> Ui.t(n, "status"), 90));
        recentItems.setRowFactory(tv -> {
            TableRow<JsonNode> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) ItemDetailDialog.open(getScene().getWindow(), row.getItem());
            });
            return row;
        });

        setupTable(bottomTable);
        if (staff) {
            bottomTable.getColumns().add(Ui.col("BORROWER", n -> n.path("claimant").path("name").asText(""), 150));
            bottomTable.getColumns().add(Ui.col("EQUIPMENT", n -> n.path("item").path("name").asText(""), 170));
            bottomTable.getColumns().add(Ui.col("REQUESTED", n -> Ui.when(Ui.t(n, "createdAt")), 130));
        } else {
            bottomTable.getColumns().add(Ui.col("EQUIPMENT", n -> ItemNames.get(Ui.t(n, "itemId")), 170));
            bottomTable.getColumns().add(Ui.statusCol("STATUS", n -> Ui.t(n, "status"), 100));
            bottomTable.getColumns().add(Ui.col("REQUESTED", n -> Ui.when(Ui.t(n, "createdAt")), 130));
        }

        // ----- panels
        VBox pRecent = panel("Available campus equipment", "Latest registered equipment ready for request", "Inventory",
                () -> navigate.accept("found"), recentItems);

        donutHolder.setMinHeight(160);
        HBox donutRow = new HBox(22, donutHolder, legend);
        donutRow.setAlignment(Pos.CENTER_LEFT);
        VBox pDonut = panel("Request distribution", "Live status of your borrowing requests", "Requests",
                () -> navigate.accept("my-claims"), donutRow);

        VBox pBottom = staff
                ? panel("Recent borrowing requests", "Review and manage incoming equipment requests", "View all",
                        () -> navigate.accept("moderation"), bottomTable)
                : panel("Recent transactions", "Latest equipment requests you submitted", "Transactions",
                        () -> navigate.accept("my-claims"), bottomTable);

        VBox pNotifs = panel("Due today & alerts", "Time-sensitive activity and notifications", "Review",
                () -> navigate.accept("notifications"), notifBox);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(62);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(38);
        grid.getColumnConstraints().addAll(left, right);
        grid.add(pRecent, 0, 0);
        grid.add(pDonut, 1, 0);
        grid.add(pBottom, 0, 1);
        grid.add(pNotifs, 1, 1);

        VBox content = new VBox(18, header, cardsRow, grid);
        content.setPadding(new Insets(22, 26, 26, 26));
        setContent(content);

        load();
    }

    @Override public void reload() { load(); }

    private void load() {
        quiet(ApiClient.get("/v1/items?page=0&size=5"), r -> {
            JsonNode root = r.json();
            recentItems.getItems().setAll(list(root.path("content")));
            cards[0].set(root.path("totalElements").asLong(0), "available in inventory", "db-sub-green");
        });

        quiet(ApiClient.get("/v1/claims/mine"), r -> {
            List<JsonNode> claims = list(r.json());
            long pending = count(claims, "PENDING");
            StatCard target = staff ? cards[2] : cards[3];
            target.set(claims.size(), pending + " awaiting review", pending > 0 ? "db-sub-orange" : "db-sub-muted");
            target.setTag(pending > 0 ? "In queue" : "On track", pending > 0 ? "db-pill-orange" : "db-pill-green");
            drawDonut(claims);
            if (!staff) {
                List<JsonNode> latest = new ArrayList<>(claims.subList(0, Math.min(5, claims.size())));
                bottomTable.getItems().setAll(latest);
                for (JsonNode cl : latest) ItemNames.resolve(Ui.t(cl, "itemId"), bottomTable::refresh);
            }
        });

        if (staff) {
            quiet(ApiClient.get("/v1/moderation/claims?status=PENDING"), r -> {
                List<JsonNode> pending = list(r.json());
                cards[1].set(pending.size(), pending.isEmpty() ? "all clear" : "awaiting review", pending.isEmpty() ? "db-sub-muted" : "db-sub-orange");
                cards[1].setTag(pending.isEmpty() ? "On track" : "In queue", pending.isEmpty() ? "db-pill-green" : "db-pill-orange");
                bottomTable.getItems().setAll(new ArrayList<>(pending.subList(0, Math.min(5, pending.size()))));
            });
            quiet(ApiClient.get("/v1/moderation/lost-items?status=OPEN&page=0&size=1"), r ->
                    cards[3].set(r.json().path("totalElements").asLong(0), "high priority", "db-sub-red"));
        } else {
            quiet(ApiClient.get("/v1/items/mine"), r -> {
                List<JsonNode> mine = list(r.json());
                cards[1].set(mine.size(), count(mine, "OPEN") + " currently open", "db-sub-muted");
            });
            quiet(ApiClient.get("/v1/lost-items/mine"), r -> {
                List<JsonNode> mine = list(r.json());
                long matched = count(mine, "MATCHED");
                cards[2].set(mine.size(), matched + " with suggested matches", matched > 0 ? "db-sub-green" : "db-sub-muted");
            });
        }

        quiet(ApiClient.get("/v1/notifications?page=0&size=4"), r -> {
            notifBox.getChildren().clear();
            List<JsonNode> rows = list(r.json().path("content"));
            if (rows.isEmpty()) {
                Label none = new Label("No alerts or notifications yet.");
                none.getStyleClass().add("db-empty-note");
                notifBox.getChildren().add(none);
            }
            for (JsonNode n : rows) {
                boolean read = n.path("read").asBoolean(true);
                Circle dot = new Circle(4);
                dot.getStyleClass().add(read ? "db-notif-dot-read" : "db-notif-dot");
                Label msg = new Label(n.path("payload").path("message").asText(""));
                msg.setWrapText(true);
                msg.getStyleClass().add("db-notif-msg");
                if (!read) msg.setStyle("-fx-font-weight: bold;");
                Label time = new Label(Ui.when(Ui.t(n, "createdAt")));
                time.getStyleClass().add("db-sub-muted");
                VBox text = new VBox(2, msg, time);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(10, dot, text);
                row.setAlignment(Pos.TOP_LEFT);
                notifBox.getChildren().add(row);
            }
        });
    }

    private static void quiet(CompletableFuture<ApiClient.Response> request, Consumer<ApiClient.Response> onOk) {
        request.whenComplete((r, e) -> Platform.runLater(() -> {
            if (e == null && r.ok()) onOk.accept(r);
        }));
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> out = new ArrayList<>();
        array.forEach(out::add);
        return out;
    }

    private static long count(List<JsonNode> rows, String status) {
        return rows.stream().filter(n -> status.equals(Ui.t(n, "status"))).count();
    }

    private void drawDonut(List<JsonNode> claims) {
        String[] names = {"APPROVED", "PENDING", "REJECTED", "CANCELLED"};
        String[] labels = {"Approved", "Pending", "Rejected", "Cancelled"};
        Color[] colors = {Color.web("#1E9E63"), Color.web("#F59E0B"), Color.web("#E5484D"), Color.web("#94A3B8")};
        long total = claims.size();

        Pane ring = new Pane();
        ring.setPrefSize(150, 150);
        ring.setMinSize(150, 150);
        ring.setMaxSize(150, 150);
        ring.getChildren().add(arc(0, 360, Color.web("#E9EEF5")));

        legend.getChildren().clear();
        double start = 90;
        for (int i = 0; i < names.length; i++) {
            long n = count(claims, names[i]);
            if (total > 0 && n > 0) {
                double sweep = 360.0 * n / total;
                ring.getChildren().add(arc(start, -sweep, colors[i]));
                start -= sweep;
            }
            Circle dot = new Circle(5, colors[i]);
            Label name = new Label(labels[i]);
            name.getStyleClass().add("db-legend-text");
            Region fill = new Region();
            HBox.setHgrow(fill, Priority.ALWAYS);
            Label num = new Label(String.valueOf(n));
            num.getStyleClass().add("db-legend-count");
            HBox row = new HBox(8, dot, name, fill, num);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMinWidth(150);
            legend.getChildren().add(row);
        }

        Label big = new Label(String.valueOf(total));
        big.getStyleClass().add("db-donut-total");
        Label cap = new Label(total == 1 ? "REQUEST" : "REQUESTS");
        cap.getStyleClass().add("db-donut-caption");
        VBox centre = new VBox(0, big, cap);
        centre.setAlignment(Pos.CENTER);
        centre.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        donutHolder.getChildren().setAll(ring, centre);
    }

    private static Arc arc(double start, double length, Color color) {
        Arc a = new Arc(75, 75, 58, 58, start, length);
        a.setType(ArcType.OPEN);
        a.setFill(Color.TRANSPARENT);
        a.setStroke(color);
        a.setStrokeWidth(18);
        return a;
    }

    private static void setupTable(TableView<JsonNode> t) {
        t.getStyleClass().add("db-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setFixedCellSize(40);
        t.setPrefHeight(240);
        t.setMinHeight(240);
        t.setPlaceholder(new Label("Nothing here yet."));
    }

    private static VBox panel(String title, String subtitle, String actionText, Runnable action, Node body) {
        Label t = new Label(title);
        t.getStyleClass().add("db-panel-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("db-panel-sub");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(new VBox(2, t, s), sp);
        head.setAlignment(Pos.TOP_LEFT);
        if (actionText != null) {
            Button b = new Button(actionText);
            b.setOnAction(e -> action.run());
            head.getChildren().add(b);
        }
        VBox box = new VBox(12, head, body);
        box.getStyleClass().add("card");
        return box;
    }

    private static final class StatCard extends VBox {
        private final Label number = new Label("-");
        private final Label sub = new Label(" ");
        private final Label tag = new Label();

        StatCard(String iconPath, String tileClass, String label, String tagText, String tagClass) {
            super(6);
            getStyleClass().add("card");
            StackPane tile = Icons.box(iconPath, null, 22);
            tile.getStyleClass().addAll("db-tile", tileClass);
            tile.setPrefSize(40, 40);
            tile.setMinSize(40, 40);
            tile.setMaxSize(40, 40);
            tag.setText(tagText);
            tag.getStyleClass().addAll("db-pill", tagClass);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox top = new HBox(tile, sp, tag);
            top.setAlignment(Pos.TOP_LEFT);
            number.getStyleClass().add("db-stat-number");
            Label name = new Label(label);
            name.getStyleClass().add("db-stat-label");
            sub.getStyleClass().add("db-sub-muted");
            getChildren().addAll(top, number, name, sub);
        }

        void set(long n, String subText, String subClass) {
            number.setText(String.valueOf(n));
            sub.setText(subText);
            sub.getStyleClass().setAll(subClass);
        }

        void setTag(String text, String pillClass) {
            tag.setText(text);
            tag.getStyleClass().setAll("db-pill", pillClass);
        }
    }
}