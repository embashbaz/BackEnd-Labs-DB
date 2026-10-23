-- =====================================================================
-- Bulk test data for the performance benchmark.
-- Run AFTER 01_schema.sql and 02_seed.sql, and BEFORE 04_indexes.sql.
-- Assumes a freshly created schema (product ids are contiguous from 1).
--
-- Adds roughly: 20 categories, 5,000 customers, 100,000 products,
-- 50,000 orders, 100,000 order items and 30,000 reviews.
-- Takes well under a minute on a typical laptop.
-- =====================================================================

SELECT setseed(0.42);   -- makes random() repeatable, so "before" and "after" use identical data

-- ---------- categories ----------
INSERT INTO categories (name, description)
SELECT 'Bulk Category ' || lpad(g::text, 2, '0'), 'Generated for performance testing'
FROM generate_series(1, 20) AS g;

-- ---------- customers ----------
INSERT INTO users (full_name, email, password_hash, role)
SELECT 'Customer ' || g, 'customer' || g || '@bulk.test', '$2a$10$placeholder.bulk.hash', 'CUSTOMER'
FROM generate_series(1, 5000) AS g;

-- ---------- products ----------
-- Names like "Wireless Phone 4242": varied enough for realistic searches.
WITH words AS (
    SELECT ARRAY['Smart','Wireless','Portable','Classic','Premium','Compact','Ultra','Eco','Deluxe','Mini',
                 'Pro','Vintage','Modern','Rugged','Slim','Digital','Organic','Heavy-Duty','Foldable','Rechargeable'] AS adj,
           ARRAY['Phone','Headphones','Speaker','Lamp','Backpack','Watch','Kettle','Blender','Notebook','Jacket',
                 'Sneakers','Camera','Keyboard','Mouse','Charger','Tablet','Mug','Pillow','Toaster','Router',
                 'Monitor','Drone','Bottle','Tent','Chair'] AS noun
),
cats AS (
    SELECT array_agg(category_id ORDER BY category_id) AS ids FROM categories
)
INSERT INTO products (category_id, name, description, price)
SELECT cats.ids[1 + floor(random() * array_length(cats.ids, 1))::int],
       words.adj[1 + g % 20] || ' ' || words.noun[1 + (g / 20) % 25] || ' ' || g,
       'Generated product ' || g,
       round((5 + random() * 995)::numeric, 2)
FROM generate_series(1, 100000) AS g, words, cats;

-- ---------- inventory for every product that doesn't have a row yet ----------
INSERT INTO inventory (product_id, quantity_on_hand, reorder_level)
SELECT p.product_id, floor(random() * 200)::int, 5 + floor(random() * 15)::int
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.product_id);

-- ---------- orders, spread over two years ----------
INSERT INTO orders (user_id, order_date, status)
SELECT u.ids[1 + floor(random() * array_length(u.ids, 1))::int],
       TIMESTAMP '2024-10-01' + random() * INTERVAL '730 days',
       (ARRAY['DELIVERED','DELIVERED','DELIVERED','SHIPPED','PAID','PENDING','CANCELLED'])[1 + floor(random() * 7)::int]
FROM generate_series(1, 50000) AS g,
     (SELECT array_agg(user_id) AS ids FROM users WHERE email LIKE '%@bulk.test') AS u;

-- ---------- order items: 1 to 3 different products per bulk order ----------
INSERT INTO order_items (order_id, product_id, quantity, unit_price)
SELECT x.order_id, p.product_id, 1 + floor(random() * 3)::int, p.price
FROM (
    SELECT o.order_id,
           1 + ((o.order_id::bigint * 7919 + k * 104729) % m.max_id) AS product_id
    FROM orders o
    JOIN users u ON u.user_id = o.user_id AND u.email LIKE '%@bulk.test'
    CROSS JOIN (SELECT max(product_id) AS max_id FROM products) AS m
    CROSS JOIN generate_series(1, 3) AS k
    WHERE k <= 1 + (o.order_id % 3)
) AS x
JOIN products p ON p.product_id = x.product_id
ON CONFLICT DO NOTHING;

-- ---------- reviews ----------
INSERT INTO reviews (product_id, user_id, rating, comment)
SELECT 1 + floor(random() * m.max_id)::int,
       u.ids[1 + floor(random() * array_length(u.ids, 1))::int],
       1 + floor(random() * 5)::int,
       'Generated review'
FROM generate_series(1, 30000) AS g,
     (SELECT max(product_id) AS max_id FROM products) AS m,
     (SELECT array_agg(user_id) AS ids FROM users WHERE email LIKE '%@bulk.test') AS u
ON CONFLICT DO NOTHING;

-- Refresh the planner's statistics so it knows how big the tables are now
ANALYZE;

SELECT 'products' AS table_name, COUNT(*) AS row_count FROM products
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items
UNION ALL SELECT 'reviews', COUNT(*) FROM reviews;
