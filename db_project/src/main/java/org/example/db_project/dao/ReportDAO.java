package org.example.db_project.dao;


import org.example.db_project.model.ReportResult;
import org.example.db_project.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reporting queries. Each method builds a parameterized query and returns the
 * result as a generic table, using the quoted SQL aliases as column headings.
 * from/to dates are optional (null = no limit on that side) and inclusive.
 */
public class ReportDAO {

    /** JOIN + GROUP BY + aggregates. */
    public ReportResult bestSellers(LocalDate from, LocalDate to, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql =
                "SELECT p.product_id AS \"ID\", "
              + "       p.name AS \"Product\", "
              + "       c.name AS \"Category\", "
              + "       SUM(oi.quantity) AS \"Units sold\", "
              + "       SUM(oi.quantity * oi.unit_price) AS \"Revenue\", "
              + "       COUNT(DISTINCT o.order_id) AS \"Orders\" "
              + "FROM order_items oi "
              + "JOIN orders o ON o.order_id = oi.order_id "
              + "JOIN products p ON p.product_id = oi.product_id "
              + "JOIN categories c ON c.category_id = p.category_id "
              + "WHERE o.status <> 'CANCELLED' "
              + dateFilter("o.order_date", from, to, params)
              + "GROUP BY p.product_id, p.name, c.name "
              + "ORDER BY \"Units sold\" DESC, \"Revenue\" DESC "
              + "LIMIT ?";
        params.add(limit);
        return run("Best-selling products", sql, params);
    }

    /**
     * LEFT JOIN onto a subquery so categories with no sales still appear (with 0),
     * plus a window function (SUM(...) OVER ()) to get each category's share of the total.
     */
    public ReportResult revenueByCategory(LocalDate from, LocalDate to) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql =
                "SELECT c.name AS \"Category\", "
              + "       COUNT(DISTINCT s.order_id) AS \"Orders\", "
              + "       COALESCE(SUM(s.quantity), 0) AS \"Units sold\", "
              + "       COALESCE(SUM(s.quantity * s.unit_price), 0) AS \"Revenue\", "
              + "       COALESCE(ROUND(100.0 * SUM(s.quantity * s.unit_price) "
              + "             / NULLIF(SUM(SUM(s.quantity * s.unit_price)) OVER (), 0), 1), 0) AS \"Share %\" "
              + "FROM categories c "
              + "LEFT JOIN ( "
              + "    SELECT p.category_id, oi.order_id, oi.quantity, oi.unit_price "
              + "    FROM order_items oi "
              + "    JOIN orders o ON o.order_id = oi.order_id "
              + "    JOIN products p ON p.product_id = oi.product_id "
              + "    WHERE o.status <> 'CANCELLED' "
              +      dateFilter("o.order_date", from, to, params)
              + ") s ON s.category_id = c.category_id "
              + "GROUP BY c.category_id, c.name "
              + "ORDER BY \"Revenue\" DESC, c.name";
        return run("Revenue by category", sql, params);
    }

    /** Compares two columns in WHERE, and uses CASE to label the urgency. */
    public ReportResult lowStock() throws SQLException {
        String sql =
                "SELECT p.product_id AS \"ID\", "
              + "       p.name AS \"Product\", "
              + "       c.name AS \"Category\", "
              + "       i.quantity_on_hand AS \"In stock\", "
              + "       i.reorder_level AS \"Reorder level\", "
              + "       CASE WHEN i.quantity_on_hand = 0 THEN 'OUT OF STOCK' ELSE 'LOW' END AS \"Status\" "
              + "FROM inventory i "
              + "JOIN products p ON p.product_id = i.product_id "
              + "JOIN categories c ON c.category_id = p.category_id "
              + "WHERE i.quantity_on_hand <= i.reorder_level "
              + "ORDER BY i.quantity_on_hand, (i.reorder_level - i.quantity_on_hand) DESC, p.name";
        return run("Low-stock alert", sql, List.of());
    }

    /** GROUP BY + HAVING (filters groups after aggregation, unlike WHERE). */
    public ReportResult topCustomers(LocalDate from, LocalDate to, int minOrders, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql =
                "SELECT u.user_id AS \"ID\", "
              + "       u.full_name AS \"Customer\", "
              + "       u.email AS \"Email\", "
              + "       COUNT(DISTINCT o.order_id) AS \"Orders\", "
              + "       SUM(oi.quantity * oi.unit_price) AS \"Total spent\", "
              + "       ROUND(SUM(oi.quantity * oi.unit_price) / COUNT(DISTINCT o.order_id), 2) AS \"Avg order value\", "
              + "       MAX(o.order_date) AS \"Last order\" "
              + "FROM users u "
              + "JOIN orders o ON o.user_id = u.user_id "
              + "JOIN order_items oi ON oi.order_id = o.order_id "
              + "WHERE o.status <> 'CANCELLED' "
              + dateFilter("o.order_date", from, to, params)
              + "GROUP BY u.user_id, u.full_name, u.email "
              + "HAVING COUNT(DISTINCT o.order_id) >= ? "
              + "ORDER BY \"Total spent\" DESC "
              + "LIMIT ?";
        params.add(minOrders);
        params.add(limit);
        return run("Top customers", sql, params);
    }

    // ---------- helpers ----------

    /**
     * Adds optional date conditions. "to" is inclusive, so we compare with the start of the NEXT day.
     * The column name is a fixed string from this class, never user input, so concatenating it is safe.
     */
    private String dateFilter(String column, LocalDate from, LocalDate to, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        if (from != null) {
            sb.append("AND ").append(column).append(" >= ? ");
            params.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sb.append("AND ").append(column).append(" < ? ");
            params.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        return sb.toString();
    }

    /** Runs any report query and converts the result set into a generic table, timing the query. */
    private ReportResult run(String title, String sql, List<Object> params) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            long start = System.nanoTime();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }

                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }

                double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;
                return new ReportResult(title, columns, rows, elapsedMillis);
            }
        }
    }
}
