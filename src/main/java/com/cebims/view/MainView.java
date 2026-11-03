package com.cebims.view;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cebims.api.ApiClient;
import com.cebims.api.Session;
import com.cebims.view.screens.AdminView;
import com.cebims.view.screens.DashboardView;
import com.cebims.view.screens.ItemsView;
import com.cebims.view.screens.LostReportsView;
import com.cebims.view.screens.ModerationView;
import com.cebims.view.screens.MyClaimsView;
import com.cebims.view.screens.NotificationsView;
import com.cebims.view.screens.ProfileView;
import com.cebims.view.screens.StaffLostView;
import com.cebims.view.util.Icons;
import com.cebims.view.util.ThemeManager;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * CEBIMS: Full-height dark left sidebar + right workspace with top search bar,
 * Sun/Moon theme toggle, and screen container.
 */
public class MainView extends BorderPane {
    private final Map<String, Node> views = new LinkedHashMap<>();
    private final Map<String, HBox> navButtons = new LinkedHashMap<>();
    private final StackPane contentArea = new StackPane();
    private final TextField quickSearch = new TextField();
    private final Label notifBadge = new Label("0");
    private final Label bellBadge = new Label("0");
    private final Timeline poller;

    public MainView() {
        getStyleClass().add("workspace-bg");
        contentArea.getStyleClass().add("workspace-bg");

        ItemsView openItemsView = new ItemsView(false);
        views.put("dashboard", new DashboardView(this::switchTo));
        views.put("found", openItemsView);
        views.put("my-found", new ItemsView(true));
        views.put("my-lost", new LostReportsView());
        views.put("my-claims", new MyClaimsView());
        views.put("notifications", new NotificationsView(this::refreshBadge));
        views.put("profile", new ProfileView());

        if (Session.isStaff()) {
            views.put("moderation", new ModerationView());
            views.put("staff-lost", new StaffLostView());
        }
       
        if ("Admin".equalsIgnoreCase(Session.role())) {
            views.put("admin-panel", new AdminView());
        }

        setLeft(buildSidebar());

        BorderPane rightWorkspace = new BorderPane();
        rightWorkspace.getStyleClass().add("workspace-bg");
        rightWorkspace.setTop(buildTopBar(openItemsView));
        rightWorkspace.setCenter(contentArea);
        setCenter(rightWorkspace);

        switchTo("dashboard");

        refreshBadge();
        poller = new Timeline(new KeyFrame(Duration.seconds(60), e -> refreshBadge()));
        poller.setCycleCount(Animation.INDEFINITE);
        poller.play();

        sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) {
                poller.stop();
            } else {
                scene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                        () -> quickSearch.requestFocus()
                );
            }
        });
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(245);

        StackPane logoIcon = Icons.badge(Icons.BOX, "#ffffff", "#2563eb", 20, 38, 10);

        Label brandTitle = new Label("CEBIMS");
        brandTitle.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 17px; -fx-font-weight: bold;");
        Label brandSub = new Label("Campus Equipment Borrowing &\nInventory Management System");
        brandSub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 10.5px;");
        HBox brandBox = new HBox(12, logoIcon, new VBox(1, brandTitle, brandSub));
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(0, 6, 14, 6));

        Label workSec = new Label("WORKSPACE");
        workSec.getStyleClass().add("sidebar-section");

        sidebar.getChildren().addAll(
                brandBox,
                workSec,
                navItem("dashboard", Icons.HOME, "Dashboard", null),
                navItem("found", Icons.BOX, "Equipment", null),
                navItem("my-claims", Icons.CHECK, "Borrowing Requests", null),
                navItem("my-found", Icons.DOC, "My Listed Equipment", null),
                navItem("my-lost", Icons.HELP, "Missing Reports", null),
                navItem("notifications", Icons.BELL, "Notifications", notifBadge)
        );

        if (Session.isStaff()) {
            Label staffSec = new Label("MODERATION");
            staffSec.getStyleClass().add("sidebar-section");
            sidebar.getChildren().addAll(
                    staffSec,
                    navItem("moderation", Icons.SHIELD, "Requests Review", null),
                    navItem("staff-lost", Icons.CLIPBOARD, "All Missing Reports", null)
            );
        }

        if ("Admin".equalsIgnoreCase(Session.role())) {
            Label adminSec = new Label("ADMINISTRATION");
            adminSec.getStyleClass().add("sidebar-section");
            sidebar.getChildren().addAll(
                    adminSec,
                    navItem("admin-panel", Icons.USER, "Users & Settings", null)
            );
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        StackPane campIcon = Icons.badge(Icons.HOME, "#15803d", "#dcfce7", 16, 32, 8);
        Label campName = new Label("Main Campus");
        campName.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 12.5px;");
        Label campSub = new Label("All systems operational");
        campSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        HBox campusCard = new HBox(10, campIcon, new VBox(1, campName, campSub));
        campusCard.setAlignment(Pos.CENTER_LEFT);
        campusCard.getStyleClass().add("campus-card");

        sidebar.getChildren().addAll(
                spacer,
                campusCard,
                navItem("profile", Icons.USER, "Settings", null)
        );
        return sidebar;
    }

    private HBox navItem(String key, String iconPath, String label, Label badge) {
        StackPane ic = Icons.badge(iconPath, "#94a3b8", null, 18, 20, 0);

        Label text = new Label(label);
        text.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 600; -fx-font-size: 13.5px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox row = new HBox(12, ic, text, sp);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("nav-btn");
        if (badge != null) {
            badge.setVisible(false);
            badge.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: #ffffff; -fx-font-size: 11px;"
                    + "-fx-font-weight: bold; -fx-padding: 1 7 1 7; -fx-background-radius: 999;");
            row.getChildren().add(badge);
        }
        row.setOnMouseClicked(e -> switchTo(key));
        navButtons.put(key, row);
        return row;
    }

    private HBox buildTopBar(ItemsView openItemsView) {
        StackPane searchIcon = Icons.badge(Icons.SEARCH, "#94a3b8", null, 15, 18, 0);

        quickSearch.setPromptText("Search equipment, users, or requests...");
        quickSearch.getStyleClass().add("search-input");
        HBox.setHgrow(quickSearch, Priority.ALWAYS);
        quickSearch.setOnAction(e -> {
            String q = quickSearch.getText();
            switchTo("found");
            openItemsView.searchFor(q);
        });

        Label cmdK = new Label("Ctrl+K");
        cmdK.getStyleClass().add("cmd-k-badge");

        HBox searchContainer = new HBox(8, searchIcon, quickSearch, cmdK);
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        searchContainer.setPrefWidth(420);
        searchContainer.setMaxWidth(460);
        searchContainer.setPadding(new Insets(5, 10, 5, 12));
        searchContainer.getStyleClass().add("search-container");

        quickSearch.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                searchContainer.getStyleClass().add("search-container-focused");
            } else {
                searchContainer.getStyleClass().remove("search-container-focused");
            }
        });

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        Button themeBtn = ThemeManager.createToggleButton(false);
        themeBtn.getStyleClass().add("icon-btn");

        StackPane bellIcon = Icons.box(Icons.BELL, null, 18);
        Button bellBtn = new Button();
        bellBtn.setGraphic(bellIcon);
        bellBtn.getStyleClass().add("icon-btn");
        bellBtn.setOnAction(e -> switchTo("notifications"));

        bellBadge.setVisible(false);
        bellBadge.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 10px;"
                + "-fx-font-weight: bold; -fx-padding: 1 5 1 5; -fx-background-radius: 999;");
        StackPane bellStack = new StackPane(bellBtn, bellBadge);
        StackPane.setAlignment(bellBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(bellBadge, new Insets(-4, -4, 0, 0));

        Separator vSep = new Separator(javafx.geometry.Orientation.VERTICAL);
        vSep.setMaxHeight(24);

        StackPane userAvatar = Icons.box(Icons.USER, null, 18);
        userAvatar.getStyleClass().add("user-avatar");

        Label uName = new Label(Session.displayName());
        uName.getStyleClass().add("user-name");
        Label uRole = new Label(Session.role());
        uRole.getStyleClass().add("muted-label");
        VBox uInfo = new VBox(0, uName, uRole);

        HBox userChip = new HBox(10, userAvatar, uInfo);
        userChip.setAlignment(Pos.CENTER_LEFT);
        userChip.setCursor(Cursor.HAND);
        userChip.setOnMouseClicked(e -> switchTo("profile"));

        StackPane outIcon = Icons.badge(Icons.LOGOUT, "#64748b", null, 15, 18, 0);
        Button logoutBtn = new Button("Log out", outIcon);
        logoutBtn.setOnAction(e -> logout());

        HBox top = new HBox(12, searchContainer, rightSpacer, themeBtn, bellStack, vSep, userChip, logoutBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");
        return top;
    }

    public void switchTo(String key) {
        Node view = views.get(key);
        if (view == null) return;

        navButtons.forEach((k, btn) -> {
            boolean active = k.equals(key);
            btn.getStyleClass().remove("nav-btn-active");
            if (active) btn.getStyleClass().add("nav-btn-active");

            String color = active ? "#ffffff" : "#94a3b8";
            for (Node child : btn.getChildren()) {
                if (child instanceof Label lbl && lbl != notifBadge) {
                    lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 600; -fx-font-size: 13.5px;");
                } else if (child instanceof StackPane iconPane && !iconPane.getChildren().isEmpty()
                        && iconPane.getChildren().get(0) instanceof Group g && !g.getChildren().isEmpty()
                        && g.getChildren().get(0) instanceof SVGPath svg) {
                    svg.setStroke(Color.web(color));
                }
            }
        });

        contentArea.getChildren().setAll(view);
        if (view instanceof Reloadable r) r.reload();
    }

    private void refreshBadge() {
        if (!Session.loggedIn()) return;
        ApiClient.get("/v1/notifications/unread-count").whenComplete((r, ex) -> Platform.runLater(() -> {
            if (ex != null || !r.ok()) return;
            long n = r.json().path("count").asLong(0);
            notifBadge.setText(String.valueOf(n));
            notifBadge.setVisible(n > 0);
            bellBadge.setText(String.valueOf(n));
            bellBadge.setVisible(n > 0);
        }));
    }

    private void logout() {
        poller.stop();
        String refresh = Session.refreshToken();
        if (refresh != null) {
            ApiClient.post("/v1/auth/logout", Map.of("refreshToken", refresh));
        }
        Session.clear();
        SceneManager.showLogin();
    }
}