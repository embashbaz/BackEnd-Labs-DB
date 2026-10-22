package org.example.db_project.model;

import java.util.List;

/**
 * A report as a generic table: column names plus rows of values.
 * Column names come from the SQL aliases, so each report defines its own columns.
 * elapsedMillis is how long the query took, which is useful for the performance report.
 */
public record ReportResult(String title, List<String> columns, List<List<Object>> rows, double elapsedMillis) {
}
