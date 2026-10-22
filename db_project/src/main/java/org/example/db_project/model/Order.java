package org.example.db_project.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Order {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final int orderId;
    private final int userId;
    private final String customerName;
    private final LocalDateTime orderDate;
    private final OrderStatus status;
    private final BigDecimal totalAmount;   // calculated by the order_totals view
    private final int itemCount;

    public Order(int orderId, int userId, String customerName, LocalDateTime orderDate,
                 OrderStatus status, BigDecimal totalAmount, int itemCount) {
        this.orderId = orderId;
        this.userId = userId;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
    }

    public int getOrderId() { return orderId; }
    public int getUserId() { return userId; }
    public String getCustomerName() { return customerName; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public int getItemCount() { return itemCount; }

    /** Used by the table's date column. */
    public String getFormattedDate() {
        return orderDate == null ? "" : orderDate.format(DISPLAY_FORMAT);
    }
}
