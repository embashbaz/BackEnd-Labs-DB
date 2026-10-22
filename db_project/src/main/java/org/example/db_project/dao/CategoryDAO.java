package org.example.db_project.dao;


import org.example.db_project.model.Category;
import org.example.db_project.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL for the categories table lives here. Every query is parameterized. */
public class CategoryDAO {

    public List<Category> findAll() throws SQLException {
        String sql = "SELECT category_id, name, description FROM categories ORDER BY name";
        List<Category> categories = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(mapRow(rs));
            }
        }
        return categories;
    }

    public Optional<Category> findById(int id) throws SQLException {
        String sql = "SELECT category_id, name, description FROM categories WHERE category_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /** True if another category already uses this name (case-insensitive). */
    public boolean existsByName(String name, int excludeId) throws SQLException {
        String sql = "SELECT 1 FROM categories WHERE LOWER(name) = LOWER(?) AND category_id <> ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Category insert(Category category) throws SQLException {
        // RETURNING is PostgreSQL's way to get the generated id back
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?) RETURNING category_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    category.setCategoryId(rs.getInt("category_id"));
                }
            }
        }
        return category;
    }

    /** Returns false if no row had that id. */
    public boolean update(Category category) throws SQLException {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE category_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getCategoryId());
            return ps.executeUpdate() == 1;
        }
    }

    /** Returns false if no row had that id. */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM categories WHERE category_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getInt("category_id"),
                rs.getString("name"),
                rs.getString("description"));
    }
}