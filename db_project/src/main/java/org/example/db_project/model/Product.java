package org.example.db_project.model;

import java.math.BigDecimal;

public class Product {

    private int productId;
    private int categoryId;
    private String categoryName;   // filled from a JOIN, for display only
    private String name;
    private String description;
    private BigDecimal price;      // BigDecimal, never double, for money
    private int stockQuantity;     // from the inventory table

    public Product() {}

    public Product(int productId, int categoryId, String categoryName, String name,
                   String description, BigDecimal price, int stockQuantity) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    @Override
    public String toString() { return name; }
}
