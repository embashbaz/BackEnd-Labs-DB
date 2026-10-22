package org.example.db_project.dao;



import org.example.db_project.model.Product;
import org.example.db_project.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    private static final String BASE_SELECT =
            "SELECT p.product_id, p.category_id, c.name AS category_name, p.name, p.description, "
          + "       p.price, COALESCE(i.quantity_on_hand, 0) AS stock "
          + "FROM products p "
          + "JOIN categories c ON c.category_id = p.category_id "
          + "LEFT JOIN inventory i ON i.product_id = p.product_id ";

    /**
     * Case-insensitive search with optional category filter and pagination.
     * keyword may be empty; categoryId may be null (= all categories).
     */
    public List<Product> search(String keyword, Integer categoryId, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, categoryId);
        sql.append("ORDER BY p.name, p.product_id LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Product> products = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapRow(rs));
                }
            }
        }
        return products;
    }

    /** Total number of matches, so the UI can work out how many pages there are. */
    public int count(String keyword, Integer categoryId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, categoryId);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Inserts the product and its inventory row in ONE transaction: both succeed or neither does. */
    public Product insert(Product product, int stock) throws SQLException {
        String productSql = "INSERT INTO products (category_id, name, description, price) "
                          + "VALUES (?, ?, ?, ?) RETURNING product_id";
        String stockSql = "INSERT INTO inventory (product_id, quantity_on_hand) VALUES (?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(productSql);
                 PreparedStatement stockPs = con.prepareStatement(stockSql)) {

                ps.setInt(1, product.getCategoryId());
                ps.setString(2, product.getName());
                ps.setString(3, product.getDescription());
                ps.setBigDecimal(4, product.getPrice());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    product.setProductId(rs.getInt(1));
                }

                stockPs.setInt(1, product.getProductId());
                stockPs.setInt(2, stock);
                stockPs.executeUpdate();

                con.commit();
                product.setStockQuantity(stock);
                return product;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Updates product details and stock in one transaction. Returns false if the product doesn't exist. */
    public boolean update(Product product, int stock) throws SQLException {
        String productSql = "UPDATE products SET category_id = ?, name = ?, description = ?, price = ?, "
                          + "updated_at = CURRENT_TIMESTAMP WHERE product_id = ?";
        // "Upsert": insert the inventory row, or update it if it already exists
        String stockSql = "INSERT INTO inventory (product_id, quantity_on_hand) VALUES (?, ?) "
                        + "ON CONFLICT (product_id) DO UPDATE "
                        + "SET quantity_on_hand = EXCLUDED.quantity_on_hand, updated_at = CURRENT_TIMESTAMP";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(productSql);
                 PreparedStatement stockPs = con.prepareStatement(stockSql)) {

                ps.setInt(1, product.getCategoryId());
                ps.setString(2, product.getName());
                ps.setString(3, product.getDescription());
                ps.setBigDecimal(4, product.getPrice());
                ps.setInt(5, product.getProductId());
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }

                stockPs.setInt(1, product.getProductId());
                stockPs.setInt(2, stock);
                stockPs.executeUpdate();

                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Inventory and reviews are removed by ON DELETE CASCADE. Fails if the product was ever ordered. */
    public boolean delete(int productId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() == 1;
        }
    }

    // ---------- helpers ----------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, Integer categoryId) {
        sql.append("WHERE 1 = 1 ");
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND p.name ILIKE ? ");          // ILIKE = case-insensitive LIKE in PostgreSQL
            params.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (categoryId != null) {
            sql.append("AND p.category_id = ? ");
            params.add(categoryId);
        }
    }

    /** Stops a user typing % or _ from acting as a wildcard. */
    private String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("product_id"),
                rs.getInt("category_id"),
                rs.getString("category_name"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"));
    }
}
