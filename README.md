# Smart E-Commerce System

A JavaFX desktop application backed by PostgreSQL that manages categories, products,
inventory, orders and reports for an e-commerce store. Built with a layered architecture
(Controller → Service → DAO), parameterized JDBC queries, in-memory caching and
measured index optimization.

## Tech stack

| Part | Technology |
|---|---|
| Language | Java 17+ (Java Platform Module System) |
| UI | JavaFX 21 with FXML |
| Database | PostgreSQL 13+ (uses the `pg_trgm` extension) |
| Data access | JDBC with `PreparedStatement` (PostgreSQL driver 42.7.4) |
| Build | Gradle with the `org.openjfx.javafxplugin` |

## Setup

### 1. Create the database

```bash
psql -U postgres -c "CREATE DATABASE smart_ecommerce"
psql -U postgres -d smart_ecommerce -f sql/schema.sql
psql -U postgres -d smart_ecommerce -f sql/seed.sql
```

`schema.sql` drops and recreates all tables, so it can be re-run at any time to reset the database.

### 2. Set the database password

Either edit the defaults in `src/main/java/com/example/smartecommerce/util/DBConnection.java`,
or set environment variables:

| Variable | Default |
|---|---|
| `SMART_DB_URL` | `jdbc:postgresql://localhost:5432/smart_ecommerce` |
| `SMART_DB_USER` | `postgres` |
| `SMART_DB_PASSWORD` | `your_password_here` |

### 3. Run the application

Open the folder in IntelliJ IDEA as a Gradle project, then run the `application > run` Gradle task,
or from a terminal:

```bash
./gradlew run
```

## Project structure

```
smart-ecommerce/
├── build.gradle, settings.gradle
├── sql/
│   ├── schema.sql          all tables, constraints and the order_totals view (3NF)
│   ├── seed.sql            small demo data set
│   ├── bulk_data.sql       ~100k products / 50k orders for performance testing
│   ├── indexes.sql         performance indexes (each one documented)
│   └── drop_indexes.sql    removes them again
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/example/smartecommerce/
    │       ├── SmartECommerceApp.java   entry point
    │       ├── controller/   FXML controllers (one per tab + MainController)
    │       ├── service/      validation, business rules, caching
    │       ├── dao/          all SQL, parameterized queries, transactions
    │       ├── model/        plain data classes, enums and records
    │       ├── exception/    InsufficientStockException
    │       ├── util/         DBConnection
    │       └── benchmark/    PerformanceBenchmark
    └── resources/com/example/smartecommerce/
        └── main-, category-, product-, order-, report-view.fxml
```

## Architecture

```
FXML view  →  Controller  →  Service  →  DAO  →  PostgreSQL
```

- **Controllers** handle UI events only and never contain SQL.
- **Services** validate input, apply business rules (such as allowed order-status changes),
  translate database errors into readable messages, and manage caches.
- **DAOs** contain all SQL. Every value is passed through `?` placeholders.
  Multi-step writes (placing an order, saving a product with its stock) run in a transaction.

## Features

| Tab | What it does |
|---|---|
| Categories | Create, update and delete categories; case-insensitive duplicate check |
| Products | CRUD with stock, case-insensitive search (`ILIKE`), category filter, pagination |
| Orders | Create orders with stock check and decrement in one transaction; status workflow; cancelling restores stock |
| Reports | Best sellers, revenue by category, low stock, top customers; date filters; CSV export; query timing |

## Database design

Tables: `users`, `categories`, `products`, `inventory`, `orders`, `order_items`, `reviews`,
plus the `order_totals` view.

Key decisions:

- **No stored order total.** It is derived from `order_items`, so storing it would violate 3NF.
  The `order_totals` view calculates it.
- **`order_items.unit_price`** records the price at the time of purchase. It is a historical fact,
  not a copy of `products.price`, so it is not redundant.
- **`inventory` is a separate 1:1 table**, because stock changes constantly while product details rarely do.
- **`order_items` has a composite primary key `(order_id, product_id)`**, resolving the many-to-many
  relationship between orders and products.
- **Delete rules:** an ordered product cannot be deleted (`RESTRICT`), preserving order history;
  deleting a product removes its inventory row and reviews (`CASCADE`).
- **Case-insensitive uniqueness** on category names and user emails via unique indexes on `LOWER(...)`.
- **CHECK constraints** on prices, quantities, ratings, roles and order statuses.

## Caching

| Data | Cache | Invalidation |
|---|---|---|
| Categories | `LinkedHashMap<Integer, Category>`, shared by all tabs | Cleared after every insert, update or delete |
| Product search pages | `HashMap<String, ProductPage>`, key `keyword|category|page`, keyword lower-cased to match the case-insensitive search | Cleared after every product write, order or cancellation |
| Orders, reports | Not cached: they change on every purchase and must always be current | — |

## Performance testing

1. Reset and load bulk data:
   ```bash
   psql -U postgres -d smart_ecommerce -f sql/schema.sql
   psql -U postgres -d smart_ecommerce -f sql/seed.sql
   psql -U postgres -d smart_ecommerce -f sql/bulk_data.sql
   ```
2. Baseline: `./gradlew benchmark -Plabel=before`
3. Add indexes: `psql -U postgres -d smart_ecommerce -f sql/indexes.sql`
4. Measure again: `./gradlew benchmark -Plabel=after`
5. Compare: `./gradlew benchmark -Plabel=compare` (writes `benchmark-results/comparison.md`)

Reset with `schema.sql` + `seed.sql` afterwards for demos; the Orders tab loads every order at once.

### Results

| Query | Before (ms) | After (ms) | Speed-up | Plan before | Plan after |
|---|---:|---:|---:|---|---|
| Search 'phone' (common word), page 1 | 30.27 | 0.51 | 58.9x | Seq Scan on categories c; Seq Scan on products p; Index Scan using inventory_pkey on inventory i | Index Scan using idx_products_name_id on products p; Index Scan using categories_pkey on categories c; Index Scan using inventory_pkey on inventory i |
| Search '4242' (rare), page 1 | 13.63 | 0.07 | 200.5x | Seq Scan on categories c; Seq Scan on products p; Index Scan using inventory_pkey on inventory i | Bitmap Heap Scan on products p; Bitmap Index Scan on idx_products_name_trgm; Seq Scan on categories c; Index Scan using inventory_pkey on inventory i |
| Search count for 'phone' | 14.15 | 2.87 | 4.9x | Seq Scan on products p | Bitmap Heap Scan on products p; Bitmap Index Scan on idx_products_name_trgm |
| Browse all products, page 1 | 27.70 | 0.05 | 553.9x | Parallel Seq Scan on products p; Seq Scan on categories c; Seq Scan on inventory i | Index Scan using idx_products_name_id on products p; Index Scan using categories_pkey on categories c; Index Scan using inventory_pkey on inventory i |
| Browse all products, page 5000 (deep OFFSET) | 76.13 | 73.53 | 1.0x | Seq Scan on products p; Seq Scan on categories c; Seq Scan on inventory i | Seq Scan on products p; Seq Scan on categories c; Seq Scan on inventory i |
| Browse one category, page 1 | 4.46 | 0.02 | 223.2x | Parallel Seq Scan on products p; Seq Scan on categories c; Index Scan using inventory_pkey on inventory i | Index Scan using idx_products_category_name on products p; Seq Scan on categories c; Index Scan using inventory_pkey on inventory i |
| Order history of one customer | 1.81 | 0.01 | 201.2x | Seq Scan on orders o | Bitmap Heap Scan on orders o; Bitmap Index Scan on idx_orders_user_id |
| Count orders in one month | 1.86 | 0.25 | 7.3x | Seq Scan on orders o | Bitmap Heap Scan on orders o; Bitmap Index Scan on idx_orders_order_date |
| Report: best sellers (all time) | 105.47 | 119.11 | 0.9x | Seq Scan on order_items oi; Seq Scan on orders o; Seq Scan on products p; Seq Scan on categories c | Seq Scan on order_items oi; Seq Scan on orders o; Seq Scan on products p; Seq Scan on categories c |
| Report: best sellers (one month) | 14.48 | 13.25 | 1.1x | Seq Scan on order_items oi; Seq Scan on orders o; Index Scan using products_pkey on products p; Seq Scan on categories c | Seq Scan on order_items oi; Bitmap Heap Scan on orders o; Bitmap Index Scan on idx_orders_order_date; Index Scan using products_pkey on products p; Seq Scan on categories c |
| Report: revenue by category (one month) | 14.09 | 12.72 | 1.1x | Seq Scan on order_items oi; Seq Scan on orders o; Index Scan using products_pkey on products p; Seq Scan on categories c | Seq Scan on order_items oi; Bitmap Heap Scan on orders o; Bitmap Index Scan on idx_orders_order_date; Index Scan using products_pkey on products p; Seq Scan on categories c |
| Report: top customers (one month) | 12.18 | 9.77 | 1.2x | Seq Scan on order_items oi; Seq Scan on orders o; Seq Scan on users u | Seq Scan on order_items oi; Bitmap Heap Scan on orders o; Bitmap Index Scan on idx_orders_order_date; Seq Scan on users u |
| Report: low stock | 15.45 | 12.64 | 1.2x | Seq Scan on products p; Seq Scan on inventory i; Seq Scan on categories c | Seq Scan on products p; Bitmap Heap Scan on inventory i; Bitmap Index Scan on idx_inventory_low_stock; Seq Scan on categories c |
| Reviews written by one customer | 0.97 | 0.01 | 193.6x | Seq Scan on reviews r | Bitmap Heap Scan on reviews r; Bitmap Index Scan on idx_reviews_user_id |
| ProductService search 'phone': cache miss | 50.23 | 10.04 | 5.0x | new connection + COUNT query + page query | new connection + COUNT query + page query |
| ProductService search 'Phone': cache hit | 0.02 | 0.01 | 3.0x | in-memory HashMap lookup, no database | in-memory HashMap lookup, no database |


## Still to do

- [ ] DSA: own sorting and searching algorithms, benchmarked against `Collections.sort` / `Collections.binarySearch`
- [ ] NoSQL design for reviews/logs, with justification
- [ ] ERDs: conceptual, logical, physical
- [ ] Performance results in this README
