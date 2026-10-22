CREATE TABLE categories (
                            category_id INT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE products (
                          product_id INT AUTO_INCREMENT PRIMARY KEY,
                          category_id INT NOT NULL,
                          name VARCHAR(150) NOT NULL,
                          price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
                          FOREIGN KEY (category_id) REFERENCES categories(category_id)
);