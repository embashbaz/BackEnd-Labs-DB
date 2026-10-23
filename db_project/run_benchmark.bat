@echo off
REM Runs the whole indexing benchmark from start to finish.
REM Run from your PROJECT ROOT (the folder containing gradlew.bat):
REM   set PGPASSWORD=yourpassword
REM   run_benchmark.bat
REM WARNING: step 1 drops and recreates every table in the database.
setlocal

if "%PGDATABASE%"=="" set PGDATABASE=smart_ecommerce
if "%PGUSER%"=="" set PGUSER=postgres
set PSQL=psql -U %PGUSER% -d %PGDATABASE% -v ON_ERROR_STOP=1 -q

echo [1/7] Recreating tables (01_schema.sql)
%PSQL% -f sql\01_schema.sql || goto :failed

echo [2/7] Loading demo data (02_seed.sql)
%PSQL% -f sql\02_seed.sql || goto :failed

echo [3/7] Loading bulk test data (03_bulk_data.sql), takes up to a minute
%PSQL% -f sql\03_bulk_data.sql || goto :failed

echo [4/7] Benchmark WITHOUT indexes
call gradlew.bat benchmark -Plabel=before || goto :failed

echo [5/7] Creating indexes (04_indexes.sql)
%PSQL% -f sql\04_indexes.sql || goto :failed

echo [6/7] Benchmark WITH indexes
call gradlew.bat benchmark -Plabel=after || goto :failed

echo [7/7] Writing comparison
call gradlew.bat benchmark -Plabel=compare || goto :failed

echo.
echo Done. Results are in benchmark-results\ (before.csv, after.csv, comparison.md).
exit /b 0

:failed
echo.
echo A step failed. Read the error above, fix it, and run the script again.
exit /b 1
