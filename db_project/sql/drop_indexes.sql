-- Removes the performance indexes so the "before" benchmark can be re-run.
-- Primary keys and unique constraints from schema.sql are not affected.
DROP INDEX IF EXISTS idx_products_name_trgm;
DROP INDEX IF EXISTS idx_products_name_id;
DROP INDEX IF EXISTS idx_products_category_name;
DROP INDEX IF EXISTS idx_orders_user_id;
DROP INDEX IF EXISTS idx_orders_order_date;
DROP INDEX IF EXISTS idx_order_items_product_id;
DROP INDEX IF EXISTS idx_reviews_user_id;
DROP INDEX IF EXISTS idx_inventory_low_stock;
ANALYZE;
