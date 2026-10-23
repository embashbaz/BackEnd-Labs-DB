package org.example.db_project.benchmark;


import org.example.db_project.model.ProductPage;
import org.example.db_project.service.ProductService;
import org.example.db_project.util.DBConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Times the app's main queries and records the execution plan PostgreSQL chose.
 *
 * Usage (program argument):
 *   before   -> run the benchmark and save benchmark-results/before.csv
 *   after    -> run the benchmark and save benchmark-results/after.csv
 *   compare  -> read both files and write benchmark-results/comparison.md
 *
 * Each query runs on ONE shared connection (so connection setup doesn't distort the numbers),
 * with warm-up runs first and the median of the measured runs reported.
 * "Server ms" is PostgreSQL's own execution time from EXPLAIN ANALYZE, which excludes
 * network and JDBC overhead, so it is the fairest number for comparing indexes.
 *
 * The SQL below mirrors ProductDAO and ReportDAO. If you change a query there, update it here too.
 */
public class PerformanceBenchmark {

    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 15;
    private static final Path RESULTS_DIR = Path.of("benchmark-results");

    // A month inside the bulk data's date range (2024-10-01 to 2026-10-01)
    private static final Timestamp MONTH_START = Timestamp.valueOf("2026-08-01 00:00:00");
    private static final Timestamp MONTH_END = Timestamp.valueOf("2026-09-01 00:00:00");

    // ----- mirrored from ProductDAO -----
    private static final String PRODUCT_SELECT =
            "SELECT p.product_id, p.category_id, c.name AS category_name, p.name, p.description, "
          + "       p.price, COALESCE(i.quantity_on_hand, 0) AS stock "
          + "FROM products p "
          + "JOIN categories c ON c.category_id = p.category_id "
          + "LEFT JOIN inventory i ON i.product_id = p.product_id ";

    // ----- mirrored from ReportDAO -----
    private static final String BEST_SELLERS_START =
            "SELECT p.product_id, p.name, c.name, SUM(oi.quantity) AS units, "
          + "       SUM(oi.quantity * oi.unit_price) AS revenue, COUNT(DISTINCT o.order_id) "
          + "FROM order_items oi "
          + "JOIN orders o ON o.order_id = oi.order_id "
          + "JOIN products p ON p.product_id = oi.product_id "
          + "JOIN categories c ON c.category_id = p.category_id "
          + "WHERE o.status <> 'CANCELLED' ";
    private static final String BEST_SELLERS_END =
            "GROUP BY p.product_id, p.name, c.name ORDER BY units DESC, revenue DESC LIMIT ?";

    private static final String REVENUE_BY_CATEGORY_MONTH =
            "SELECT c.name, COUNT(DISTINCT s.order_id), COALESCE(SUM(s.quantity), 0), "
          + "       COALESCE(SUM(s.quantity * s.unit_price), 0) AS revenue, "
          + "       COALESCE(ROUND(100.0 * SUM(s.quantity * s.unit_price) "
          + "             / NULLIF(SUM(SUM(s.quantity * s.unit_price)) OVER (), 0), 1), 0) "
          + "FROM categories c "
          + "LEFT JOIN ( "
          + "    SELECT p.category_id, oi.order_id, oi.quantity, oi.unit_price "
          + "    FROM order_items oi "
          + "    JOIN orders o ON o.order_id = oi.order_id "
          + "    JOIN products p ON p.product_id = oi.product_id "
          + "    WHERE o.status <> 'CANCELLED' AND o.order_date >= ? AND o.order_date < ? "
          + ") s ON s.category_id = c.category_id "
          + "GROUP BY c.category_id, c.name ORDER BY revenue DESC, c.name";

    private static final String TOP_CUSTOMERS_MONTH =
            "SELECT u.user_id, u.full_name, u.email, COUNT(DISTINCT o.order_id), "
          + "       SUM(oi.quantity * oi.unit_price) AS spent, "
          + "       ROUND(SUM(oi.quantity * oi.unit_price) / COUNT(DISTINCT o.order_id), 2), MAX(o.order_date) "
          + "FROM users u "
          + "JOIN orders o ON o.user_id = u.user_id "
          + "JOIN order_items oi ON oi.order_id = o.order_id "
          + "WHERE o.status <> 'CANCELLED' AND o.order_date >= ? AND o.order_date < ? "
          + "GROUP BY u.user_id, u.full_name, u.email "
          + "HAVING COUNT(DISTINCT o.order_id) >= ? "
          + "ORDER BY spent DESC LIMIT ?";

    private static final String LOW_STOCK =
            "SELECT p.product_id, p.name, c.name, i.quantity_on_hand, i.reorder_level "
          + "FROM inventory i "
          + "JOIN products p ON p.product_id = i.product_id "
          + "JOIN categories c ON c.category_id = p.category_id "
          + "WHERE i.quantity_on_hand <= i.reorder_level "
          + "ORDER BY i.quantity_on_hand, (i.reorder_level - i.quantity_on_hand) DESC, p.name";

    record BenchmarkQuery(String name, String sql, List<Object> params) {}

    record Result(String name, double medianMs, double minMs, double serverMs, int rows, String plan) {}

    public static void main(String[] args) throws Exception {
        String label = args.length > 0 ? args[0].trim().toLowerCase() : "run";
        if (label.equals("compare")) {
            compare();
            return;
        }

        List<Result> results = new ArrayList<>();
        try (Connection con = DBConnection.getConnection()) {
            printDataSize(con);
            int categoryId = singleInt(con, "SELECT category_id FROM categories WHERE name = 'Electronics'");
            int customerId = singleInt(con, "SELECT user_id FROM users WHERE email = 'customer42@bulk.test'");

            for (BenchmarkQuery q : queries(categoryId, customerId)) {
                System.out.println("Running: " + q.name());
                results.add(measure(con, q));
            }
        }
        System.out.println("Running: cache comparison");
        results.addAll(cacheBenchmark());

        printResults(label, results);
        Result miss = results.get(results.size() - 2);
        Result hit = results.get(results.size() - 1);
        System.out.printf("%nCache: a hit is %.0fx faster than going to the database (%.3f ms vs %.2f ms).%n",
                miss.medianMs() / hit.medianMs(), hit.medianMs(), miss.medianMs());
        Path file = saveCsv(label, results);
        System.out.println("\nSaved " + file.toAbsolutePath());
    }

    // ---------- the queries being measured ----------

    private static List<BenchmarkQuery> queries(int categoryId, int customerId) {
        String search = PRODUCT_SELECT + "WHERE 1 = 1 AND p.name ILIKE ? ORDER BY p.name, p.product_id LIMIT ? OFFSET ?";
        String browse = PRODUCT_SELECT + "WHERE 1 = 1 ORDER BY p.name, p.product_id LIMIT ? OFFSET ?";
        String browseCategory = PRODUCT_SELECT + "WHERE 1 = 1 AND p.category_id = ? ORDER BY p.name, p.product_id LIMIT ? OFFSET ?";

        return List.of(
                new BenchmarkQuery("Search 'phone' (common word), page 1", search, List.of("%phone%", 10, 0)),
                new BenchmarkQuery("Search '4242' (rare), page 1", search, List.of("%4242%", 10, 0)),
                new BenchmarkQuery("Search count for 'phone'",
                        "SELECT COUNT(*) FROM products p WHERE 1 = 1 AND p.name ILIKE ?", List.of("%phone%")),
                new BenchmarkQuery("Browse all products, page 1", browse, List.of(10, 0)),
                new BenchmarkQuery("Browse all products, page 5000 (deep OFFSET)", browse, List.of(10, 49990)),
                new BenchmarkQuery("Browse one category, page 1", browseCategory, List.of(categoryId, 10, 0)),
                new BenchmarkQuery("Order history of one customer",
                        "SELECT o.order_id, o.order_date, o.status FROM orders o WHERE o.user_id = ? ORDER BY o.order_date DESC",
                        List.of(customerId)),
                new BenchmarkQuery("Count orders in one month",
                        "SELECT COUNT(*) FROM orders o WHERE o.order_date >= ? AND o.order_date < ?",
                        List.of(MONTH_START, MONTH_END)),
                new BenchmarkQuery("Report: best sellers (all time)",
                        BEST_SELLERS_START + BEST_SELLERS_END, List.of(10)),
                new BenchmarkQuery("Report: best sellers (one month)",
                        BEST_SELLERS_START + "AND o.order_date >= ? AND o.order_date < ? " + BEST_SELLERS_END,
                        List.of(MONTH_START, MONTH_END, 10)),
                new BenchmarkQuery("Report: revenue by category (one month)",
                        REVENUE_BY_CATEGORY_MONTH, List.of(MONTH_START, MONTH_END)),
                new BenchmarkQuery("Report: top customers (one month)",
                        TOP_CUSTOMERS_MONTH, List.of(MONTH_START, MONTH_END, 1, 10)),
                new BenchmarkQuery("Report: low stock", LOW_STOCK, List.of()),
                new BenchmarkQuery("Reviews written by one customer",
                        "SELECT r.review_id, r.product_id, r.rating FROM reviews r WHERE r.user_id = ?",
                        List.of(customerId))
        );
    }

    // ---------- measuring ----------

    private static Result measure(Connection con, BenchmarkQuery q) throws SQLException {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runQuery(con, q);           // fills PostgreSQL's cache so we measure steady-state speed
        }

        double[] times = new double[MEASURED_RUNS];
        int rows = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            rows = runQuery(con, q);
            times[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(times);

        Explain explain = explain(con, q);
        return new Result(q.name(), times[MEASURED_RUNS / 2], times[0], explain.executionMs(), rows, explain.plan());
    }

    /** Runs the query and reads every row, returning the row count. */
    private static int runQuery(Connection con, BenchmarkQuery q) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(q.sql())) {
            bind(ps, q.params());
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                return count;
            }
        }
    }

    record Explain(double executionMs, String plan) {}

    /** Runs EXPLAIN ANALYZE and pulls out the execution time and the table/index scans used. */
    private static Explain explain(Connection con, BenchmarkQuery q) throws SQLException {
        Set<String> scans = new LinkedHashSet<>();
        double executionMs = Double.NaN;

        try (PreparedStatement ps = con.prepareStatement("EXPLAIN (ANALYZE) " + q.sql())) {
            bind(ps, q.params());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String line = rs.getString(1).trim();
                    if (line.startsWith("Execution Time:")) {
                        executionMs = Double.parseDouble(line.replaceAll("[^0-9.]", ""));
                    }
                    String node = line.startsWith("->") ? line.substring(2).trim() : line;
                    if (node.matches("^(Parallel )?(Seq|Index|Index Only|Bitmap Heap|Bitmap Index) Scan.*")) {
                        int cut = node.indexOf("  (");
                        scans.add(cut > 0 ? node.substring(0, cut) : node);
                    }
                }
            }
        }
        return new Explain(executionMs, String.join("; ", scans));
    }

    /** Compares a product search served by the database with the same search served from ProductService's cache. */
    private static List<Result> cacheBenchmark() throws SQLException {
        ProductService service = new ProductService();
        double[] miss = new double[MEASURED_RUNS];
        double[] hit = new double[MEASURED_RUNS];
        int rows = 0;

        for (int i = 0; i < MEASURED_RUNS; i++) {
            service.invalidateCache();                      // force a database round trip
            long start = System.nanoTime();
            ProductPage page = service.search("phone", null, 1);
            miss[i] = (System.nanoTime() - start) / 1_000_000.0;
            rows = page.products().size();

            start = System.nanoTime();
            service.search("Phone", null, 1);               // different case, same cache entry
            hit[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(miss);
        Arrays.sort(hit);

        return List.of(
                new Result("ProductService search 'phone': cache miss", miss[MEASURED_RUNS / 2], miss[0],
                        Double.NaN, rows, "new connection + COUNT query + page query"),
                new Result("ProductService search 'Phone': cache hit", hit[MEASURED_RUNS / 2], hit[0],
                        Double.NaN, rows, "in-memory HashMap lookup, no database"));
    }

    // ---------- output ----------

    private static void printDataSize(Connection con) throws SQLException {
        System.out.println("Data size:");
        for (String table : List.of("products", "orders", "order_items", "users", "reviews")) {
            System.out.printf("  %-12s %,d rows%n", table, singleInt(con, "SELECT COUNT(*) FROM " + table));
        }
        System.out.println();
    }

    private static void printResults(String label, List<Result> results) {
        System.out.println("\n=== Benchmark: " + label + " (median of " + MEASURED_RUNS + " runs) ===");
        System.out.printf("%-48s %11s %11s %6s  %s%n", "Query", "Client ms", "Server ms", "Rows", "Plan (scans used)");
        for (Result r : results) {
            System.out.printf("%-48s %11.2f %11s %6d  %s%n",
                    r.name(), r.medianMs(),
                    Double.isNaN(r.serverMs()) ? "-" : String.format("%.2f", r.serverMs()),
                    r.rows(), r.plan());
        }
    }

    private static Path saveCsv(String label, List<Result> results) throws IOException {
        Files.createDirectories(RESULTS_DIR);
        Path file = RESULTS_DIR.resolve(label + ".csv");
        List<String> lines = new ArrayList<>();
        lines.add("query,median_ms,min_ms,server_ms,rows,plan");
        for (Result r : results) {
            lines.add(String.join(",",
                    csv(r.name()),
                    String.format("%.3f", r.medianMs()),
                    String.format("%.3f", r.minMs()),
                    Double.isNaN(r.serverMs()) ? "" : String.format("%.3f", r.serverMs()),
                    String.valueOf(r.rows()),
                    csv(r.plan())));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    /** Reads before.csv and after.csv and writes a Markdown comparison table for the README. */
    private static void compare() throws IOException {
        Map<String, String[]> before = readCsv(RESULTS_DIR.resolve("before.csv"));
        Map<String, String[]> after = readCsv(RESULTS_DIR.resolve("after.csv"));

        StringBuilder md = new StringBuilder();
        md.append("| Query | Before (ms) | After (ms) | Speed-up | Plan before | Plan after |\n");
        md.append("|---|---:|---:|---:|---|---|\n");

        for (Map.Entry<String, String[]> entry : before.entrySet()) {
            String[] b = entry.getValue();
            String[] a = after.get(entry.getKey());
            if (a == null) {
                continue;
            }
            // Prefer PostgreSQL's server time; fall back to client time (cache rows have no server time)
            double beforeMs = b[3].isEmpty() ? Double.parseDouble(b[1]) : Double.parseDouble(b[3]);
            double afterMs = a[3].isEmpty() ? Double.parseDouble(a[1]) : Double.parseDouble(a[3]);
            String speedup = afterMs > 0 ? String.format("%.1fx", beforeMs / afterMs) : "-";

            md.append(String.format("| %s | %.2f | %.2f | %s | %s | %s |%n",
                    entry.getKey(), beforeMs, afterMs, speedup, b[5], a[5]));
        }

        Path out = RESULTS_DIR.resolve("comparison.md");
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("Saved " + out.toAbsolutePath());
    }

    // ---------- small helpers ----------

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static int singleInt(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("No result for: " + sql + " (did you run bulk_data.sql?)");
            }
            return rs.getInt(1);
        }
    }

    private static String csv(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** Minimal CSV reader for the files this class writes (handles quoted fields). */
    private static Map<String, String[]> readCsv(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalStateException("Missing " + file + ". Run the benchmark with 'before' and 'after' first.");
        }
        Map<String, String[]> rows = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            List<String> fields = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else if (ch == '"') {
                        quoted = false;
                    } else {
                        current.append(ch);
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            }
            fields.add(current.toString());
            rows.put(fields.get(0), fields.toArray(new String[0]));
        }
        return rows;
    }
}
