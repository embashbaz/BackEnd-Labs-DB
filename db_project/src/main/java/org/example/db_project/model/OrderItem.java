package org.example.db_project.model;

import java.math.BigDecimal;

/** One line of an order. Also used for lines in the "new order" cart. */
public class OrderItem {

    private final int productId;
    private final String productName;
    private int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(int productId, String productName, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
