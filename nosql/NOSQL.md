# NoSQL design: reviews and activity logs (MongoDB)

The core of the store (users, products, inventory, orders) stays in PostgreSQL: it needs
transactions, foreign keys and joins. Two kinds of data fit a document database better:
**product reviews** and **activity logs**. This folder defines both in MongoDB.

## Running it

Requires Docker.

```bash
cd nosql
docker compose up -d                                                         # start MongoDB; creates the collections on first start
docker exec -it smart-ecommerce-mongo mongosh smart_ecommerce /scripts/queries.js   # run the example queries
docker exec -it smart-ecommerce-mongo mongosh smart_ecommerce               # interactive shell
```

| Task | Command |
|---|---|
| Re-run the setup script | `docker exec -it smart-ecommerce-mongo mongosh /docker-entrypoint-initdb.d/01-init.js` |
| Stop (keep data) | `docker compose down` |
| Reset everything | `docker compose down -v`, then `docker compose up -d` |

MongoDB listens on `localhost:27017` without authentication; fine for local development only.
GUI tools such as MongoDB Compass can connect to `mongodb://localhost:27017`.

| File | Purpose |
|---|---|
| `docker-compose.yml` | MongoDB 7 container with a persistent volume |
| `init/01-init.js` | Creates the collections, validation rules, indexes, sample documents and the view |
| `scripts/queries.js` | Example queries, an aggregation, a query plan, and a validation failure |

## Collections

### `product_reviews`

```json
{
  "_id": ObjectId("..."),
  "productId": 1,
  "productName": "Smartphone X10",
  "user": { "userId": 2, "displayName": "Alice U." },
  "rating": 5,
  "title": "Battery lasts two days",
  "body": "Great battery life and a bright screen...",
  "pros": ["battery life", "screen"],
  "cons": ["no headphone jack"],
  "tags": ["battery", "display"],
  "images": [{ "url": "https://example.com/reviews/x10-front.jpg", "caption": "Front view" }],
  "verifiedPurchase": true,
  "helpfulVotes": 14,
  "merchantReply": { "body": "Thanks Alice!", "repliedAt": ISODate("2026-09-05T09:00:00Z") },
  "createdAt": ISODate("2026-09-03T18:20:00Z")
}
```

Design choices:

- **Embedded reviewer and merchant reply.** A review is always displayed together with them,
  so one read returns everything and no join is needed.
- **Snapshots (`productName`, `displayName`).** Deliberate denormalization: the review shows
  the name as it was when written, and listing reviews never has to query PostgreSQL.
- **Optional, variable fields.** `title`, `pros`, `cons`, `images` and `merchantReply`
  appear only when present; there are no empty columns.
- **Validation still applies.** A `$jsonSchema` validator enforces required fields, types and
  `rating` between 1 and 5, so flexible doesn't mean unchecked.

Indexes:

| Index | Supports |
|---|---|
| `{ productId: 1, createdAt: -1 }` | A product's reviews, newest first |
| `{ productId: 1, "user.userId": 1 }` unique | One review per user per product (same rule as the SQL `UNIQUE` constraint) |
| `{ "user.userId": 1, createdAt: -1 }` | A customer's own reviews |
| Text index on `title` (weight 3) and `body` | Searching review text |

### `activity_logs`

One document per event. The `details` object has a **different shape for each event type**:

```json
{ "eventType": "SEARCH", "timestamp": ISODate("..."), "userId": 2, "sessionId": "s-1001",
  "details": { "query": "phone", "categoryId": null, "resultsCount": 8001, "page": 1, "durationMs": 5 },
  "client": { "app": "smart-ecommerce-desktop", "version": "1.0", "os": "macOS" } }

{ "eventType": "ORDER_PLACED", "timestamp": ISODate("..."), "userId": 2, "sessionId": "s-1001",
  "details": { "orderId": 3, "itemCount": 2, "total": NumberDecimal("67.00"), "paymentMethod": "card" } }

{ "eventType": "ERROR", "timestamp": ISODate("..."), "userId": 3, "sessionId": "s-1006",
  "details": { "operation": "placeOrder", "message": "Not enough stock...", "sqlState": null } }
```

Indexes:

| Index | Supports |
|---|---|
| `{ timestamp: 1 }` with **TTL of 90 days** | MongoDB deletes old logs automatically |
| `{ userId: 1, timestamp: -1 }` | One user's recent activity |
| `{ eventType: 1, timestamp: -1 }` | Analytics per event type over a time window |

### `product_rating_summary` (view)

An aggregation over `product_reviews` giving each product's review count, average rating and
star breakdown. Being a view, it is recalculated on every read and can never be out of date.

## Why NoSQL for this data

| | Reviews | Activity logs |
|---|---|---|
| Structure | Semi-structured: optional photos, pros/cons, replies | Varies completely by event type |
| Volume | Grows steadily | Very high write volume |
| Relationships | Read as a self-contained unit | Written once, never updated, no joins |
| Consistency needs | Doesn't need to be in the same transaction as an order | Losing or delaying a log entry is acceptable |
| Lifetime | Kept | Expire after 90 days (TTL index) |

## Relational vs document comparison

| Aspect | PostgreSQL `reviews` table | MongoDB `product_reviews` |
|---|---|---|
| Schema | Fixed columns; adding photos or replies means new tables or columns and a migration | Flexible; new optional fields need no migration, validator still checks types |
| Photos, pros/cons, reply | Separate tables (`review_images`, `review_replies`...) joined on read | Embedded arrays/objects, one read |
| Integrity | Foreign keys to `products` and `users` enforced by the database | References only; the application must keep `productId` / `userId` valid |
| Uniqueness | `UNIQUE (product_id, user_id)` | Unique compound index, same effect |
| Text search | Needs `pg_trgm` or full-text setup | Built-in text index with relevance scores |
| Aggregation | SQL `GROUP BY` | Aggregation pipeline / view |
| Transactions with orders | Same database, so one ACID transaction | Separate system, so no cross-database transaction |
| Scaling | Primarily vertical | Designed to shard horizontally for large volumes |

For logs the difference is larger still: a relational design would need either one wide table full
of nullable columns, a generic JSON column, or a table per event type.

## Trade-offs and what stays relational

- **No cross-database foreign keys.** If a product is deleted in PostgreSQL, its reviews in MongoDB
  remain unless the application removes them. The app must validate `productId` and `userId`.
- **Denormalized snapshots can go stale.** If a product is renamed, old reviews keep the old name;
  that's intentional here, but would need an update job if the current name were required.
- **Orders, inventory and payments stay in PostgreSQL.** Placing an order must reduce stock in the
  same ACID transaction; splitting that across two databases would risk overselling.

The result is a **polyglot persistence** design: each type of data lives in the store that suits it.
