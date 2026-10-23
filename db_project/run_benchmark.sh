#!/usr/bin/env bash
# Runs the whole indexing benchmark from start to finish.
# Run from your PROJECT ROOT (the folder containing gradlew):
# WARNING: step 1 drops and recreates every table in the database.
set -e

DB="${PGDATABASE:-smart_ecommerce}"
DB_USER="${PGUSER:-postgres}"
PSQL="psql -U $DB_USER -d $DB -v ON_ERROR_STOP=1 -q"

echo "[1/7] Recreating tables (01_schema.sql)"
$PSQL -f sql/01_schema.sql

echo "[2/7] Loading demo data (02_seed.sql)"
$PSQL -f sql/02_seed.sql

echo "[3/7] Loading bulk test data (03_bulk_data.sql), takes up to a minute"
$PSQL -f sql/03_bulk_data.sql

echo "[4/7] Benchmark WITHOUT indexes"
./gradlew benchmark -Plabel=before

echo "[5/7] Creating indexes (04_indexes.sql)"
$PSQL -f sql/04_indexes.sql

echo "[6/7] Benchmark WITH indexes"
./gradlew benchmark -Plabel=after

echo "[7/7] Writing comparison"
./gradlew benchmark -Plabel=compare

echo
echo "Done. Results are in benchmark-results/ (before.csv, after.csv, comparison.md)."
