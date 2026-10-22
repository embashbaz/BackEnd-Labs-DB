package org.example.db_project.service;


import org.example.db_project.dao.OrderDAO;
import org.example.db_project.dao.UserDAO;
import org.example.db_project.exception.InsufficientStockException;
import org.example.db_project.model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order rules. Orders are NOT cached: they change on every purchase and status
 * update, so a cache would mostly hold stale data (unlike categories/products).
 */
public class OrderService {

    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final int MAX_QUANTITY_PER_LINE = 1000;

    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();

    public List<Order> getOrders(OrderStatus statusFilter) throws SQLException {
        return orderDAO.findAll(statusFilter);
    }

    public List<OrderItem> getItems(int orderId) throws SQLException {
        return orderDAO.findItems(orderId);
    }

    public List<User> getCustomers() throws SQLException {
        return userDAO.findCustomers();
    }

    /**
     * Adds a product to the cart, or increases its quantity if it's already there.
     * The stock check here is only a quick early warning; the real check happens
     * inside the placeOrder transaction.
     */
    public void addToCart(List<OrderItem> cart, Product product, String quantityText) {
        if (product == null) {
            throw new IllegalArgumentException("Find and choose a product first.");
        }
        int quantity = parseQuantity(quantityText);

        OrderItem existing = cart.stream()
                .filter(i -> i.getProductId() == product.getProductId())
                .findFirst()
                .orElse(null);
        int newQuantity = quantity + (existing == null ? 0 : existing.getQuantity());

        if (newQuantity > product.getStockQuantity()) {
            throw new IllegalArgumentException("Only " + product.getStockQuantity() + " of \""
                    + product.getName() + "\" in stock.");
        }
        if (newQuantity > MAX_QUANTITY_PER_LINE) {
            throw new IllegalArgumentException("At most " + MAX_QUANTITY_PER_LINE + " units per product.");
        }

        if (existing != null) {
            existing.setQuantity(newQuantity);
        } else {
            cart.add(new OrderItem(product.getProductId(), product.getName(), quantity, product.getPrice()));
        }
    }

    /** @return the new order's id */
    public int placeOrder(User customer, List<OrderItem> cart) throws SQLException {
        if (customer == null) {
            throw new IllegalArgumentException("Choose a customer.");
        }
        if (cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException("Add at least one product to the order.");
        }

        // Merge duplicate products using a hash map keyed by product id (order_items has
        // PRIMARY KEY (order_id, product_id), so each product may appear only once per order).
        Map<Integer, OrderItem> merged = new LinkedHashMap<>();
        for (OrderItem line : cart) {
            if (line.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantities must be at least 1.");
            }
            OrderItem copy = new OrderItem(line.getProductId(), line.getProductName(),
                    line.getQuantity(), line.getUnitPrice());
            merged.merge(line.getProductId(), copy, (a, b) -> {
                a.setQuantity(a.getQuantity() + b.getQuantity());
                return a;
            });
        }

        try {
            return orderDAO.placeOrder(customer.getUserId(), new ArrayList<>(merged.values()));
        } catch (InsufficientStockException e) {
            throw new IllegalArgumentException("Not enough stock for \"" + e.getProductName() + "\": only "
                    + e.getAvailable() + " left. The order was not saved.");
        } catch (SQLException e) {
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalArgumentException("The customer or a product no longer exists. The order was not saved.");
            }
            throw e;
        }
    }

    public void changeStatus(Order order, OrderStatus newStatus) throws SQLException {
        if (order == null) {
            throw new IllegalArgumentException("Select an order first.");
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("Choose the new status.");
        }
        if (!order.getStatus().canChangeTo(newStatus)) {
            throw new IllegalArgumentException("An order that is " + order.getStatus()
                    + " can't be changed to " + newStatus + ".");
        }
        boolean changed = orderDAO.changeStatus(order.getOrderId(), order.getStatus(), newStatus);
        if (!changed) {
            throw new IllegalArgumentException("This order was changed elsewhere. The list has been refreshed; try again.");
        }
    }

    private int parseQuantity(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter a quantity.");
        }
        int quantity;
        try {
            quantity = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Quantity must be a whole number.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }
        return quantity;
    }
}
