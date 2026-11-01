// Example queries for the Smart E-Commerce MongoDB collections.
// Run:  docker exec -it smart-ecommerce-mongo mongosh smart_ecommerce /scripts/queries.js

db = db.getSiblingDB("smart_ecommerce");

function heading(text) {
  print("\n==== " + text + " ====");
}

heading("1. Reviews for product 1, newest first (uses index product_newest)");
db.product_reviews
  .find({ productId: 1 }, { _id: 0, "user.displayName": 1, rating: 1, title: 1, createdAt: 1 })
  .sort({ createdAt: -1 })
  .forEach(printjson);

heading("2. Full-text search for 'battery' (uses the text index)");
db.product_reviews
  .find({ $text: { $search: "battery" } },
        { _id: 0, productName: 1, title: 1, score: { $meta: "textScore" } })
  .sort({ score: { $meta: "textScore" } })
  .forEach(printjson);

heading("3. Rating summary per product (from the view)");
db.product_rating_summary.find().forEach(printjson);

heading("4. Most helpful verified reviews");
db.product_reviews
  .find({ verifiedPurchase: true }, { _id: 0, productName: 1, title: 1, helpfulVotes: 1 })
  .sort({ helpfulVotes: -1 })
  .limit(3)
  .forEach(printjson);

heading("5. Reviews that include photos (querying inside an array)");
db.product_reviews
  .find({ "images.0": { $exists: true } }, { _id: 0, productName: 1, imageCount: { $size: "$images" } })
  .forEach(printjson);

heading("6. Everything user 2 did in the last 3 days (uses index user_activity)");
db.activity_logs
  .find({ userId: 2, timestamp: { $gte: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000) } },
        { _id: 0, eventType: 1, timestamp: 1, details: 1 })
  .sort({ timestamp: -1 })
  .forEach(printjson);

heading("7. Events per type in the last 7 days (aggregation)");
db.activity_logs.aggregate([
  { $match: { timestamp: { $gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) } } },
  { $group: { _id: "$eventType", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
]).forEach(printjson);

heading("8. Most common search terms (reaching inside the flexible 'details' object)");
db.activity_logs.aggregate([
  { $match: { eventType: "SEARCH" } },
  { $group: { _id: { $toLower: "$details.query" }, searches: { $sum: 1 }, avgResults: { $avg: "$details.resultsCount" } } },
  { $sort: { searches: -1 } }
]).forEach(printjson);

heading("9. Query plan: is the index used? (compare with EXPLAIN in PostgreSQL)");
const plan = db.product_reviews.find({ productId: 1 }).sort({ createdAt: -1 }).explain("executionStats");
let stage = plan.queryPlanner.winningPlan;
const stages = [];
while (stage) {
  stages.push(stage.stage + (stage.indexName ? " (" + stage.indexName + ")" : ""));
  stage = stage.inputStage;
}
print("Winning plan: " + stages.join(" <- "));
print("Documents examined: " + plan.executionStats.totalDocsExamined +
      ", keys examined: " + plan.executionStats.totalKeysExamined);

heading("10. Validation in action: a rating of 7 is rejected");
try {
  db.product_reviews.insertOne({
    productId: 2, productName: "Wireless Earbuds",
    user: { userId: 3, displayName: "Brian M." },
    rating: 7, body: "Too good!", verifiedPurchase: false, createdAt: new Date()
  });
  print("Unexpected: the invalid review was accepted");
} catch (e) {
  print("Rejected as expected: " + e.message);
}
