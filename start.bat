@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
title Goods Evidence System - One-click Start

echo ============================================================
echo   Goods Evidence System - one-click start (Windows)
echo ============================================================
echo.

echo [1/6] Checking runtime (java / node)...
where java >nul 2>nul
if errorlevel 1 goto NOJAVA
where node >nul 2>nul
if errorlevel 1 goto NONODE
echo       java / node OK

echo [2/6] Checking MySQL on port 3306...
netstat -an | findstr ":3306" | findstr "LISTENING" >nul
if errorlevel 1 (
  echo       WARNING: MySQL 3306 not detected.
  echo       Start MySQL and import sql\schema.sql before using the system.
) else (
  echo       MySQL 3306 OK
)

echo [3/6] Backend jar...
if exist "backend\target\goods-evidence-1.0.0.jar" (
  echo       jar found, skip build
) else (
  echo       Building backend, first run takes 1-3 min...
  where mvn >nul 2>nul
  if errorlevel 1 goto NOMAVEN
  pushd backend
  call mvn clean package -DskipTests
  if errorlevel 1 goto BUILDFAIL
  popd
)

echo [4/6] Frontend dependencies...
if exist "frontend\node_modules\vite\bin\vite.js" (
  echo       vite found, skip npm install
) else (
  echo       Installing frontend deps, first run takes 1-3 min...
  pushd frontend
  call npm install
  if errorlevel 1 goto NPMFAIL
  popd
)

echo [5/6] Starting services, Mock chain by default, no FISCO needed...
cd backend
start "goods-backend" cmd /k "java -jar target\goods-evidence-1.0.0.jar --server.port=8080 --fisco.enabled=false"
cd ..
cd frontend
start "goods-frontend" cmd /k "node node_modules\vite\bin\vite.js --host 0.0.0.0 --port 5173 --strictPort"
cd ..

echo [6/6] Waiting 25s for backend, then opening browser...
timeout /t 25 >nul
start http://localhost:5173
echo.
echo   Done. Open http://localhost:5173
echo   FIRST RUN: the new database is empty. Register accounts at /register first.
echo   (ent / user / admin with 123456 only exist on the author's machine.)
echo   To stop: close the two black console windows.
echo   Note: default is Mock chain. See README for a real FISCO chain.
echo.
pause
exit /b 0

:NOJAVA
echo   ERROR: java not found. Install JDK 22 and add it to PATH or JAVA_HOME.
pause
exit /b 1
:NONODE
echo   ERROR: node not found. Install Node.js 18 or newer.
pause
exit /b 1
:NOMAVEN
echo   ERROR: maven not found and backend jar is missing. Install Maven 3.9+.
pause
exit /b 1
:BUILDFAIL
popd
echo   ERROR: backend build failed. Check that java -version is 22.
pause
exit /b 1
:NPMFAIL
popd
echo   ERROR: npm install failed. Check your network or npm registry.
pause
exit /b 1
