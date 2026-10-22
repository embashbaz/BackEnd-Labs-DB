-- =====================================================================
-- Smart E-Commerce: full schema (PostgreSQL), normalized to 3NF
-- WARNING: drops and recreates every table. Development use only.
-- Run:  psql -U postgres -d smart_ecommerce -f sql/schema.sql
--       psql -U postgres -d smart_ecommerce -f sql/seed.sql
--
-- Only primary keys and unique constraints are indexed here on purpose.
-- Extra indexes (foreign keys, search columns) are added in a later
-- script AFTER taking "before" timings for the performance report.
-- =====================================================================

DROP VIEW  IF EXISTS order_totals;
DROP TABLE IF EXISTS reviews, order_items, orders, inventory, products, categories, users CASCADE;

-- ---------- users ----------
CREATE TABLE users (
                       user_id       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       full_name     VARCHAR(100) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,          -- never store plain passwords
                       role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'
                           CHECK (role IN ('CUSTOMER', 'ADMIN')),
                       created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

-- ---------- categories ----------
CREATE TABLE categories (
                            category_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            name         VARCHAR(100) NOT NULL,
                            description  TEXT,
                            created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_categories_name_lower ON categories (LOWER(name));

-- ---------- products ----------
CREATE TABLE products (
                          product_id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          category_id  INT NOT NULL REFERENCES categories (category_id) ON DELETE RESTRICT,
                          name         VARCHAR(150)  NOT NULL,
                          description  TEXT,
                          price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
                          created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- inventory (1:1 with products) ----------
-- Kept separate because stock changes constantly while product details rarely do.
CREATE TABLE inventory (
                           product_id        INT PRIMARY KEY REFERENCES products (product_id) ON DELETE CASCADE,
                           quantity_on_hand  INT NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
                           reorder_level     INT NOT NULL DEFAULT 5 CHECK (reorder_level >= 0),
                           updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- orders ----------
-- No stored total: it is derived from order_items (see the order_totals view),
-- and storing it would create a transitive dependency that breaks 3NF.
CREATE TABLE orders (
                        order_id    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        user_id     INT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
                        order_date  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

-- ---------- order_items (resolves the many-to-many between orders and products) ----------
-- unit_price is the price AT THE TIME OF PURCHASE, a historical fact, not a copy
-- of products.price (which can change later). So it is not redundant.
CREATE TABLE order_items (
                             order_id    INT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
                             product_id  INT NOT NULL REFERENCES products (product_id) ON DELETE RESTRICT,
                             quantity    INT NOT NULL CHECK (quantity > 0),
                             unit_price  NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),
                             PRIMARY KEY (order_id, product_id)
);

-- ---------- reviews ----------
CREATE TABLE reviews (
                         review_id   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         product_id  INT NOT NULL REFERENCES products (product_id) ON DELETE CASCADE,
                         user_id     INT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
                         rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                         comment     TEXT,
                         created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         UNIQUE (product_id, user_id)              -- one review per user per product
);

-- ---------- derived order totals ----------
CREATE VIEW order_totals AS
SELECT o.order_id,
       o.user_id,
       o.order_date,
       o.status,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_amount
FROM orders o
         LEFT JOIN order_items oi ON oi.order_id = o.order_id
GROUP BY o.order_id;