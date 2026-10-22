package org.example.db_project.service;

import org.example.db_project.dao.CategoryDAO;
import org.example.db_project.model.Category;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business rules for categories: validation, friendly error messages and an
 * in-memory cache. Validation problems are thrown as IllegalArgumentException
 * so the controller can show them to the user.
 */
public class CategoryService {

    private static final int MAX_NAME_LENGTH = 100;

    // PostgreSQL error codes
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final CategoryDAO dao = new CategoryDAO();

    // Hash-based cache: id -> Category, O(1) lookup. LinkedHashMap keeps DB order (by name).
    private final Map<Integer, Category> cache = new LinkedHashMap<>();
    private boolean cacheValid = false;

    public List<Category> getAllCategories() throws SQLException {
        if (!cacheValid) {
            refreshCache();
        }
        return new ArrayList<>(cache.values());
    }

    public Category getById(int id) throws SQLException {
        if (!cacheValid) {
            refreshCache();
        }
        return cache.get(id);
    }

    public Category addCategory(String name, String description) throws SQLException {
        String cleanName = validateName(name);
        if (dao.existsByName(cleanName, 0)) {
            throw new IllegalArgumentException("A category named \"" + cleanName + "\" already exists.");
        }
        try {
            Category created = dao.insert(new Category(cleanName, cleanText(description)));
            invalidateCache();
            return created;
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public void updateCategory(int id, String name, String description) throws SQLException {
        String cleanName = validateName(name);
        if (dao.existsByName(cleanName, id)) {
            throw new IllegalArgumentException("Another category is already named \"" + cleanName + "\".");
        }
        try {
            boolean updated = dao.update(new Category(id, cleanName, cleanText(description)));
            invalidateCache();
            if (!updated) {
                throw new IllegalArgumentException("That category no longer exists.");
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    public void deleteCategory(int id) throws SQLException {
        try {
            boolean deleted = dao.delete(id);
            invalidateCache();
            if (!deleted) {
                throw new IllegalArgumentException("That category no longer exists.");
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    // ---------- helpers ----------

    private String validateName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Category name is required.");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Category name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return clean;
    }

    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private void refreshCache() throws SQLException {
        cache.clear();
        for (Category c : dao.findAll()) {
            cache.put(c.getCategoryId(), c);
        }
        cacheValid = true;
    }

    /** Called after every write so the cache never shows stale data. */
    private void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    /** Turns known constraint errors into messages a user can understand. */
    private SQLException translate(SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            throw new IllegalArgumentException("A category with that name already exists.");
        }
        if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
            throw new IllegalArgumentException("This category still has products. Move or delete them first.");
        }
        return e;
    }
}