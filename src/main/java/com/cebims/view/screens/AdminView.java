package com.cebims.view.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cebims.api.ApiClient;
import com.cebims.api.Lookups;
import com.cebims.api.Session;
import com.cebims.view.Reloadable;
import com.cebims.view.dialogs.FormDialog;
import com.cebims.view.dialogs.FormDialog.Field;
import com.cebims.view.util.Page;
import com.cebims.view.util.Ui;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * CEBIMS Admin Control Center:
 * - List all users, filtered by role.
 * - Create Moderator or Admin accounts (already email-verified; Student refused).
 * - Promote or demote accounts (cannot change your own role / last Admin protected).
 * - Add, edit, and delete categories (409 if category is in use).
 * - Add, edit, and delete drop-off locations (409 if items use it).
 * - View drop-off location details including contact person and phone number.
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
                new Tab("Equipment Categories", buildCategoriesPanel()),
                new Tab("Pickup / Drop-off Locations", buildLocationsPanel())
        );

        Button refresh = new Button("Refresh Data");
        refresh.setOnAction(e -> load());

        setTop(Page.top("Users & System Settings (Admin)",
                "Manage campus accounts, equipment categories, and authorized pickup/drop-off locations.", refresh));
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

    /** Extracts rows whether the backend returns a JSON array or a paged/wrapped JSON object. */
    private static List<JsonNode> extractRows(JsonNode root, String... fallbackKeys) {
        List<JsonNode> list = new ArrayList<>();
        if (root == null) return list;
        if (root.isArray()) {
            root.forEach(list::add);
            return list;
        }
        if (root.path("content").isArray()) {
            root.path("content").forEach(list::add);
            return list;
        }
        for (String key : fallbackKeys) {
            if (root.path(key).isArray()) {
                root.path(key).forEach(list::add);
                return list;
            }
        }
        return list;
    }

    // ==========================================
    // 1. USER MANAGEMENT (Roles: Student, Moderator, Admin)
    // ==========================================
    private VBox buildUserManagementPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        roleFilter.getItems().addAll("ALL", "Student", "Moderator", "Admin");
        roleFilter.setValue("ALL");
        roleFilter.setOnAction(e -> loadUsers());

        Button createStaffBtn = new Button("+ Create Mod / Admin");
        createStaffBtn.getStyleClass().add("btn-primary");
        createStaffBtn.setOnAction(e -> openCreateStaffDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(12, new Label("Filter by Role:"), roleFilter, spacer, createStaffBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        usersTable.setPlaceholder(new Label("No users found for this role filter."));
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
        promoteBtn.setDisable(true);
        demoteBtn.setDisable(true);

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, sel) -> {
            if (sel == null) {
                promoteBtn.setDisable(true);
                demoteBtn.setDisable(true);
                return;
            }
            boolean isSelf = Ui.t(sel, "id").equals(Session.userId());
            String r = Ui.t(sel, "role");
            // Cannot change your own role; cannot promote an Admin or demote a Student
            promoteBtn.setDisable(isSelf || "Admin".equalsIgnoreCase(r));
            demoteBtn.setDisable(isSelf || "Student".equalsIgnoreCase(r));
        });

        promoteBtn.setOnAction(e -> changeRole(true));
        demoteBtn.setOnAction(e -> changeRole(false));

        Label selfHint = new Label("Note: You cannot change your own role, and the last Admin cannot be demoted.");
        selfHint.getStyleClass().add("page-sub");
        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        HBox actions = new HBox(10, selfHint, bottomSpacer, promoteBtn, demoteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        layout.getChildren().addAll(topBar, usersTable, actions);
        return layout;
    }

    private void loadUsers() {
        String selectedRole = roleFilter.getValue();
        String query = (selectedRole == null || "ALL".equals(selectedRole)) ? "" : "?role=" + selectedRole;
        Ui.call(ApiClient.get("/v1/admin/users" + query), r -> {
            if (!r.ok()) {
                Ui.error("Could not load users: " + r.errorMessage());
                return;
            }
            usersTable.getItems().setAll(extractRows(r.json(), "users", "data", "items"));
        });
    }

    private void openCreateStaffDialog() {
        FormDialog.show(
                getScene().getWindow(),
                "Create Moderator / Admin Account",
                "Creates an already email-verified Moderator or Admin account. Creating a Student here is refused.",
                "Create Account",
                List.of(
                        Field.text("firstName", "First name"),
                        Field.text("lastName", "Last name"),
                        Field.text("email", "Email"),
                        Field.text("phoneNum", "Phone (09XXXXXXXXX)"),
                        Field.text("role", "Role (Moderator or Admin)"),
                        Field.password("password", "Password")
                ),
                Map.of("role", "Moderator"),
                v -> {
                    if (v.get("firstName").isEmpty() || v.get("lastName").isEmpty()
                            || v.get("email").isEmpty() || v.get("phoneNum").isEmpty() || v.get("password").isEmpty()) {
                        return "Please fill in every field.";
                    }
                    String r = v.get("role");
                    if ("Student".equalsIgnoreCase(r)) {
                        return "Creating a Student account here is refused. Use the public registration screen for Students.";
                    }
                    if (!"Moderator".equalsIgnoreCase(r) && !"Admin".equalsIgnoreCase(r)) {
                        return "Role must be Moderator or Admin.";
                    }
                    return null;
                },
                v -> {
                    String normalizedRole = "Admin".equalsIgnoreCase(v.get("role")) ? "Admin" : "Moderator";
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("firstName", v.get("firstName"));
                    body.put("lastName", v.get("lastName"));
                    body.put("email", v.get("email"));
                    body.put("phoneNum", v.get("phoneNum"));
                    body.put("role", normalizedRole);
                    body.put("password", v.get("password"));
                    return ApiClient.post("/v1/admin/users", body);
                },
                r -> {
                    Ui.info("Staff account created and email-verified.");
                    loadUsers();
                }
        );
    }

    private void changeRole(boolean promote) {
        JsonNode u = usersTable.getSelectionModel().getSelectedItem();
        if (u == null) return;
        if (Ui.t(u, "id").equals(Session.userId())) {
            Ui.error("You cannot change your own role.");
            return;
        }
        String current = Ui.t(u, "role");
        String target = promote
                ? ("Student".equalsIgnoreCase(current) ? "Moderator" : "Admin")
                : ("Admin".equalsIgnoreCase(current) ? "Moderator" : "Student");
        if (target.equalsIgnoreCase(current)) {
            Ui.error("Account is already at role: " + current);
            return;
        }
        if (!Ui.confirm("Change role of " + Ui.t(u, "firstName") + " " + Ui.t(u, "lastName")
                + " from " + current + " to " + target + "?")) return;

        Ui.call(ApiClient.put("/v1/admin/users/" + Ui.t(u, "id") + "/role", Map.of("role", target)), r -> {
            if (r.ok()) loadUsers();
            else Ui.error(r.errorMessage());
        });
    }

    // ==========================================
    // 2. EQUIPMENT CATEGORIES (id, name, icon)
    // ==========================================
    private VBox buildCategoriesPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        TextField categoryField = new TextField();
        categoryField.setPromptText("Enter new category name...");
        categoryField.setPrefWidth(220);

        TextField iconField = new TextField("box");
        iconField.setPromptText("Icon (e.g. laptop, camera)");
        iconField.setPrefWidth(140);

        Button addCatBtn = new Button("+ Add Category");
        addCatBtn.getStyleClass().add("btn-primary");
        addCatBtn.setOnAction(e -> {
            String name = categoryField.getText() == null ? "" : categoryField.getText().trim();
            String icon = iconField.getText() == null || iconField.getText().isBlank() ? "box" : iconField.getText().trim();
            if (name.isEmpty()) {
                Ui.error("Enter a category name first.");
                return;
            }
            Ui.call(ApiClient.post("/v1/admin/categories", Map.of("name", name, "icon", icon)), r -> {
                if (r.ok()) {
                    categoryField.clear();
                    Lookups.load();
                    loadCategories();
                } else {
                    Ui.error(r.errorMessage());
                }
            });
        });

        HBox topBar = new HBox(10, new Label("Name:"), categoryField, new Label("Icon:"), iconField, addCatBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);

        categoryTable.setPlaceholder(new Label("No equipment categories found."));
        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        categoryTable.getColumns().add(Ui.col("Category Name", n -> Ui.t(n, "name"), 240));
        categoryTable.getColumns().add(Ui.col("Icon", n -> Ui.t(n, "icon"), 120));
        categoryTable.getColumns().add(Ui.col("Category ID", n -> Ui.t(n, "id"), 220));
        VBox.setVgrow(categoryTable, Priority.ALWAYS);

        Button editBtn = new Button("Edit Category");
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
            if (!r.ok()) {
                Ui.error("Could not load categories: " + r.errorMessage());
                return;
            }
            categoryTable.getItems().setAll(extractRows(r.json(), "categories", "data", "items"));
        });
    }

    private void editCategory() {
        JsonNode sel = categoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        FormDialog.show(
                getScene().getWindow(),
                "Edit Category",
                null,
                "Save",
                List.of(
                        Field.text("name", "Category name"),
                        Field.text("icon", "Icon")
                ),
                Map.of(
                        "name", Ui.t(sel, "name"),
                        "icon", Ui.t(sel, "icon").isEmpty() ? "box" : Ui.t(sel, "icon")
                ),
                v -> v.get("name").isEmpty() ? "Category name cannot be empty." : null,
                v -> ApiClient.put("/v1/admin/categories/" + Ui.t(sel, "id"),
                        Map.of("name", v.get("name"), "icon", v.get("icon").isEmpty() ? "box" : v.get("icon"))),
                r -> {
                    Lookups.load();
                    loadCategories();
                }
        );
    }

    private void deleteCategory() {
        JsonNode sel = categoryTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!Ui.confirm("Delete category \"" + Ui.t(sel, "name") + "\"?")) return;
        Ui.call(ApiClient.delete("/v1/admin/categories/" + Ui.t(sel, "id")), r -> {
            if (r.ok()) {
                Lookups.load();
                loadCategories();
            } else if (r.status() == 409) {
                Ui.error("Cannot delete category (HTTP 409 Conflict): This category is currently in use by registered equipment.\n"
                        + r.errorMessage());
            } else {
                Ui.error(r.errorMessage());
            }
        });
    }

    // ==========================================
    // 3. PICKUP / DROP-OFF LOCATIONS (id, name, contact, person_name)
    // ==========================================
    private VBox buildLocationsPanel() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(18, 10, 10, 10));

        Label restrictionNotice = new Label("Admin View: Showing contact person and phone number (other roles only see location names).");
        restrictionNotice.getStyleClass().add("page-sub");
        restrictionNotice.setStyle("-fx-font-weight: bold;");

        locationsTable.setPlaceholder(new Label("No pickup / drop-off locations found."));
        locationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        locationsTable.getColumns().add(Ui.col("Location Name", n -> Ui.t(n, "name"), 200));
        locationsTable.getColumns().add(Ui.col("Contact Person", n -> {
            String p = Ui.t(n, "personName");
            return p.isEmpty() ? Ui.t(n, "person_name") : p;
        }, 160));
        locationsTable.getColumns().add(Ui.col("Contact / Phone", n -> Ui.t(n, "contact"), 160));
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
        Ui.call(ApiClient.get("/v1/admin/drop-locations"), r -> {
            if (r.ok()) {
                locationsTable.getItems().setAll(extractRows(r.json(), "locations", "dropLocations", "data"));
            } else {
                Ui.call(ApiClient.get("/v1/drop-locations"), r2 -> {
                    if (r2.ok()) {
                        locationsTable.getItems().setAll(extractRows(r2.json(), "locations", "dropLocations", "data"));
                    } else {
                        Ui.error("Could not load locations: " + r.errorMessage());
                    }
                });
            }
        });
    }

    private void openLocationDialog(JsonNode existing) {
        boolean edit = existing != null;
        String existingPerson = edit ? (Ui.t(existing, "personName").isEmpty() ? Ui.t(existing, "person_name") : Ui.t(existing, "personName")) : "";
        Map<String, String> init = edit
                ? Map.of("name", Ui.t(existing, "name"),
                         "personName", existingPerson,
                         "contact", Ui.t(existing, "contact"))
                : Map.of();

        FormDialog.show(
                getScene().getWindow(),
                edit ? "Edit Pickup / Drop-off Location" : "Add Pickup / Drop-off Location",
                "Contact person and phone number are visible only to Admins and on approved borrowing requests.",
                "Save",
                List.of(
                        Field.text("name", "Location name"),
                        Field.text("personName", "Contact person"),
                        Field.text("contact", "Phone / Contact")
                ),
                init,
                v -> (v.get("name").isEmpty() || v.get("contact").isEmpty())
                        ? "Location name and Contact are required." : null,
                v -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("name", v.get("name"));
                    body.put("personName", v.get("personName"));
                    body.put("contact", v.get("contact"));
                    return edit
                            ? ApiClient.put("/v1/admin/drop-locations/" + Ui.t(existing, "id"), body)
                            : ApiClient.post("/v1/admin/drop-locations", body);
                },
                r -> {
                    Lookups.load();
                    loadLocations();
                }
        );
    }

    private void deleteLocation() {
        JsonNode sel = locationsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!Ui.confirm("Delete location \"" + Ui.t(sel, "name") + "\"?")) return;
        Ui.call(ApiClient.delete("/v1/admin/drop-locations/" + Ui.t(sel, "id")), r -> {
            if (r.ok()) {
                Lookups.load();
                loadLocations();
            } else if (r.status() == 409) {
                Ui.error("Cannot delete location (HTTP 409 Conflict): Registered equipment items are currently using this location.\n"
                        + r.errorMessage());
            } else {
                Ui.error(r.errorMessage());
            }
        });
    }
}