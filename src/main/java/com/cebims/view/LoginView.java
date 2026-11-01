package com.cebims.view;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.api.Session;
import com.cebims.view.dialogs.AuthDialogs;
import com.cebims.view.util.Icons;
import com.cebims.view.util.ThemeManager;
import com.cebims.view.util.Ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Split-screen CEBIMS-themed authentication screen using pure vector SVG icons and Light/Dark mode.
 */
public class LoginView extends BorderPane {
    private final TextField email = new TextField();
    private final PasswordField password = new PasswordField();
    private final Button loginBtn = new Button("Sign in to Workspace");
    private final Label errorBanner = new Label();

    public LoginView() {
        getStyleClass().add("workspace-bg");
        setLeft(buildBrandSidebar());
        setCenter(buildLoginWorkspace());
    }

    private VBox buildBrandSidebar() {
        VBox side = new VBox(18);
        side.setPrefWidth(430);
        side.setPadding(new Insets(40, 36, 34, 36));
        side.setStyle("-fx-background-color: #0f172a; -fx-border-color: transparent #1e293b transparent transparent; -fx-border-width: 1;");

        // Brand Logo Header (Vector Box Icon)
        StackPane logoIcon = Icons.badge(Icons.BOX, "#ffffff", "#2563eb", 22, 44, 12);
        Label brandTitle = new Label("CEBIMS");
        brandTitle.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 20px; -fx-font-weight: bold;");
        Label brandSub = new Label("Campus Equipment Borrowing & Inventory Management System");
        brandSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        HBox brandHeader = new HBox(14, logoIcon, new VBox(2, brandTitle, brandSub));
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        // Hero Headline
        Label pill = new Label("CAMPUS PORTAL");
        pill.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #60a5fa; -fx-font-size: 11px;"
                + "-fx-font-weight: bold; -fx-padding: 4 12 4 12; -fx-background-radius: 999;");

        Label headline = new Label("Smart recovery for campus belongings.");
        headline.setWrapText(true);
        headline.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 26px; -fx-font-weight: bold;");

        Label desc = new Label("Report found items, file private lost reports, and get automated match suggestions backed by verified staff review.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        VBox heroText = new VBox(10, pill, headline, desc);
        heroText.setPadding(new Insets(20, 0, 8, 0));

        // Feature Highlights with Vector Icons
        VBox features = new VBox(12,
                featureRow(Icons.SEARCH, "#2563eb", "#dbeafe", "Automated Smart Matching",
                        "Instant scoring across category, brand, color, and date"),
                featureRow(Icons.SHIELD, "#15803d", "#dcfce7", "Verified Claim Protection",
                        "Photo proof and distinguishing marks checked by staff"),
                featureRow(Icons.BELL, "#b45309", "#fef3c7", "Real-Time Alerts & Pickup",
                        "In-app and email updates with drop-off location details")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Bottom Campus Operational Card
        StackPane campIcon = Icons.badge(Icons.HOME, "#15803d", "#dcfce7", 16, 34, 8);
        Label campName = new Label("Main Campus");
        campName.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label campSub = new Label("All systems operational");
        campSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11.5px;");

        Label statusDot = new Label("Online");
        statusDot.setStyle("-fx-background-color: #064e3b; -fx-text-fill: #34d399; -fx-font-size: 11px;"
                + "-fx-font-weight: bold; -fx-padding: 3 10 3 10; -fx-background-radius: 999;");
        Region cardSpacer = new Region();
        HBox.setHgrow(cardSpacer, Priority.ALWAYS);

        HBox campusCard = new HBox(12, campIcon, new VBox(1, campName, campSub), cardSpacer, statusDot);
        campusCard.setAlignment(Pos.CENTER_LEFT);
        campusCard.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 12; -fx-padding: 14;");

        side.getChildren().addAll(brandHeader, heroText, features, spacer, campusCard);
        return side;
    }

    private HBox featureRow(String iconPath, String iconStroke, String iconBg, String title, String sub) {
        StackPane ic = Icons.badge(iconPath, iconStroke, iconBg, 16, 34, 8);
        Label t = new Label(title);
        t.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label s = new Label(sub);
        s.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11.5px;");
        s.setWrapText(true);
        HBox row = new HBox(12, ic, new VBox(2, t, s));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 10; -fx-padding: 11 14 11 14;");
        return row;
    }

    private BorderPane buildLoginWorkspace() {
        BorderPane rightPane = new BorderPane();
        rightPane.getStyleClass().add("workspace-bg");

        // Top Bar inside Login Workspace for Theme Toggle Button
        Button themeBtn = ThemeManager.createToggleButton(true);
        HBox topRow = new HBox(themeBtn);
        topRow.setAlignment(Pos.CENTER_RIGHT);
        topRow.setPadding(new Insets(18, 28, 0, 28));
        rightPane.setTop(topRow);

        // Centered Login Card
        VBox card = new VBox(16);
        card.getStyleClass().add("card");
        card.setMaxWidth(420);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(32, 34, 28, 34));

        Label badge = new Label("AUTHENTICATION");
        badge.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; -fx-font-size: 10.5px;"
                + "-fx-font-weight: bold; -fx-padding: 3 10 3 10; -fx-background-radius: 999;");

        Label title = new Label("Welcome back!");
        title.getStyleClass().add("page-title");

        Label sub = new Label("Enter your campus credentials to access your dashboard.");
        sub.getStyleClass().add("page-sub");
        sub.setWrapText(true);

        VBox header = new VBox(4, badge, title, sub);

        Label emailLbl = new Label("Email Address");
        emailLbl.getStyleClass().add("field-label");
        email.setPromptText("student@campus.edu.ph");
        email.setPrefHeight(38);
        VBox emailGroup = new VBox(6, emailLbl, email);

        Label pwLbl = new Label("Password");
        pwLbl.getStyleClass().add("field-label");
        Hyperlink forgotLink = link("Forgot password?", () -> AuthDialogs.forgotPassword(owner()));
        forgotLink.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: transparent;");
        Region pwSpacer = new Region();
        HBox.setHgrow(pwSpacer, Priority.ALWAYS);
        HBox pwHeader = new HBox(pwLbl, pwSpacer, forgotLink);
        pwHeader.setAlignment(Pos.CENTER_LEFT);

        password.setPromptText("Enter your password");
        password.setPrefHeight(38);
        VBox pwGroup = new VBox(6, pwHeader, password);

        errorBanner.setWrapText(true);
        errorBanner.setMaxWidth(Double.MAX_VALUE);
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        errorBanner.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fecaca; -fx-text-fill: #b91c1c;"
                + "-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 12 10 12;"
                + "-fx-background-radius: 8; -fx-border-radius: 8;");

        loginBtn.setDefaultButton(true);
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(40);
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setOnAction(e -> login());

        Button registerBtn = new Button("Create Student Account");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setPrefHeight(38);
        registerBtn.setOnAction(e -> AuthDialogs.register(owner()));

        Label toolsTitle = new Label("EMAIL & TOKEN TOOLS");
        toolsTitle.getStyleClass().add("muted-label");
        toolsTitle.setStyle("-fx-font-weight: bold;");

        Button verifyBtn = pillBtn("Verify email", () -> AuthDialogs.verifyEmail(owner()));
        Button resendBtn = pillBtn("Resend link", () -> AuthDialogs.resendVerification(owner()));
        Button resetBtn  = pillBtn("Reset token", () -> AuthDialogs.resetPassword(owner()));
        HBox toolRow = new HBox(8, verifyBtn, resendBtn, resetBtn);
        toolRow.setAlignment(Pos.CENTER);

        VBox footer = new VBox(8, new Separator(), toolsTitle, toolRow);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(4, 0, 0, 0));

        card.getChildren().addAll(header, emailGroup, pwGroup, errorBanner, loginBtn, registerBtn, footer);

        StackPane centerWrapper = new StackPane(card);
        centerWrapper.setPadding(new Insets(10, 28, 28, 28));
        rightPane.setCenter(centerWrapper);
        return rightPane;
    }

    private Window owner() { return getScene() == null ? null : getScene().getWindow(); }

    private static Hyperlink link(String text, Runnable action) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> action.run());
        return h;
    }

    private static Button pillBtn(String text, Runnable action) {
        Button b = new Button(text);
        b.setCursor(Cursor.HAND);
        b.getStyleClass().add("pill-tool-btn");
        b.setOnAction(e -> action.run());
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void showError(String msg) {
        if (msg == null || msg.isBlank()) {
            errorBanner.setText("");
            errorBanner.setVisible(false);
            errorBanner.setManaged(false);
        } else {
            errorBanner.setText(msg);
            errorBanner.setVisible(true);
            errorBanner.setManaged(true);
        }
    }

    private void login() {
        showError(null);
        String em = email.getText() == null ? "" : email.getText().trim();
        String pw = password.getText() == null ? "" : password.getText();
        if (em.isEmpty() || pw.isEmpty()) {
            showError("Enter both your email address and password.");
            return;
        }
        loginBtn.setDisable(true);
        loginBtn.setText("Signing in...");
        Ui.call(ApiClient.post("/v1/auth/login", Map.of("email", em, "password", pw)), r -> {
            loginBtn.setDisable(false);
            loginBtn.setText("Sign in to Workspace");
            if (!r.ok()) {
                showError(r.errorMessage());
                return;
            }
            JsonNode n = r.json();
            JsonNode u = n.path("user");
            Session.set(n.path("accessToken").asText(), n.path("refreshToken").asText(),
                    u.path("role").asText(), u.path("id").asText(),
                    u.path("firstName").asText() + " " + u.path("lastName").asText());
            Lookups.load().thenRun(() -> Platform.runLater(SceneManager::showMain));
        });
    }
}