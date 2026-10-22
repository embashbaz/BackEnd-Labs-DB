package org.example.db_project.model;

import java.util.Arrays;
import java.util.List;

/** Matches the CHECK constraint on orders.status. */
public enum OrderStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CANCELLED;

    /** The allowed status changes: an order moves forward, or is cancelled before shipping. */
    public boolean canChangeTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;    // final states
        };
    }

    public List<OrderStatus> nextOptions() {
        return Arrays.stream(values()).filter(this::canChangeTo).toList();
    }
}
