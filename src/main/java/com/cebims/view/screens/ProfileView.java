package com.cebims.view.screens;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.api.Session;
import com.cebims.view.Reloadable;
import com.cebims.view.SceneManager;
import com.cebims.view.dialogs.AuthDialogs;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Your profile (GET /v1/auth/me), "log out of all devices", and the category / drop-off lists.
 * Only Moderators and Admins can edit their own name and phone (the backend refuses students).
 */
public class ProfileView extends BorderPane implements Reloadable {
    private final Label name = new Label();
    private final Label role = new Label();
    private final Label studentId = new Label();
    private final Label email = new Label();
    private final Label phone = new Label();
    private String first = "", last = "", phoneNum = "";

    public ProfileView() {
        setPadding(new Insets(22, 28, 22, 28));

        GridPane g = new GridPane();
        g.setHgap(20);
        g.setVgap(12);
        addRow(g, 0, "Name", name);
        addRow(g, 1, "Role", role);
        addRow(g, 2, "Student ID", studentId);
        addRow(g, 3, "Email", email);
        addRow(g, 4, "Phone", phone);

        Button edit = new Button("Edit my name and phone");
        edit.getStyleClass().add("btn-primary");
        edit.setVisible(Session.isStaff());
        edit.setManaged(Session.isStaff());
        edit.setOnAction(e -> AuthDialogs.editProfile(getScene().getWindow(), "Edit my profile",
                "You can change your name and phone number. Email and student ID cannot be changed here.",
                "/v1/auth/me", first, last, phoneNum, this::load));

        Button logoutAll = new Button("Log out of all devices");
        logoutAll.setOnAction(e -> logoutAll());

        Label hint = new Label(Session.isStaff() ? ""
                : "Your name, phone and email can only be changed by staff.");
        hint.getStyleClass().add("page-sub");
        hint.setWrapText(true);

        Label myProfTitle = new Label("My profile");
        myProfTitle.getStyleClass().add("page-title");
        myProfTitle.setStyle("-fx-font-size: 16px;");

        VBox leftCard = new VBox(16, myProfTitle, g, new HBox(10, edit, logoutAll), hint);
        leftCard.getStyleClass().add("card");

        ListView<String> cats = new ListView<>(FXCollections.observableArrayList(
                Lookups.categories().stream().map(Lookups.Option::name).toList()));
        ListView<String> drops = new ListView<>(FXCollections.observableArrayList(
                Lookups.dropLocations().stream().map(Lookups.Option::name).toList()));
        cats.setPrefHeight(180);
        drops.setPrefHeight(180);

        Label catTitle = new Label("Categories");
        catTitle.getStyleClass().add("field-label");
        Label dropTitle = new Label("Drop-off locations");
        dropTitle.getStyleClass().add("field-label");

        VBox rightCard = new VBox(10, catTitle, cats, dropTitle, drops);
        rightCard.getStyleClass().add("card");

        GridPane layout = new GridPane();
        layout.setHgap(18);
        layout.setPadding(new Insets(14, 0, 0, 0));
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(48);
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(52);
        c2.setHgrow(Priority.ALWAYS);
        layout.getColumnConstraints().addAll(c1, c2);
        layout.add(leftCard, 0, 0);
        layout.add(rightCard, 1, 0);

        setTop(Page.header("Profile", "Your account details and the lists of categories and drop-off places."));
        setCenter(layout);
        load();
    }

    private static void addRow(GridPane g, int r, String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("page-sub");
        l.setStyle("-fx-font-weight: bold;");
        value.getStyleClass().add("field-label");
        g.addRow(r, l, value);
    }

    @Override public void reload() { load(); }

    private void load() {
        Ui.call(ApiClient.get("/v1/auth/me"), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            JsonNode u = r.json();
            first = Ui.t(u, "firstName");
            last = Ui.t(u, "lastName");
            phoneNum = Ui.t(u, "phoneNum");
            name.setText(first + " " + last);
            role.setText(Ui.t(u, "role"));
            studentId.setText(Ui.t(u, "studentId").isEmpty() ? "-" : Ui.t(u, "studentId"));
            email.setText(Ui.t(u, "email"));
            phone.setText(phoneNum);
        });
    }

    private void logoutAll() {
        if (!Ui.confirm("Log out of every device, including this one?")) return;
        Ui.call(ApiClient.post("/v1/auth/logout-all", null), r -> {
            if (!r.ok()) { Ui.error(r.errorMessage()); return; }
            Session.clear();
            SceneManager.showLogin();
        });
    }
}