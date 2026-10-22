package org.example.db_project.controller;


import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.db_project.model.Category;
import org.example.db_project.service.CategoryService;

import java.sql.SQLException;

public class CategoryController {

    // Each fx:id in category-view.fxml is injected into the field with the same name
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, Integer> idColumn;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> descriptionColumn;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;

    private final CategoryService service = new CategoryService();

    /** Runs automatically after the FXML is loaded. */
    @FXML
    public void initialize() {
        // "categoryId" -> calls getCategoryId(), "name" -> getName(), etc.
        idColumn.setCellValueFactory(new PropertyValueFactory<>("categoryId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Clicking a row fills the form so it can be edited
        categoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, selected) -> {
            if (selected != null) {
                nameField.setText(selected.getName());
                descriptionField.setText(selected.getDescription());
            }
        });

        loadCategories();
    }

    @FXML
    private void handleAdd() {
        try {
            Category created = service.addCategory(nameField.getText(), descriptionField.getText());
            loadCategories();
            clearForm();
            showStatus("Added \"" + created.getName() + "\".", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleUpdate() {
        Category selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Select a category in the table to update.", true);
            return;
        }
        try {
            service.updateCategory(selected.getCategoryId(), nameField.getText(), descriptionField.getText());
            loadCategories();
            clearForm();
            showStatus("Category updated.", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleDelete() {
        Category selected = categoryTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Select a category in the table to delete.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\"?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            service.deleteCategory(selected.getCategoryId());
            loadCategories();
            clearForm();
            showStatus("Deleted \"" + selected.getName() + "\".", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
        statusLabel.setText("");
    }

    // ---------- helpers ----------

    private void loadCategories() {
        try {
            categoryTable.setItems(FXCollections.observableArrayList(service.getAllCategories()));
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void clearForm() {
        categoryTable.getSelectionModel().clearSelection();
        nameField.clear();
        descriptionField.clear();
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1e8449;");
    }

    private void showDatabaseError(SQLException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Database error");
        alert.setHeaderText("Could not reach the database");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
