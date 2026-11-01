package org.example.db_project;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(loader.load());   // uses the size set in main-view.fxml (1250 x 720)
        stage.setTitle("Smart E-Commerce System");
        stage.setScene(scene);
        stage.show();
    }
}