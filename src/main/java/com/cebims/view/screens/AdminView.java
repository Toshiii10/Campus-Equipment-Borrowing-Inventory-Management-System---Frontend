package com.cebims.view.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.view.Reloadable;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * CEBIMS Admin Control Center: User roles, Equipment Categories, and Authorized Locations.
 */
public class AdminView extends BorderPane implements Reloadable {

    private final TableView<JsonNode> usersTable = new TableView<>();
    private final ComboBox<String> roleFilter = new ComboBox<>();
    private final TableView<JsonNode> categoryTable = new TableView<>();
    private final TableView<JsonNode> locationsTable = new TableView<>();

    public AdminView() {
        setPadding(new Insets(22, 28, 22, 28));

        TabPane adminTabPane = new TabPane();
        adminTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        adminTabPane.getStyleClass().add("card");

        adminTabPane.getTabs().addAll(
                new Tab("User Management", buildUserManagementPanel()),
                new Tab("Categories", buildCategoriesPanel()),
                new Tab("Pickup / Drop-off Locations", buildLocationsPanel())
        );

        Button refresh = new Button("Refresh Data");
        refresh.setOnAction(e -> load());

        setTop(Page.top("Users & System Settings",
                "Manage accounts, item categories and drop-off locations.", refresh));
        setCenter(adminTabPane);

        load();
    }

    @Override
    public void reload() {
        load();
    }

    private void load() {
        loadUsers();
        loadCategories();
        loadLocations();
    }

    // ==========================================
    // 1. USER MANAGEMENT
    // ==========================================
    private VBox buildUserManagementPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        roleFilter.getItems().addAll("ALL", "STUDENT", "MODERATOR", "ADMIN");
        roleFilter.setValue("ALL");
        roleFilter.setOnAction(e -> loadUsers());

        Button createStaffBtn = new Button("+ Create Mod / Admin");
        createStaffBtn.getStyleClass().add("btn-primary");
        createStaffBtn.setOnAction(e -> openCreateStaffDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(12, new Label("Filter by Role:"), roleFilter, spacer, createStaffBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        usersTable.getColumns().add(Ui.col("Name", n -> Ui.t(n, "firstName") + " " + Ui.t(n, "lastName"), 170));
        usersTable.getColumns().add(Ui.col("Email", n -> Ui.t(n, "email"), 200));
        usersTable.getColumns().add(Ui.col("Student ID", n -> Ui.t(n, "studentId").isEmpty() ? "-" : Ui.t(n, "studentId"), 110));
        usersTable.getColumns().add(Ui.col("Phone", n -> Ui.t(n, "phoneNum"), 120));
        usersTable.getColumns().add(Ui.statusCol("Role", n -> Ui.t(n, "role"), 110));
        usersTable.getColumns().add(Ui.col("Verified", n -> n.path("emailVerified").asBoolean(true) ? "Yes" : "No", 80));
        VBox.setVgrow(usersTable, Priority.ALWAYS);

        Button promoteBtn = new Button("Promote Account");
        Button demoteBtn = new Button("Demote Account");
        for (Button b : List.of(promoteBtn, demoteBtn)) {
            b.disableProperty().bind(usersTable.getSelectionModel().selectedItemProperty().isNull());
        }
        promoteBtn.setOnAction(e -> changeRole(true));
        demoteBtn.setOnAction(e -> changeRole(false));

        HBox actions = new HBox(10, promoteBtn, demoteBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(topBar, usersTable, actions);
        return layout;
    }

    /** The backend spells roles Student / Moderator / Admin, and it is case-sensitive. */
    private static String apiRole(String anyCase) {
        if (anyCase == null || anyCase.isEmpty()) return "";
        return anyCase.substring(0, 1).toUpperCase() + anyCase.substring(1).toLowerCase();
    }

    private void loadUsers() {
        String role = "ALL".equals(roleFilter.getValue()) ? "" : "&role=" + apiRole(roleFilter.getValue());
        Ui.call(ApiClient.get("/v1/admin/users?size=100" + role), r -> {
            if (!r.ok()) return;
            List<JsonNode> list = new ArrayList<>();
            JsonNode root = r.json();
            JsonNode rows = root.isArray() ? root : root.path("content");
            rows.forEach(list::add);
            usersTable.getItems().setAll(list);
        });
    }

    private void openCreateStaffDialog() {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Create Staff Account");
        dialog.setHeaderText("Create an already email-verified Moderator or Admin account.");

        ButtonType createType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField first = new TextField();
        TextField last = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        ComboBox<String> role = new ComboBox<>();
        role.getItems().addAll("MODERATOR", "ADMIN");
        role.setValue("MODERATOR");
        PasswordField pw = new PasswordField();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("First name:"), first);
        grid.addRow(1, new Label("Last name:"), last);
        grid.addRow(2, new Label("Email:"), email);
        grid.addRow(3, new Label("Phone:"), phone);
        grid.addRow(4, new Label("Role:"), role);
        grid.addRow(5, new Label("Password:"), pw);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != createType) return null;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("firstName", first.getText().trim());
            body.put("lastName", last.getText().trim());
            body.put("email", email.getText().trim());
            body.put("phoneNum", phone.getText().trim());
            body.put("role", apiRole(role.getValue()));
            body.put("password", pw.getText());
            return body;
        });

        dialog.showAndWait().ifPresent(body -> {
            Ui.call(ApiClient.post("/v1/admin/users/staff", body), r -> {
                if (r.ok()) loadUsers();
                else Ui.error(r.errorMessage());
            });
        });
    }

    private void changeRole(boolean promote) {
        JsonNode u = usersTable.getSelectionModel().getSelectedItem();
        if (u == null) return;
        String current = Ui.t(u, "role").toUpperCase();
        String target = promote
                ? ("STUDENT".equals(current) ? "MODERATOR" : "ADMIN")
                : ("ADMIN".equals(current) ? "MODERATOR" : "STUDENT");
        if (target.equals(current)) {
            Ui.error("Account is already at role: " + current);
            return;
        }
        if (!Ui.confirm("Change role of " + Ui.t(u, "firstName") + " " + Ui.t(u, "lastName")
                + " from " + current + " to " + target + "?")) return;

        Ui.call(ApiClient.patch("/v1/admin/users/" + Ui.t(u, "id") + "/role", Map.of("role", apiRole(target))), r -> {
            if (r.ok()) loadUsers();
            else Ui.error(r.errorMessage());
        });
    }

    // ==========================================
    // 2. CATEGORIES
    // ==========================================
    private VBox buildCategoriesPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        TextField categoryField = new TextField();
        categoryField.setPromptText("Enter new category name...");
        categoryField.setPrefWidth(240);
        TextField iconField = new TextField();
        iconField.setPromptText("Icon, e.g. 📱");
        iconField.setPrefWidth(120);
        Button addCatBtn = new Button("+ Add Category");
        addCatBtn.getStyleClass().add("btn-primary");
        addCatBtn.setOnAction(e -> {
            String name = categoryField.getText() == null ? "" : categoryField.getText().trim();
            String icon = iconField.getText() == null ? "" : iconField.getText().trim();
            if (name.isEmpty() || icon.isEmpty()) { Ui.error("Enter both a category name and an icon."); return; }
            Ui.call(ApiClient.post("/v1/categories", Map.of("name", name, "icon", icon)), r -> {
                if (r.ok()) {
                    categoryField.clear();
                    iconField.clear();
                    Lookups.load();
                    loadCategories();
                } else {
                    Ui.error(r.errorMessage());
                }
            });
        });

        HBox topBar = new HBox(10, categoryField, iconField, addCatBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        categoryTable.getColumns().add(Ui.col("Category Name", n -> Ui.t(n, "name"), 260));
        categoryTable.getColumns().add(Ui.col("Icon", n -> Ui.t(n, "icon"), 80));
        categoryTable.getColumns().add(Ui.col("Category ID", n -> Ui.t(n, "id"), 220));
        VBox.setVgrow(categoryTable, Priority.ALWAYS);

        Button editBtn = new Button("Rename Category");
        Button deleteBtn = new Button("Delete Category");
        for (Button b : List.of(editBtn, deleteBtn)) {
            b.disableProperty().bind(categoryTable.getSelectionModel().selectedItemProperty().isNull());
        }
        editBtn.setOnAction(e -> editCategory());
        deleteBtn.setOnAction(e -> deleteCategory());

        HBox actions = new HBox(10, editBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(topBar, categoryTable, actions);
        return layout;
    }

    private void loadCategories() {
        Ui.call(ApiClient.get("/v1/categories"), r -> {
            if (r.ok()) {
                List<JsonNode> list = new ArrayList<>();
                r.json().forEach(list::add);
                categoryTable.getItems().setAll(list);
            }
        });
    }

    private void editCategory() {
        JsonNode sel = categoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        TextInputDialog d = new TextInputDialog(Ui.t(sel, "name"));
        d.initOwner(getScene().getWindow());
        d.setTitle("Rename Category");
        d.setHeaderText("Enter new name for category:");
        d.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty()) return;
            Ui.call(ApiClient.put("/v1/categories/" + Ui.t(sel, "id"), Map.of("name", newName.trim(),
                    "icon", Ui.t(sel, "icon").isEmpty() ? "📦" : Ui.t(sel, "icon"))), r -> {
                if (r.ok()) { Lookups.load(); loadCategories(); }
                else Ui.error(r.errorMessage());
            });
        });
    }

    private void deleteCategory() {
        JsonNode sel = categoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!Ui.confirm("Delete category \"" + Ui.t(sel, "name") + "\"?")) return;
        Ui.call(ApiClient.delete("/v1/categories/" + Ui.t(sel, "id")), r -> {
            if (r.ok()) { Lookups.load(); loadCategories(); }
            else Ui.error(r.errorMessage());
        });
    }

    // ==========================================
    // 3. PICKUP / DROP-OFF LOCATIONS
    // ==========================================
    private VBox buildLocationsPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        Label restrictionNotice = new Label("Admin Clearance Active: Displaying restricted contact persons and phone numbers.");
        restrictionNotice.getStyleClass().add("page-sub");
        restrictionNotice.setStyle("-fx-font-weight: bold;");

        locationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        locationsTable.getColumns().add(Ui.col("Location Name", n -> Ui.t(n, "name"), 180));
        locationsTable.getColumns().add(Ui.col("Contact Person", n -> {
            String p = Ui.t(n, "personName");
            return p.isEmpty() ? Ui.t(n, "contactPerson") : p;
        }, 160));
        locationsTable.getColumns().add(Ui.col("Contact Info / Phone", n -> {
            String c = Ui.t(n, "contact");
            return c.isEmpty() ? Ui.t(n, "contactPhone") : c;
        }, 160));
        VBox.setVgrow(locationsTable, Priority.ALWAYS);

        Button addLocBtn = new Button("+ Add Location");
        addLocBtn.getStyleClass().add("btn-primary");
        Button editLocBtn = new Button("Edit Location");
        Button deleteLocBtn = new Button("Delete Location");
        for (Button b : List.of(editLocBtn, deleteLocBtn)) {
            b.disableProperty().bind(locationsTable.getSelectionModel().selectedItemProperty().isNull());
        }
        addLocBtn.setOnAction(e -> openLocationDialog(null));
        editLocBtn.setOnAction(e -> openLocationDialog(locationsTable.getSelectionModel().getSelectedItem()));
        deleteLocBtn.setOnAction(e -> deleteLocation());

        HBox actions = new HBox(10, addLocBtn, editLocBtn, deleteLocBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(restrictionNotice, locationsTable, actions);
        return layout;
    }

    private void loadLocations() {
        Ui.call(ApiClient.get("/v1/drop-locations/details"), r -> {
            if (r.ok()) {
                List<JsonNode> list = new ArrayList<>();
                r.json().forEach(list::add);
                locationsTable.getItems().setAll(list);
            } else {
                Ui.call(ApiClient.get("/v1/drop-locations"), r2 -> {
                    if (r2.ok()) {
                        List<JsonNode> list = new ArrayList<>();
                        r2.json().forEach(list::add);
                        locationsTable.getItems().setAll(list);
                    }
                });
            }
        });
    }

    private void openLocationDialog(JsonNode existing) {
        boolean edit = existing != null;
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle(edit ? "Edit Location" : "Add Location");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField name = new TextField(edit ? Ui.t(existing, "name") : "");
        String existingPerson = edit ? (Ui.t(existing, "personName").isEmpty() ? Ui.t(existing, "contactPerson") : Ui.t(existing, "personName")) : "";
        String existingContact = edit ? (Ui.t(existing, "contact").isEmpty() ? Ui.t(existing, "contactPhone") : Ui.t(existing, "contact")) : "";
        TextField person = new TextField(existingPerson);
        TextField contact = new TextField(existingContact);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Location name:"), name);
        grid.addRow(1, new Label("Contact person:"), person);
        grid.addRow(2, new Label("Phone / Contact:"), contact);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != saveType) return null;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", name.getText().trim());
            body.put("personName", person.getText().trim());
            body.put("contactPerson", person.getText().trim());
            body.put("contact", contact.getText().trim());
            body.put("contactPhone", contact.getText().trim());
            return body;
        });

        dialog.showAndWait().ifPresent(body -> {
            var req = edit
                    ? ApiClient.put("/v1/drop-locations/" + Ui.t(existing, "id"), body)
                    : ApiClient.post("/v1/drop-locations", body);
            Ui.call(req, r -> {
                if (r.ok()) { Lookups.load(); loadLocations(); }
                else Ui.error(r.errorMessage());
            });
        });
    }

    private void deleteLocation() {
        JsonNode sel = locationsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!Ui.confirm("Delete location \"" + Ui.t(sel, "name") + "\"?")) return;
        Ui.call(ApiClient.delete("/v1/drop-locations/" + Ui.t(sel, "id")), r -> {
            if (r.ok()) { Lookups.load(); loadLocations(); }
            else Ui.error(r.errorMessage());
        });
    }
}