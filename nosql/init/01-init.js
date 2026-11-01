// =====================================================================
// Smart E-Commerce: MongoDB setup for unstructured / semi-structured data
//
// Creates:
//   product_reviews         customer reviews (flexible content, nested replies, images)
//   activity_logs           app events (shape differs per event type, high write volume)
//   product_rating_summary  a read-only VIEW aggregating product_reviews
//
// Runs automatically on the container's first start. Safe to re-run by hand:
//   docker exec -it smart-ecommerce-mongo mongosh /docker-entrypoint-initdb.d/01-init.js
//
// productId and userId refer to products.product_id and users.user_id in PostgreSQL.
// MongoDB can't enforce foreign keys across databases, so the application must keep them valid.
// =====================================================================

db = db.getSiblingDB("smart_ecommerce");

// Start clean so the script can be re-run
db.product_rating_summary.drop();
db.product_reviews.drop();
db.activity_logs.drop();

// ---------------------------------------------------------------------
// 1. product_reviews
// ---------------------------------------------------------------------
// One document per review. Reviewer details and the merchant's reply are EMBEDDED,
// so displaying a review needs a single read and no joins.
db.createCollection("product_reviews", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["productId", "productName", "user", "rating", "body", "verifiedPurchase", "createdAt"],
      properties: {
        productId:   { bsonType: "int", minimum: 1, description: "products.product_id in PostgreSQL" },
        productName: { bsonType: "string", description: "snapshot of the name when the review was written" },
        user: {
          bsonType: "object",
          required: ["userId", "displayName"],
          properties: {
            userId:      { bsonType: "int", minimum: 1, description: "users.user_id in PostgreSQL" },
            displayName: { bsonType: "string" }
          }
        },
        rating:           { bsonType: "int", minimum: 1, maximum: 5 },
        title:            { bsonType: "string", maxLength: 120 },
        body:             { bsonType: "string", minLength: 1, maxLength: 5000 },
        pros:             { bsonType: "array", items: { bsonType: "string" } },
        cons:             { bsonType: "array", items: { bsonType: "string" } },
        tags:             { bsonType: "array", items: { bsonType: "string" } },
        images: {
          bsonType: "array",
          items: {
            bsonType: "object",
            required: ["url"],
            properties: { url: { bsonType: "string" }, caption: { bsonType: "string" } }
          }
        },
        verifiedPurchase: { bsonType: "bool" },
        helpfulVotes:     { bsonType: "int", minimum: 0 },
        merchantReply: {
          bsonType: "object",
          required: ["body", "repliedAt"],
          properties: { body: { bsonType: "string" }, repliedAt: { bsonType: "date" } }
        },
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" }
      }
    }
  },
  validationLevel: "strict",
  validationAction: "error"
});

// Show a product's reviews, newest first (the most common query)
db.product_reviews.createIndex({ productId: 1, createdAt: -1 }, { name: "product_newest" });

// One review per user per product: same rule as UNIQUE (product_id, user_id) in PostgreSQL
db.product_reviews.createIndex({ productId: 1, "user.userId": 1 }, { unique: true, name: "one_review_per_user" });

// "My reviews" page for a customer
db.product_reviews.createIndex({ "user.userId": 1, createdAt: -1 }, { name: "user_reviews" });

// Full-text search across review text; matches in the title count three times as much
db.product_reviews.createIndex(
  { title: "text", body: "text" },
  { weights: { title: 3, body: 1 }, name: "review_text" }
);

db.product_reviews.insertMany([
  {
    productId: 1, productName: "Smartphone X10",
    user: { userId: 2, displayName: "Alice U." },
    rating: 5, title: "Battery lasts two days",
    body: "Great battery life and a bright screen. Charges quickly with the 65W charger.",
    pros: ["battery life", "screen"], cons: ["no headphone jack"],
    tags: ["battery", "display"],
    images: [{ url: "https://example.com/reviews/x10-front.jpg", caption: "Front view" }],
    verifiedPurchase: true, helpfulVotes: 14,
    merchantReply: { body: "Thanks Alice, glad you're enjoying it!", repliedAt: new Date("2026-09-05T09:00:00Z") },
    createdAt: new Date("2026-09-03T18:20:00Z")
  },
  {
    productId: 1, productName: "Smartphone X10",
    user: { userId: 3, displayName: "Brian M." },
    rating: 3, title: "Good phone, average camera",
    body: "Battery is excellent but low-light photos are noisy.",
    pros: ["battery"], cons: ["camera in low light"],
    tags: ["battery", "camera"],
    verifiedPurchase: false, helpfulVotes: 4,
    createdAt: new Date("2026-09-22T11:05:00Z")
  },
  {
    productId: 4, productName: "USB-C Charger 65W",
    user: { userId: 2, displayName: "Alice U." },
    rating: 4, title: "Fast but warm",
    body: "Charges my laptop and phone fast. Gets a bit warm under full load.",
    cons: ["runs warm"], tags: ["charging"],
    verifiedPurchase: true, helpfulVotes: 6,
    createdAt: new Date("2026-09-04T08:45:00Z"),
    updatedAt: new Date("2026-09-10T12:00:00Z")
  },
  {
    productId: 5, productName: "Clean Code",
    user: { userId: 3, displayName: "Brian M." },
    rating: 5, title: "Every developer should read it",
    body: "Changed how I name variables and structure functions.",
    tags: ["programming", "classic"],
    verifiedPurchase: true, helpfulVotes: 22,
    createdAt: new Date("2026-09-25T20:30:00Z")
  },
  {
    productId: 6, productName: "Database System Concepts",
    user: { userId: 3, displayName: "Brian M." },
    rating: 4, title: "Thorough textbook",
    body: "Excellent chapters on indexing and normalization. Heavy to carry around.",
    pros: ["depth", "exercises"], cons: ["very heavy"],
    tags: ["textbook", "databases"],
    verifiedPurchase: true, helpfulVotes: 9,
    createdAt: new Date("2026-09-26T07:15:00Z")
  },
  {
    productId: 11, productName: "Electric Kettle",
    user: { userId: 2, displayName: "Alice U." },
    rating: 2, title: "Lid broke after a month",
    body: "Boils fast, but the lid hinge snapped after a few weeks.",
    cons: ["build quality"], tags: ["durability"],
    images: [
      { url: "https://example.com/reviews/kettle-lid.jpg", caption: "Broken hinge" },
      { url: "https://example.com/reviews/kettle-side.jpg" }
    ],
    verifiedPurchase: true, helpfulVotes: 3,
    merchantReply: { body: "Sorry about that. We've sent a replacement lid.", repliedAt: new Date("2026-10-20T10:00:00Z") },
    createdAt: new Date("2026-10-18T16:40:00Z")
  },
  {
    productId: 8, productName: "Cotton T-Shirt",
    user: { userId: 2, displayName: "Alice U." },
    rating: 4, body: "Soft and fits well. Washes without shrinking.",
    verifiedPurchase: true, helpfulVotes: 1,
    createdAt: new Date("2026-10-19T13:10:00Z")
  }
]);

// ---------------------------------------------------------------------
// 2. activity_logs
// ---------------------------------------------------------------------
// One document per event. The "details" object has a DIFFERENT shape for each eventType,
// which would need many nullable columns or extra tables in a relational design.
db.createCollection("activity_logs", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["eventType", "timestamp", "sessionId"],
      properties: {
        eventType: {
          enum: ["LOGIN", "LOGOUT", "PRODUCT_VIEW", "SEARCH", "ADD_TO_CART",
                 "ORDER_PLACED", "ORDER_STATUS_CHANGED", "REVIEW_POSTED", "ERROR"]
        },
        timestamp: { bsonType: "date" },
        userId:    { bsonType: ["int", "null"], description: "null for anonymous visitors" },
        sessionId: { bsonType: "string" },
        details:   { bsonType: "object", description: "free-form, depends on eventType" },
        client: {
          bsonType: "object",
          properties: {
            app:     { bsonType: "string" },
            version: { bsonType: "string" },
            os:      { bsonType: "string" }
          }
        }
      }
    }
  },
  validationLevel: "moderate",   // logs are best-effort: old documents aren't re-checked on update
  validationAction: "error"
});

// TTL index: MongoDB deletes each log automatically 90 days after its timestamp
db.activity_logs.createIndex({ timestamp: 1 }, { expireAfterSeconds: 60 * 60 * 24 * 90, name: "expire_after_90_days" });

// One user's activity, newest first
db.activity_logs.createIndex({ userId: 1, timestamp: -1 }, { name: "user_activity" });

// Analytics by event type over a time window
db.activity_logs.createIndex({ eventType: 1, timestamp: -1 }, { name: "event_type_time" });

// Sample timestamps are relative to NOW, so the 90-day TTL doesn't delete them straight away
const hoursAgo = (h) => new Date(Date.now() - h * 60 * 60 * 1000);
const desktop = { app: "smart-ecommerce-desktop", version: "1.0", os: "macOS" };

db.activity_logs.insertMany([
  { eventType: "LOGIN",        timestamp: hoursAgo(50), userId: 2, sessionId: "s-1001", client: desktop,
    details: { method: "password", success: true } },
  { eventType: "SEARCH",       timestamp: hoursAgo(49.9), userId: 2, sessionId: "s-1001", client: desktop,
    details: { query: "phone", categoryId: null, resultsCount: 8001, page: 1, durationMs: 5 } },
  { eventType: "PRODUCT_VIEW", timestamp: hoursAgo(49.8), userId: 2, sessionId: "s-1001", client: desktop,
    details: { productId: 1, source: "search" } },
  { eventType: "ADD_TO_CART",  timestamp: hoursAgo(49.7), userId: 2, sessionId: "s-1001", client: desktop,
    details: { productId: 1, quantity: 1 } },
  { eventType: "ORDER_PLACED", timestamp: hoursAgo(49.6), userId: 2, sessionId: "s-1001", client: desktop,
    details: { orderId: 3, itemCount: 2, total: NumberDecimal("67.00"), paymentMethod: "card" } },
  { eventType: "SEARCH",       timestamp: hoursAgo(30), userId: null, sessionId: "s-1002", client: desktop,
    details: { query: "kettle", categoryId: 4, resultsCount: 3, page: 1, durationMs: 2 } },
  { eventType: "SEARCH",       timestamp: hoursAgo(29), userId: 3, sessionId: "s-1003", client: desktop,
    details: { query: "phone", categoryId: null, resultsCount: 8001, page: 2, durationMs: 4 } },
  { eventType: "ORDER_STATUS_CHANGED", timestamp: hoursAgo(20), userId: 1, sessionId: "s-1004", client: desktop,
    details: { orderId: 2, from: "PAID", to: "SHIPPED" } },
  { eventType: "REVIEW_POSTED", timestamp: hoursAgo(10), userId: 2, sessionId: "s-1005", client: desktop,
    details: { productId: 11, rating: 2 } },
  { eventType: "ERROR",        timestamp: hoursAgo(2), userId: 3, sessionId: "s-1006", client: desktop,
    details: { operation: "placeOrder", message: "Not enough stock for \"Rain Jacket\": only 4 left.", sqlState: null } }
]);

// ---------------------------------------------------------------------
// 3. product_rating_summary (a view)
// ---------------------------------------------------------------------
// Computed from product_reviews every time it's read, so it's never out of date.
db.createView("product_rating_summary", "product_reviews", [
  {
    $group: {
      _id: "$productId",
      productName: { $first: "$productName" },
      reviewCount: { $sum: 1 },
      averageRating: { $avg: "$rating" },
      stars5: { $sum: { $cond: [{ $eq: ["$rating", 5] }, 1, 0] } },
      stars4: { $sum: { $cond: [{ $eq: ["$rating", 4] }, 1, 0] } },
      stars3: { $sum: { $cond: [{ $eq: ["$rating", 3] }, 1, 0] } },
      stars2: { $sum: { $cond: [{ $eq: ["$rating", 2] }, 1, 0] } },
      stars1: { $sum: { $cond: [{ $eq: ["$rating", 1] }, 1, 0] } }
    }
  },
  {
    $project: {
      _id: 0,
      productId: "$_id",
      productName: 1,
      reviewCount: 1,
      averageRating: { $round: ["$averageRating", 2] },
      breakdown: { "5": "$stars5", "4": "$stars4", "3": "$stars3", "2": "$stars2", "1": "$stars1" }
    }
  },
  { $sort: { averageRating: -1, reviewCount: -1 } }
]);

print("smart_ecommerce initialised:");
print("  product_reviews:  " + db.product_reviews.countDocuments() + " documents");
print("  activity_logs:    " + db.activity_logs.countDocuments() + " documents");
print("  product_rating_summary view created");
