-- Sample data. Run right after schema.sql (it relies on fresh identity values).
-- Password hashes are placeholders, not real passwords.

INSERT INTO users (full_name, email, password_hash, role) VALUES
    ('Store Admin',   'admin@shop.test',   '$2a$10$placeholder.admin.hash.000000000000000000000', 'ADMIN'),
    ('Alice Uwase',   'alice@example.com', '$2a$10$placeholder.alice.hash.000000000000000000000', 'CUSTOMER'),
    ('Brian Mugisha', 'brian@example.com', '$2a$10$placeholder.brian.hash.000000000000000000000', 'CUSTOMER');

INSERT INTO categories (name, description) VALUES
    ('Electronics',    'Phones, laptops and accessories'),
    ('Books',          'Printed and digital books'),
    ('Clothing',       'Men''s and women''s apparel'),
    ('Home & Kitchen', 'Cookware, appliances and decor');

INSERT INTO products (category_id, name, description, price)
SELECT c.category_id, v.name, v.description, v.price
FROM (VALUES
    ('Electronics',    'Smartphone X10',          '6.5" screen, 128 GB',          349.99),
    ('Electronics',    'Wireless Earbuds',        'Noise cancelling, 24h battery', 59.90),
    ('Electronics',    'Laptop Pro 14',           '16 GB RAM, 512 GB SSD',         999.00),
    ('Electronics',    'USB-C Charger 65W',       'Fast charging',                 24.50),
    ('Books',          'Clean Code',              'Robert C. Martin',              32.00),
    ('Books',          'Database System Concepts','Silberschatz et al.',           75.00),
    ('Books',          'Things Fall Apart',       'Chinua Achebe',                 12.99),
    ('Clothing',       'Cotton T-Shirt',          '100% cotton, unisex',           15.00),
    ('Clothing',       'Rain Jacket',             'Waterproof, packable',          69.00),
    ('Home & Kitchen', 'Non-stick Frying Pan',    '28 cm',                         29.99),
    ('Home & Kitchen', 'Electric Kettle',         '1.7 L, auto shut-off',          22.00),
    ('Home & Kitchen', 'Coffee Grinder',          'Burr grinder, 15 settings',     45.00)
) AS v(category, name, description, price)
JOIN categories c ON c.name = v.category;

INSERT INTO inventory (product_id, quantity_on_hand, reorder_level)
SELECT p.product_id, v.qty, v.reorder
FROM (VALUES
    ('Smartphone X10', 25, 5),  ('Wireless Earbuds', 80, 10), ('Laptop Pro 14', 8, 3),
    ('USB-C Charger 65W', 120, 20), ('Clean Code', 40, 5), ('Database System Concepts', 15, 5),
    ('Things Fall Apart', 60, 10), ('Cotton T-Shirt', 200, 30), ('Rain Jacket', 4, 5),
    ('Non-stick Frying Pan', 35, 5), ('Electric Kettle', 0, 5), ('Coffee Grinder', 12, 3)
) AS v(name, qty, reorder)
JOIN products p ON p.name = v.name;

-- Orders: user 2 = Alice, user 3 = Brian
INSERT INTO orders (user_id, order_date, status) VALUES
    (2, '2026-09-01 10:15', 'DELIVERED'),
    (3, '2026-09-20 14:40', 'SHIPPED'),
    (2, '2026-10-15 09:05', 'PENDING');

INSERT INTO order_items (order_id, product_id, quantity, unit_price)
SELECT v.order_id, p.product_id, v.qty, p.price
FROM (VALUES
    (1, 'Smartphone X10', 1), (1, 'USB-C Charger 65W', 2),
    (2, 'Clean Code', 1),     (2, 'Database System Concepts', 1),
    (3, 'Electric Kettle', 1),(3, 'Cotton T-Shirt', 3)
) AS v(order_id, name, qty)
JOIN products p ON p.name = v.name;

INSERT INTO reviews (product_id, user_id, rating, comment)
SELECT p.product_id, v.user_id, v.rating, v.comment
FROM (VALUES
    ('Smartphone X10',    2, 5, 'Great battery life.'),
    ('USB-C Charger 65W', 2, 4, 'Charges fast, gets a bit warm.'),
    ('Clean Code',        3, 5, 'Every developer should read it.')
) AS v(name, user_id, rating, comment)
JOIN products p ON p.name = v.name;
