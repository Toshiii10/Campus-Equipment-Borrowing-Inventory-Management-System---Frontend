package com.cebims;

import com.cebims.api.Session;
import com.cebims.view.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Lost and Found");
        SceneManager.init(stage);
        // If the refresh token stops working, send the user back to the login screen.
        Session.setOnLoggedOut(() -> Platform.runLater(SceneManager::showLogin));
        SceneManager.showLogin();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
