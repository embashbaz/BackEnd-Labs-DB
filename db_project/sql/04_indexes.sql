-- =====================================================================
-- Performance indexes. Run AFTER recording the "before" benchmark.
-- Each index is tied to a specific query in the app.
-- =====================================================================

-- pg_trgm splits text into 3-letter chunks ("pho", "hon", "one"...), which lets an
-- index answer ILIKE '%phone%'. A normal B-tree can't, because of the leading %.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- products ----------
-- Product search: name ILIKE '%keyword%'  (ProductDAO.search / count)
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING GIN (name gin_trgm_ops);

-- Browsing without a filter: ORDER BY name, product_id LIMIT 10.
-- The index is already in that order, so PostgreSQL reads just the first 10 entries
-- instead of sorting all 100,000 rows.
CREATE INDEX IF NOT EXISTS idx_products_name_id
    ON products (name, product_id);

-- Browsing one category: WHERE category_id = ? ORDER BY name, product_id.
-- Composite index: filter column first, then the sort columns. Because category_id
-- is the leftmost column, this also serves the foreign key on category_id.
CREATE INDEX IF NOT EXISTS idx_products_category_name
    ON products (category_id, name, product_id);

-- ---------- orders ----------
-- Foreign key + a customer's order history (WHERE user_id = ?)
CREATE INDEX IF NOT EXISTS idx_orders_user_id
    ON orders (user_id);

-- Date-range filters in the reports (order_date >= ? AND order_date < ?)
CREATE INDEX IF NOT EXISTS idx_orders_order_date
    ON orders (order_date);

-- ---------- order_items ----------
-- The primary key (order_id, product_id) already covers lookups by order_id.
-- This one covers the foreign key to products and joins starting from a product.
CREATE INDEX IF NOT EXISTS idx_order_items_product_id
    ON order_items (product_id);

-- ---------- reviews ----------
-- UNIQUE (product_id, user_id) already covers lookups by product_id.
-- This covers the foreign key to users ("reviews written by a customer").
CREATE INDEX IF NOT EXISTS idx_reviews_user_id
    ON reviews (user_id);

-- ---------- inventory ----------
-- Partial index: contains ONLY rows that are low on stock (about 6% of the table),
-- so it is small and matches the low-stock report's WHERE clause exactly.
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock
    ON inventory (quantity_on_hand)
    WHERE quantity_on_hand <= reorder_level;

-- Deliberately NOT indexed:
--   orders.status    only 5 distinct values, so an index rarely beats a table scan
--   products.price   no query in the app filters or sorts by price in SQL

ANALYZE;
