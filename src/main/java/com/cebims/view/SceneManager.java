package com.cebims.view;

import java.net.URL;

import com.cebims.view.util.ThemeManager;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** swap yung whole screen and preserves the active Light/Dark theme. */
public final class SceneManager {
    private static Stage stage;

    private SceneManager() {}

    public static void init(Stage s) { stage = s; }

    private static void setRoot(Parent root) {
        ThemeManager.apply(root);
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1360, 840);
            URL css = SceneManager.class.getResource("/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    public static void showLogin() { setRoot(new LoginView()); }
    public static void showMain()  { setRoot(new MainView()); }
}