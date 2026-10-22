package org.example.db_project.model;

/** The available reports, and which filter controls each one uses. */
public enum ReportType {

    BEST_SELLERS("Best-selling products",
            "Units sold and revenue per product. Cancelled orders are excluded.",
            true, true, false),

    REVENUE_BY_CATEGORY("Revenue by category",
            "Sales per category (including categories with no sales) and each category's share of total revenue.",
            true, false, false),

    LOW_STOCK("Low-stock alert",
            "Products at or below their reorder level, most urgent first.",
            false, false, false),

    TOP_CUSTOMERS("Top customers",
            "Orders placed and money spent per customer. Cancelled orders are excluded.",
            true, true, true);

    private final String label;
    private final String description;
    private final boolean usesDateRange;
    private final boolean usesLimit;
    private final boolean usesMinOrders;

    ReportType(String label, String description, boolean usesDateRange, boolean usesLimit, boolean usesMinOrders) {
        this.label = label;
        this.description = description;
        this.usesDateRange = usesDateRange;
        this.usesLimit = usesLimit;
        this.usesMinOrders = usesMinOrders;
    }

    public String getDescription() { return description; }
    public boolean usesDateRange() { return usesDateRange; }
    public boolean usesLimit() { return usesLimit; }
    public boolean usesMinOrders() { return usesMinOrders; }

    // Shown in the report ComboBox
    @Override
    public String toString() { return label; }
}
