package org.example.db_project.dsa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hand-written sorting and searching algorithms.
 * The "comparisons" counter records how much work each one does,
 * which shows the growth rate independently of how fast the computer is.
 */
public final class Algorithms {

    /** Number of comparisons made so far. Set it to 0 before measuring. */
    public static long comparisons = 0;

    private Algorithms() {}

    /**
     * Merge sort: O(n log n).
     * Splits the list in half, sorts each half, then merges the two sorted halves.
     * Returns a new sorted list; the input list is not changed.
     */
    public static <T> List<T> mergeSort(List<T> list, Comparator<T> cmp) {
        if (list.size() <= 1) {
            return new ArrayList<>(list);
        }
        int mid = list.size() / 2;
        List<T> left = mergeSort(list.subList(0, mid), cmp);
        List<T> right = mergeSort(list.subList(mid, list.size()), cmp);
        return merge(left, right, cmp);
    }

    /** Combines two sorted lists into one sorted list. */
    private static <T> List<T> merge(List<T> left, List<T> right, Comparator<T> cmp) {
        List<T> result = new ArrayList<>(left.size() + right.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            comparisons++;
            if (cmp.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        while (i < left.size()) {
            result.add(left.get(i++));
        }
        while (j < right.size()) {
            result.add(right.get(j++));
        }
        return result;
    }

    /**
     * Binary search: O(log n).
     * The list MUST be sorted with the same comparator.
     * Looks at the middle element and throws away the half that can't contain the key.
     * Returns the index of the key, or -1 if it isn't there.
     */
    public static <T> int binarySearch(List<T> sorted, T key, Comparator<T> cmp) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            comparisons++;
            int result = cmp.compare(sorted.get(mid), key);
            if (result == 0) {
                return mid;
            } else if (result < 0) {
                low = mid + 1;      // key is in the right half
            } else {
                high = mid - 1;     // key is in the left half
            }
        }
        return -1;
    }

    /**
     * Linear search: O(n).
     * Checks every element in turn. Works on unsorted lists.
     * Returns the index of the key, or -1 if it isn't there.
     */
    public static <T> int linearSearch(List<T> list, T key, Comparator<T> cmp) {
        for (int i = 0; i < list.size(); i++) {
            comparisons++;
            if (cmp.compare(list.get(i), key) == 0) {
                return i;
            }
        }
        return -1;
    }
}
