package org.example.db_project.dao;



import org.example.db_project.exception.InsufficientStockException;
import org.example.db_project.model.Order;
import org.example.db_project.model.OrderItem;
import org.example.db_project.model.OrderStatus;
import org.example.db_project.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OrderDAO {

    /** All orders, newest first. status may be null to show every status. */
    public List<Order> findAll(OrderStatus status) throws SQLException {
        String sql = "SELECT t.order_id, t.user_id, u.full_name, t.order_date, t.status, t.total_amount, "
                   + "       (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = t.order_id) AS item_count "
                   + "FROM order_totals t "
                   + "JOIN users u ON u.user_id = t.user_id "
                   + (status != null ? "WHERE t.status = ? " : "")
                   + "ORDER BY t.order_date DESC, t.order_id DESC";

        List<Order> orders = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (status != null) {
                ps.setString(1, status.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new Order(
                            rs.getInt("order_id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getTimestamp("order_date").toLocalDateTime(),
                            OrderStatus.valueOf(rs.getString("status")),
                            rs.getBigDecimal("total_amount"),
                            rs.getInt("item_count")));
                }
            }
        }
        return orders;
    }

    public List<OrderItem> findItems(int orderId) throws SQLException {
        String sql = "SELECT oi.product_id, p.name, oi.quantity, oi.unit_price "
                   + "FROM order_items oi JOIN products p ON p.product_id = oi.product_id "
                   + "WHERE oi.order_id = ? ORDER BY p.name";
        List<OrderItem> items = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new OrderItem(
                            rs.getInt("product_id"),
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getBigDecimal("unit_price")));
                }
            }
        }
        return items;
    }

    /**
     * Places an order in ONE transaction:
     *   1. insert the order row
     *   2. for each line: reduce stock (only if enough is available), then insert the order item
     * If any product lacks stock, everything is rolled back and nothing is saved.
     *
     * @return the new order's id
     */
    public int placeOrder(int userId, List<OrderItem> items) throws SQLException, InsufficientStockException {
        String orderSql = "INSERT INTO orders (user_id) VALUES (?) RETURNING order_id";

        // The "AND quantity_on_hand >= ?" makes the check and the decrement a single atomic step,
        // so two simultaneous orders can never both take the last unit.
        String stockSql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand - ?, "
                        + "updated_at = CURRENT_TIMESTAMP "
                        + "WHERE product_id = ? AND quantity_on_hand >= ?";

        // unit_price comes from the database, not from the UI, so the price can't be tampered with
        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price) "
                       + "SELECT ?, product_id, ?, price FROM products WHERE product_id = ?";

        String availableSql = "SELECT COALESCE((SELECT quantity_on_hand FROM inventory WHERE product_id = ?), 0)";

        // Always lock products in the same order (by id) to avoid deadlocks between concurrent orders
        List<OrderItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(OrderItem::getProductId));

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement orderPs = con.prepareStatement(orderSql);
                 PreparedStatement stockPs = con.prepareStatement(stockSql);
                 PreparedStatement itemPs = con.prepareStatement(itemSql);
                 PreparedStatement availablePs = con.prepareStatement(availableSql)) {

                int orderId;
                orderPs.setInt(1, userId);
                try (ResultSet rs = orderPs.executeQuery()) {
                    rs.next();
                    orderId = rs.getInt(1);
                }

                for (OrderItem item : sorted) {
                    stockPs.setInt(1, item.getQuantity());
                    stockPs.setInt(2, item.getProductId());
                    stockPs.setInt(3, item.getQuantity());
                    if (stockPs.executeUpdate() == 0) {
                        int available = currentStock(availablePs, item.getProductId());
                        con.rollback();
                        throw new InsufficientStockException(item.getProductName(), available);
                    }

                    itemPs.setInt(1, orderId);
                    itemPs.setInt(2, item.getQuantity());
                    itemPs.setInt(3, item.getProductId());
                    itemPs.executeUpdate();
                }

                con.commit();
                return orderId;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Changes an order's status, but only if it still has the status the user saw
     * (protects against two people editing the same order). Cancelling puts the stock back.
     *
     * @return false if the order no longer has expectedCurrent status
     */
    public boolean changeStatus(int orderId, OrderStatus expectedCurrent, OrderStatus newStatus) throws SQLException {
        String statusSql = "UPDATE orders SET status = ? WHERE order_id = ? AND status = ?";
        String restockSql = "UPDATE inventory i "
                          + "SET quantity_on_hand = i.quantity_on_hand + oi.quantity, updated_at = CURRENT_TIMESTAMP "
                          + "FROM order_items oi "
                          + "WHERE oi.order_id = ? AND i.product_id = oi.product_id";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement statusPs = con.prepareStatement(statusSql);
                 PreparedStatement restockPs = con.prepareStatement(restockSql)) {

                statusPs.setString(1, newStatus.name());
                statusPs.setInt(2, orderId);
                statusPs.setString(3, expectedCurrent.name());
                if (statusPs.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }

                if (newStatus == OrderStatus.CANCELLED) {
                    restockPs.setInt(1, orderId);
                    restockPs.executeUpdate();
                }

                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private int currentStock(PreparedStatement availablePs, int productId) throws SQLException {
        availablePs.setInt(1, productId);
        try (ResultSet rs = availablePs.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
