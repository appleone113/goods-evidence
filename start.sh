#!/usr/bin/env bash
# Goods Evidence System - one-click start for Linux / macOS
# All paths are relative to this script; no absolute author paths.
set -e
cd "$(dirname "$0")"

echo "============================================================"
echo "  Goods Evidence System - one-click start"
echo "============================================================"
echo

echo "[1/6] Checking runtime (java / node)..."
command -v java >/dev/null 2>&1 || { echo "  ERROR: install JDK 22 and add to PATH"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "  ERROR: install Node.js 18+"; exit 1; }
echo "      java / node OK"

echo "[2/6] Checking MySQL on port 3306..."
if (echo > /dev/tcp/127.0.0.1/3306) >/dev/null 2>&1; then
  echo "      MySQL 3306 OK"
else
  echo "      WARNING: MySQL 3306 not reachable."
  echo "      Start MySQL and run: mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql"
fi

echo "[3/6] Backend jar..."
if [ ! -f backend/target/goods-evidence-1.0.0.jar ]; then
  command -v mvn >/dev/null 2>&1 || { echo "  ERROR: install Maven 3.9+ or place a prebuilt jar"; exit 1; }
  echo "      Building backend, first run takes 1-3 min..."
  (cd backend && mvn clean package -DskipTests)
else
  echo "      jar found, skip build"
fi

echo "[4/6] Frontend dependencies..."
if [ ! -f frontend/node_modules/vite/bin/vite.js ]; then
  echo "      Installing frontend deps, first run takes 1-3 min..."
  (cd frontend && npm install)
else
  echo "      vite found, skip npm install"
fi

echo "[5/6] Starting services, Mock chain by default, no FISCO needed..."
(cd backend && nohup java -jar target/goods-evidence-1.0.0.jar \
  --server.port=8080 --fisco.enabled=false > backend.log 2>&1 &)
(cd frontend && nohup node node_modules/vite/bin/vite.js \
  --host 0.0.0.0 --port 5173 --strictPort > frontend.log 2>&1 &)

echo "[6/6] Waiting for backend on port 8080..."
for i in $(seq 1 30); do
  if (echo > /dev/tcp/127.0.0.1/8080) >/dev/null 2>&1; then break; fi
  sleep 2
done

echo
echo "  Done. Open http://localhost:5173"
echo "  FIRST RUN: the new database is empty. Register accounts at /register first."
echo "  (ent / user / admin with 123456 only exist on the author's machine.)"
echo "  Logs: backend/backend.log, frontend/frontend.log"
echo "  To stop: pkill -f goods-evidence ; pkill -f vite"
echo "  Note: default is Mock chain. See README for a real FISCO chain."
