package org.example.db_project.controller;


import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.db_project.model.Category;
import org.example.db_project.model.Product;
import org.example.db_project.model.ProductPage;
import org.example.db_project.service.CategoryService;
import org.example.db_project.service.ProductService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductController {

    // Placeholder entry for the filter dropdown; id 0 never exists in the database
    private static final Category ALL_CATEGORIES = new Category(0, "All categories", null);

    // Search bar
    @FXML private TextField searchField;
    @FXML private ComboBox<Category> filterCategoryCombo;

    // Table and paging
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> idColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, BigDecimal> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private Label pageLabel;
    @FXML private Button prevButton;
    @FXML private Button nextButton;

    // Form
    @FXML private TextField nameField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private TextField priceField;
    @FXML private TextField stockField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;

    private final ProductService productService = new ProductService();
    private final CategoryService categoryService = new CategoryService();

    // Current search state
    private String currentKeyword = "";
    private Integer currentCategoryId = null;
    private int currentPage = 1;
    private int totalPages = 1;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("productId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));

        productTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, selected) -> {
            if (selected != null) {
                fillForm(selected);
            }
        });

        loadCategories();
        loadPage();
    }

    /** Called by MainController whenever the Products tab is opened. */
    public void refresh() {
        productService.invalidateCache();      // categories may have been renamed or deleted
        loadCategories();
        currentCategoryId = idOrNull(filterCategoryCombo.getValue());
        loadPage();
    }

    // ---------- search and paging ----------

    @FXML
    private void handleSearch() {
        currentKeyword = searchField.getText();
        currentCategoryId = idOrNull(filterCategoryCombo.getValue());
        currentPage = 1;
        loadPage();
    }

    @FXML
    private void handleResetSearch() {
        searchField.clear();
        filterCategoryCombo.setValue(ALL_CATEGORIES);
        handleSearch();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadPage();
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadPage();
        }
    }

    // ---------- CRUD ----------

    @FXML
    private void handleAdd() {
        try {
            Product created = productService.addProduct(nameField.getText(), categoryCombo.getValue(),
                    priceField.getText(), stockField.getText(), descriptionField.getText());
            loadPage();
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
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Select a product in the table to update.", true);
            return;
        }
        try {
            productService.updateProduct(selected.getProductId(), nameField.getText(), categoryCombo.getValue(),
                    priceField.getText(), stockField.getText(), descriptionField.getText());
            loadPage();
            clearForm();
            showStatus("Product updated.", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleDelete() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Select a product in the table to delete.", true);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\"?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            productService.deleteProduct(selected.getProductId());
            loadPage();
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

    private void loadPage() {
        try {
            int hitsBefore = productService.getCacheHits();
            ProductPage page = productService.search(currentKeyword, currentCategoryId, currentPage);
            boolean fromCache = productService.getCacheHits() > hitsBefore;

            productTable.setItems(FXCollections.observableArrayList(page.products()));
            currentPage = page.page();
            totalPages = page.totalPages();

            pageLabel.setText(String.format("Page %d of %d  (%d products)%s",
                    currentPage, totalPages, page.totalCount(), fromCache ? "  · from cache" : ""));
            prevButton.setDisable(currentPage <= 1);
            nextButton.setDisable(currentPage >= totalPages);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void loadCategories() {
        try {
            List<Category> categories = categoryService.getAllCategories();
            Category previousFilter = filterCategoryCombo.getValue();
            Category previousFormChoice = categoryCombo.getValue();

            List<Category> filterItems = new ArrayList<>();
            filterItems.add(ALL_CATEGORIES);
            filterItems.addAll(categories);

            filterCategoryCombo.setItems(FXCollections.observableArrayList(filterItems));
            categoryCombo.setItems(FXCollections.observableArrayList(categories));

            // Keep the user's previous choices if those categories still exist
            filterCategoryCombo.setValue(findById(filterItems, previousFilter, ALL_CATEGORIES));
            categoryCombo.setValue(findById(categories, previousFormChoice, null));
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void fillForm(Product p) {
        nameField.setText(p.getName());
        categoryCombo.setValue(categoryCombo.getItems().stream()
                .filter(c -> c.getCategoryId() == p.getCategoryId())
                .findFirst().orElse(null));
        priceField.setText(p.getPrice() == null ? "" : p.getPrice().toPlainString());
        stockField.setText(String.valueOf(p.getStockQuantity()));
        descriptionField.setText(p.getDescription());
    }

    private void clearForm() {
        productTable.getSelectionModel().clearSelection();
        nameField.clear();
        categoryCombo.setValue(null);
        priceField.clear();
        stockField.clear();
        descriptionField.clear();
    }

    private Category findById(List<Category> list, Category wanted, Category fallback) {
        if (wanted == null) {
            return fallback;
        }
        return list.stream()
                .filter(c -> c.getCategoryId() == wanted.getCategoryId())
                .findFirst()
                .orElse(fallback);
    }

    private Integer idOrNull(Category c) {
        return (c == null || c.getCategoryId() == 0) ? null : c.getCategoryId();
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1e8449;");
    }

    private void showDatabaseError(SQLException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Database error");
        alert.setHeaderText("Could not complete the database operation");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
