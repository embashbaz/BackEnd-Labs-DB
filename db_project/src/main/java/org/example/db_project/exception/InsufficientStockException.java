package org.example.db_project.exception;

/** Thrown when an order asks for more units than are in stock. */
public class InsufficientStockException extends RuntimeException {

    private final String productName;
    private final int available;

    public InsufficientStockException(String productName, int available) {
        super("Not enough stock for " + productName + " (available: " + available + ")");
        this.productName = productName;
        this.available = available;
    }

    public String getProductName() { return productName; }
    public int getAvailable() { return available; }
}
