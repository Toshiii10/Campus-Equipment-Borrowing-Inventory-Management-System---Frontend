package com.cebims.view.dialogs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import com.cebims.api.ApiClient;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * A reusable "fill in a few fields and submit" dialog (register, verify email, reset password,
 * edit profile...). Server errors are shown inside the dialog so nothing typed is lost.
 */
public final class FormDialog {
    private FormDialog() {}

    public enum Kind { TEXT, PASSWORD }

    public record Field(String key, String label, Kind kind) {
        public static Field text(String key, String label)     { return new Field(key, label, Kind.TEXT); }
        public static Field password(String key, String label) { return new Field(key, label, Kind.PASSWORD); }
    }

    /**
     * @param validate returns an error message, or null when the values are fine
     * @param submit   sends the request
     * @param onSuccess runs (on the FX thread) after the dialog closed on a 2xx answer
     */
    public static void show(Window owner, String title, String intro, String okText,
                            List<Field> fields, Map<String, String> initial,
                            Function<Map<String, String>, String> validate,
                            Function<Map<String, String>, CompletableFuture<ApiClient.Response>> submit,
                            Consumer<ApiClient.Response> onSuccess) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        ButtonType okType = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 0, 0, 0));
        Map<String, TextField> inputs = new LinkedHashMap<>();
        int row = 0;
        for (Field f : fields) {
            TextField input = f.kind() == Kind.PASSWORD ? new PasswordField() : new TextField();
            input.setPrefWidth(320);
            if (initial != null && initial.containsKey(f.key())) input.setText(initial.get(f.key()));
            inputs.put(f.key(), input);
            grid.addRow(row++, new Label(f.label()), input);
        }

        Label error = new Label();
        error.setWrapText(true);
        error.setMaxWidth(480);
        error.setStyle("-fx-text-fill: #c0392b;");

        VBox content = new VBox(8);
        if (intro != null && !intro.isEmpty()) {
            Label l = new Label(intro);
            l.setWrapText(true);
            l.setMaxWidth(480);
            content.getChildren().add(l);
        }
        content.getChildren().addAll(grid, error);
        dialog.getDialogPane().setContent(content);

        Button ok = (Button) dialog.getDialogPane().lookupButton(okType);
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume(); // keep the dialog open until the server accepts
            error.setText("");
            Map<String, String> values = new LinkedHashMap<>();
            inputs.forEach((k, input) -> {
                String v = input.getText() == null ? "" : input.getText();
                values.put(k, input instanceof PasswordField ? v : v.trim());
            });
            String problem = validate == null ? null : validate.apply(values);
            if (problem != null) { error.setText(problem); return; }

            ok.setDisable(true);
            submit.apply(values).whenComplete((resp, ex) -> Platform.runLater(() -> {
                ok.setDisable(false);
                if (ex != null) { error.setText("Cannot reach the server. Is the backend running?"); return; }
                if (resp.ok()) {
                    dialog.close();
                    if (onSuccess != null) onSuccess.accept(resp);
                } else {
                    error.setText(resp.errorMessage());
                }
            }));
        });
        dialog.show();
    }
}

