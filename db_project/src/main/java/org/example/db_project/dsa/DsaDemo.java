package org.example.db_project.dsa;

import org.example.db_project.dao.ProductDAO;
import org.example.db_project.model.Product;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs the algorithms in Algorithms.java on the real product data and prints the results.
 * Run it with the bulk data loaded (03_bulk_data.sql) to see clear differences.
 */
public class DsaDemo {

    private static final int LOOKUPS = 1000;

    public static void main(String[] args) throws Exception {
        // Load every product (the DAO returns them sorted by name, so they are NOT sorted by price or id)
        List<Product> products = new ProductDAO().search("", null, 1_000_000, 0);
        System.out.printf("Loaded %,d products%n%n", products.size());

        Comparator<Product> byPrice = Comparator.comparing(Product::getPrice);
        Comparator<Product> byId = Comparator.comparingInt(Product::getProductId);

        // ================= 1. SORTING =================
        // Warm-up runs, so Java's JIT compiler has optimised the code before we time it
        Algorithms.mergeSort(products, byPrice);
        new ArrayList<>(products).sort(byPrice);

        Algorithms.comparisons = 0;
        long start = System.nanoTime();
        List<Product> mergeSorted = Algorithms.mergeSort(products, byPrice);
        double mergeMs = millisSince(start);
        long mergeComparisons = Algorithms.comparisons;

        List<Product> javaSorted = new ArrayList<>(products);
        start = System.nanoTime();
        javaSorted.sort(byPrice);
        double javaMs = millisSince(start);

        System.out.println("1. SORTING all products by price");
        System.out.printf("   %-28s %10.2f ms   %,12d comparisons%n", "Merge sort (own)", mergeMs, mergeComparisons);
        System.out.printf("   %-28s %10.2f ms%n", "Java List.sort (built-in)", javaMs);
        System.out.println("   Same result: " + sameOrder(mergeSorted, javaSorted));
        System.out.println();

        // ================= 2. SEARCHING / HASHING =================
        // Three ways to find a product by its id
        List<Product> sortedById = Algorithms.mergeSort(products, byId);   // binary search needs sorted data
        Map<Integer, Product> idIndex = new HashMap<>();                     // hashing: id -> product
        for (Product p : products) {
            idIndex.put(p.getProductId(), p);
        }

        // Pick random products to look up (fixed seed, so every run is the same)
        Random random = new Random(42);
        List<Product> keys = new ArrayList<>();
        for (int i = 0; i < LOOKUPS; i++) {
            Product key = new Product();
            key.setProductId(products.get(random.nextInt(products.size())).getProductId());
            keys.add(key);
        }

        // Warm-up
        for (Product key : keys) {
            Algorithms.linearSearch(products, key, byId);
            Algorithms.binarySearch(sortedById, key, byId);
            idIndex.get(key.getProductId());
        }

        Algorithms.comparisons = 0;
        start = System.nanoTime();
        for (Product key : keys) {
            Algorithms.linearSearch(products, key, byId);
        }
        double linearMicros = millisSince(start) * 1000 / LOOKUPS;
        double linearComparisons = (double) Algorithms.comparisons / LOOKUPS;

        Algorithms.comparisons = 0;
        start = System.nanoTime();
        for (Product key : keys) {
            Algorithms.binarySearch(sortedById, key, byId);
        }
        double binaryMicros = millisSince(start) * 1000 / LOOKUPS;
        double binaryComparisons = (double) Algorithms.comparisons / LOOKUPS;

        start = System.nanoTime();
        for (Product key : keys) {
            idIndex.get(key.getProductId());
        }
        double hashMicros = millisSince(start) * 1000 / LOOKUPS;

        System.out.println("2. FINDING one product by id (average of " + LOOKUPS + " lookups)");
        System.out.printf("   %-28s %-10s %10.3f us   %,12.1f comparisons%n", "Linear search", "O(n)", linearMicros, linearComparisons);
        System.out.printf("   %-28s %-10s %10.3f us   %,12.1f comparisons%n", "Binary search (sorted list)", "O(log n)", binaryMicros, binaryComparisons);
        System.out.printf("   %-28s %-10s %10.3f us%n", "HashMap lookup (hashing)", "O(1)", hashMicros);
    }

    private static boolean sameOrder(List<Product> a, List<Product> b) {
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getProductId() != b.get(i).getProductId()) {
                return false;
            }
        }
        return a.size() == b.size();
    }

    private static double millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }
}
