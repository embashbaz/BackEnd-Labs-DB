package org.example.db_project.service;


import org.example.db_project.dao.ProductDAO;
import org.example.db_project.model.Category;
import org.example.db_project.model.Product;
import org.example.db_project.model.ProductPage;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductService {

    public static final int PAGE_SIZE = 10;
    private static final int MAX_NAME_LENGTH = 150;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99"); // NUMERIC(10,2) limit

    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    private final ProductDAO dao = new ProductDAO();

    // Search-result cache: "keyword|categoryId|page" -> page of results
    private final Map<String, ProductPage> pageCache = new HashMap<>();
    private int cacheHits = 0;
    private int cacheMisses = 0;

    public ProductPage search(String keyword, Integer categoryId, int page) throws SQLException {
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        int requestedPage = Math.max(1, page);

        // Lower-casing the key matches the case-insensitive search,
        // so "Phone" and "phone" share one cache entry.
        String cacheKey = cleanKeyword.toLowerCase(Locale.ROOT) + "|" + categoryId + "|" + requestedPage;
        ProductPage cached = pageCache.get(cacheKey);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;

        int total = dao.count(cleanKeyword, categoryId);
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(requestedPage, totalPages);
        List<Product> products = dao.search(cleanKeyword, categoryId, PAGE_SIZE, (actualPage - 1) * PAGE_SIZE);

        ProductPage result = new ProductPage(products, total, actualPage, PAGE_SIZE);
        pageCache.put(cacheKey, result);
        return result;
    }

    public Product addProduct(String name, Category category, String priceText,
                              String stockText, String description) throws SQLException {
        Product product = buildValidated(0, name, category, priceText, description);
        int stock = parseStock(stockText);
        try {
            Product created = dao.insert(product, stock);
            invalidateCache();
            return created;
        } catch (SQLException e) {
            throw translate(e, "The selected category no longer exists.");
        }
    }

    public void updateProduct(int productId, String name, Category category, String priceText,
                              String stockText, String description) throws SQLException {
        Product product = buildValidated(productId, name, category, priceText, description);
        int stock = parseStock(stockText);
        try {
            boolean updated = dao.update(product, stock);
            invalidateCache();
            if (!updated) {
                throw new IllegalArgumentException("That product no longer exists.");
            }
        } catch (SQLException e) {
            throw translate(e, "The selected category no longer exists.");
        }
    }

    public void deleteProduct(int productId) throws SQLException {
        try {
            boolean deleted = dao.delete(productId);
            invalidateCache();
            if (!deleted) {
                throw new IllegalArgumentException("That product no longer exists.");
            }
        } catch (SQLException e) {
            throw translate(e, "This product appears in existing orders, so it can't be deleted.");
        }
    }

    /** Clears cached search results. Called after every write, and when data may have changed elsewhere. */
    public void invalidateCache() {
        pageCache.clear();
    }

    public int getCacheHits() { return cacheHits; }
    public int getCacheMisses() { return cacheMisses; }

    // ---------- validation ----------

    private Product buildValidated(int productId, String name, Category category,
                                   String priceText, String description) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Product name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Choose a category.");
        }

        Product p = new Product();
        p.setProductId(productId);
        p.setCategoryId(category.getCategoryId());
        p.setName(cleanName);
        p.setDescription(description == null || description.isBlank() ? null : description.trim());
        p.setPrice(parsePrice(priceText));
        return p;
    }

    private BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Price is required.");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Price must be a number, e.g. 19.99.");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price can't be negative.");
        }
        if (price.scale() > 2) {
            throw new IllegalArgumentException("Price can have at most 2 decimal places.");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Price is too large.");
        }
        return price.setScale(2);
    }

    private int parseStock(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Stock quantity is required (use 0 if none).");
        }
        int stock;
        try {
            stock = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stock must be a whole number.");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("Stock can't be negative.");
        }
        return stock;
    }

    private SQLException translate(SQLException e, String foreignKeyMessage) {
        if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
            throw new IllegalArgumentException(foreignKeyMessage);
        }
        if (CHECK_VIOLATION.equals(e.getSQLState())) {
            throw new IllegalArgumentException("One of the values is out of the allowed range.");
        }
        return e;
    }
}
