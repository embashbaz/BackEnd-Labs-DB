CREATE TABLE IF NOT EXISTS categories (
                                          category_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                          name         VARCHAR(100) NOT NULL,
                                          description  TEXT,
                                          created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Case-insensitive uniqueness: "Electronics" and "electronics" count as duplicates
CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_name_lower
    ON categories (LOWER(name));

-- Sample data
INSERT INTO categories (name, description) VALUES
                                               ('Electronics', 'Phones, laptops and accessories'),
                                               ('Books',       'Printed and digital books'),
                                               ('Clothing',    'Men''s and women''s apparel')
ON CONFLICT DO NOTHING;
