package org.example.db_project.model;

import java.util.List;

/** One page of search results plus what the UI needs to show "Page x of y". */
public record ProductPage(List<Product> products, int totalCount, int page, int pageSize) {

    public int totalPages() {
        return Math.max(1, (totalCount + pageSize - 1) / pageSize);
    }
}
