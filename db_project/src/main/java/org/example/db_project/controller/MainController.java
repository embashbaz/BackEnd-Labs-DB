package org.example.db_project.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class MainController {

    @FXML private TabPane tabPane;
    @FXML private Tab productsTab;
    @FXML private Tab ordersTab;

    // For <fx:include fx:id="xyz" .../>, JavaFX injects the included
    // file's controller into a field named "xyz" + "Controller".
    @FXML private ProductController productViewController;
    @FXML private OrderController orderViewController;

    @FXML
    public void initialize() {
        // Refresh a tab whenever it's opened, so changes made on other tabs show up
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == productsTab) {
                productViewController.refresh();
            } else if (newTab == ordersTab) {
                orderViewController.refresh();
            }
        });
    }
}
