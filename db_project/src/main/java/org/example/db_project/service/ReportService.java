package org.example.db_project.service;


import org.example.db_project.dao.ReportDAO;
import org.example.db_project.model.ReportResult;
import org.example.db_project.model.ReportType;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Validates report filters and picks the right query.
 * Reports are not cached: they should always reflect the latest orders and stock.
 */
public class ReportService {

    private static final int MAX_LIMIT = 1000;

    private final ReportDAO dao = new ReportDAO();

    public ReportResult run(ReportType type, LocalDate from, LocalDate to, int limit, int minOrders) throws SQLException {
        if (type == null) {
            throw new IllegalArgumentException("Choose a report.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("The start date is after the end date.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Show between 1 and " + MAX_LIMIT + " rows.");
        }
        if (minOrders < 1) {
            throw new IllegalArgumentException("Minimum orders must be at least 1.");
        }

        return switch (type) {
            case BEST_SELLERS -> dao.bestSellers(from, to, limit);
            case REVENUE_BY_CATEGORY -> dao.revenueByCategory(from, to);
            case LOW_STOCK -> dao.lowStock();
            case TOP_CUSTOMERS -> dao.topCustomers(from, to, minOrders, limit);
        };
    }
}
