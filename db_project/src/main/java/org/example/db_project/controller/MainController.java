package org.example.db_project.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MainController {

    @FXML private TabPane tabPane;
    @FXML private Tab productsTab;

    // For <fx:include fx:id="productView" .../>, JavaFX injects the included
    // file's controller into a field named fx:id + "Controller".
    @FXML private ProductController productViewController;

    @FXML
    public void initialize() {
        // Refresh products whenever the tab is opened, so category changes show up
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == productsTab) {
                productViewController.refresh();
            }
        });
    }
}
