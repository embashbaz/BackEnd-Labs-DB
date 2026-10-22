package org.example.db_project.controller;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import org.example.db_project.model.*;
import org.example.db_project.service.OrderService;
import org.example.db_project.service.ProductService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderController {

    private static final String ALL_STATUSES = "All statuses";

    // ----- order list -----
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TableView<Order> orderTable;
    @FXML private TableColumn<Order, Integer> orderIdColumn;
    @FXML private TableColumn<Order, String> customerColumn;
    @FXML private TableColumn<Order, String> dateColumn;
    @FXML private TableColumn<Order, OrderStatus> statusColumn;
    @FXML private TableColumn<Order, Integer> itemCountColumn;
    @FXML private TableColumn<Order, BigDecimal> totalColumn;

    // ----- selected order details -----
    @FXML private TableView<OrderItem> itemsTable;
    @FXML private TableColumn<OrderItem, String> itemProductColumn;
    @FXML private TableColumn<OrderItem, Integer> itemQuantityColumn;
    @FXML private TableColumn<OrderItem, BigDecimal> itemPriceColumn;
    @FXML private TableColumn<OrderItem, BigDecimal> itemLineTotalColumn;
    @FXML private ComboBox<OrderStatus> newStatusCombo;
    @FXML private Button updateStatusButton;

    // ----- new order -----
    @FXML private ComboBox<User> customerCombo;
    @FXML private TextField productSearchField;
    @FXML private ComboBox<Product> productCombo;
    @FXML private TextField quantityField;
    @FXML private TableView<OrderItem> cartTable;
    @FXML private TableColumn<OrderItem, String> cartProductColumn;
    @FXML private TableColumn<OrderItem, Integer> cartQuantityColumn;
    @FXML private TableColumn<OrderItem, BigDecimal> cartPriceColumn;
    @FXML private TableColumn<OrderItem, BigDecimal> cartLineTotalColumn;
    @FXML private Label cartTotalLabel;

    @FXML private Label statusLabel;

    private final OrderService orderService = new OrderService();
    private final ProductService productService = new ProductService();
    private final ObservableList<OrderItem> cart = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Order list columns
        orderIdColumn.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        customerColumn.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("formattedDate"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        itemCountColumn.setCellValueFactory(new PropertyValueFactory<>("itemCount"));
        totalColumn.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));

        // Item columns (same OrderItem model for both tables)
        setUpItemColumns(itemProductColumn, itemQuantityColumn, itemPriceColumn, itemLineTotalColumn);
        setUpItemColumns(cartProductColumn, cartQuantityColumn, cartPriceColumn, cartLineTotalColumn);

        // Status filter
        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_STATUSES);
        for (OrderStatus s : OrderStatus.values()) {
            filterOptions.add(s.name());
        }
        statusFilterCombo.setItems(FXCollections.observableArrayList(filterOptions));
        statusFilterCombo.setValue(ALL_STATUSES);

        // Show stock next to each product in the search results
        productCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : p.getName() + "  ·  " + p.getPrice() + "  ·  " + p.getStockQuantity() + " in stock";
            }

            @Override
            public Product fromString(String s) {
                return null;   // the ComboBox isn't editable, so this is never used
            }
        });

        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, selected) -> showOrderDetails(selected));

        cartTable.setItems(cart);
        showOrderDetails(null);
        loadCustomers();
        loadOrders(null);
        updateCartTotal();
    }

    /** Called by MainController whenever the Orders tab is opened. */
    public void refresh() {
        productService.invalidateCache();   // stock may have changed on the Products tab
        productCombo.getItems().clear();
        loadCustomers();
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        loadOrders(selected == null ? null : selected.getOrderId());
    }

    // ---------- order list ----------

    @FXML
    private void handleFilterChanged() {
        loadOrders(null);
    }

    @FXML
    private void handleUpdateStatus() {
        Order selected = orderTable.getSelectionModel().getSelectedItem();
        OrderStatus newStatus = newStatusCombo.getValue();

        if (newStatus == OrderStatus.CANCELLED && selected != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Cancel order #" + selected.getOrderId() + "? Its items will be returned to stock.",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        try {
            orderService.changeStatus(selected, newStatus);
            productService.invalidateCache();          // cancelling changes stock
            loadOrders(selected.getOrderId());
            showStatus("Order #" + selected.getOrderId() + " is now " + newStatus + ".", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
            if (selected != null) {
                loadOrders(selected.getOrderId());
            }
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    // ---------- new order ----------

    @FXML
    private void handleFindProducts() {
        try {
            ProductPage page = productService.search(productSearchField.getText(), null, 1);
            productCombo.setItems(FXCollections.observableArrayList(page.products()));
            if (page.products().isEmpty()) {
                showStatus("No products match that search.", true);
            } else {
                productCombo.getSelectionModel().selectFirst();
                productCombo.show();
                String more = page.totalCount() > page.products().size()
                        ? " Showing the first " + page.products().size() + " of " + page.totalCount() + "; refine the search to narrow it down."
                        : "";
                showStatus("Choose a product." + more, false);
            }
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleAddToCart() {
        try {
            orderService.addToCart(cart, productCombo.getValue(), quantityField.getText());
            cartTable.refresh();               // redraws a line whose quantity just changed
            updateCartTotal();
            quantityField.setText("1");
            statusLabel.setText("");
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        }
    }

    @FXML
    private void handleRemoveFromCart() {
        OrderItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Select a line in the order to remove.", true);
            return;
        }
        cart.remove(selected);
        updateCartTotal();
    }

    @FXML
    private void handleClearCart() {
        cart.clear();
        updateCartTotal();
        statusLabel.setText("");
    }

    @FXML
    private void handlePlaceOrder() {
        try {
            int orderId = orderService.placeOrder(customerCombo.getValue(), cart);
            cart.clear();
            updateCartTotal();
            productService.invalidateCache();   // stock just changed
            productCombo.getItems().clear();
            statusFilterCombo.setValue(ALL_STATUSES);
            loadOrders(orderId);
            showStatus("Order #" + orderId + " placed.", false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    // ---------- helpers ----------

    private void loadOrders(Integer orderIdToSelect) {
        try {
            String filter = statusFilterCombo.getValue();
            OrderStatus status = (filter == null || ALL_STATUSES.equals(filter)) ? null : OrderStatus.valueOf(filter);
            List<Order> orders = orderService.getOrders(status);
            orderTable.setItems(FXCollections.observableArrayList(orders));

            if (orderIdToSelect != null) {
                orders.stream()
                        .filter(o -> o.getOrderId() == orderIdToSelect)
                        .findFirst()
                        .ifPresent(o -> {
                            orderTable.getSelectionModel().select(o);
                            orderTable.scrollTo(o);
                        });
            }
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void showOrderDetails(Order order) {
        if (order == null) {
            itemsTable.getItems().clear();
            newStatusCombo.getItems().clear();
            newStatusCombo.setDisable(true);
            updateStatusButton.setDisable(true);
            return;
        }
        try {
            itemsTable.setItems(FXCollections.observableArrayList(orderService.getItems(order.getOrderId())));
            List<OrderStatus> options = order.getStatus().nextOptions();
            newStatusCombo.setItems(FXCollections.observableArrayList(options));
            newStatusCombo.setDisable(options.isEmpty());
            updateStatusButton.setDisable(options.isEmpty());
            newStatusCombo.setPromptText(options.isEmpty() ? "Final status" : "Choose…");
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void loadCustomers() {
        try {
            User previous = customerCombo.getValue();
            List<User> customers = orderService.getCustomers();
            customerCombo.setItems(FXCollections.observableArrayList(customers));
            if (previous != null) {
                customers.stream()
                        .filter(u -> u.getUserId() == previous.getUserId())
                        .findFirst()
                        .ifPresent(customerCombo::setValue);
            }
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    private void updateCartTotal() {
        BigDecimal total = cart.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cartTotalLabel.setText("Estimated total: " + total.setScale(2));
    }

    private void setUpItemColumns(TableColumn<OrderItem, String> product, TableColumn<OrderItem, Integer> quantity,
                                  TableColumn<OrderItem, BigDecimal> price, TableColumn<OrderItem, BigDecimal> lineTotal) {
        product.setCellValueFactory(new PropertyValueFactory<>("productName"));
        quantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        price.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        lineTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
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
