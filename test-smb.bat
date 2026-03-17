@echo off
REM SMB Test Environment Control Script for Windows
REM Provides easy commands to manage the SMB test server

setlocal enabledelayedexpansion

set COMPOSE_FILE=docker-compose.smb-test.yml
set CONTAINER_NAME=semoss-smb-test

if "%1"=="" goto help
if "%1"=="start" goto start
if "%1"=="stop" goto stop
if "%1"=="restart" goto restart
if "%1"=="status" goto status
if "%1"=="logs" goto logs
if "%1"=="test" goto test
if "%1"=="junit" goto junit
if "%1"=="files" goto files
if "%1"=="clean" goto clean
if "%1"=="shell" goto shell
if "%1"=="help" goto help
goto unknown

:header
echo ========================================
echo   SMB Storage Engine Test Environment
echo ========================================
echo.
goto :eof

:check_docker
where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not installed or not in PATH
    exit /b 1
)

docker ps >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker daemon is not running
    exit /b 1
)
goto :eof

:start
call :header
echo Starting SMB test server...
echo.

call :check_docker
if errorlevel 1 exit /b 1

REM Create mount directory if it doesn't exist
if not exist "test-data\smb-mount" mkdir "test-data\smb-mount"

REM Start the container
docker-compose -f %COMPOSE_FILE% up -d

REM Wait for container to be ready
echo Waiting for server to be ready
timeout /t 3 /nobreak >nul

echo [OK] SMB server is running!
echo.
echo Connection details:
echo   Host:       localhost
echo   Port:       445
echo   Share:      testshare
echo   Username:   testuser
echo   Password:   testpass
echo   Domain:     WORKGROUP
echo.
echo Mount directory: %CD%\test-data\smb-mount
echo.
goto end

:stop
call :header
echo Stopping SMB test server...
echo.

docker-compose -f %COMPOSE_FILE% down

echo [OK] SMB server stopped
echo.
goto end

:restart
call :stop
call :start
goto end

:status
call :header

docker ps --format "{{.Names}}" | findstr /x "%CONTAINER_NAME%" >nul 2>&1
if errorlevel 1 (
    echo [INFO] SMB server is not running
    echo.
    echo Start it with: test-smb.bat start
) else (
    echo [OK] SMB server is running
    echo.
    docker-compose -f %COMPOSE_FILE% ps
    echo.
    echo Logs: test-smb.bat logs
)
echo.
goto end

:logs
call :header
echo Showing logs (Ctrl+C to exit)...
echo.
docker-compose -f %COMPOSE_FILE% logs -f
goto end

:test
call :header

REM Check if server is running
docker ps --format "{{.Names}}" | findstr /x "%CONTAINER_NAME%" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] SMB server is not running
    echo.
    echo Start it first: test-smb.bat start
    exit /b 1
)

echo [INFO] Running manual test...
echo.

REM Run the test class
call mvn exec:java -Dexec.mainClass="prerna.engine.impl.storage.SmbStorageEngineManualTest" -Dexec.classpathScope=test

goto end

:junit
call :header

REM Check if server is running
docker ps --format "{{.Names}}" | findstr /x "%CONTAINER_NAME%" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Starting SMB server for tests...
    call :start
    set STOP_AFTER=true
)

echo [INFO] Running JUnit tests...
echo.

call mvn test -Dtest=SmbStorageEngineTest

if "!STOP_AFTER!"=="true" (
    echo.
    echo [INFO] Stopping test server...
    call :stop
)

goto end

:files
call :header
echo Files in SMB share:
echo.

if exist "test-data\smb-mount" (
    dir /a "test-data\smb-mount"
) else (
    echo [ERROR] Mount directory does not exist
)
echo.
goto end

:clean
call :header
echo Cleaning SMB share files...

if exist "test-data\smb-mount" (
    del /q "test-data\smb-mount\*.*" 2>nul
    for /d %%p in ("test-data\smb-mount\*") do rmdir "%%p" /s /q 2>nul
    echo [OK] Files cleaned
) else (
    echo [INFO] Mount directory does not exist
)
echo.
goto end

:shell
call :header
echo Opening shell in SMB container...
echo.
docker exec -it %CONTAINER_NAME% /bin/bash
goto end

:help
call :header
echo Usage: test-smb.bat [command]
echo.
echo Commands:
echo   start       - Start the SMB test server
echo   stop        - Stop the SMB test server
echo   restart     - Restart the SMB test server
echo   status      - Show server status
echo   logs        - Show server logs (follow)
echo   test        - Run manual test
echo   junit       - Run JUnit integration tests
echo   files       - List files in SMB share
echo   clean       - Remove all files from SMB share
echo   shell       - Open shell in container
echo   help        - Show this help message
echo.
echo Examples:
echo   test-smb.bat start          # Start server
echo   test-smb.bat test           # Run manual test
echo   test-smb.bat logs           # Watch logs
echo   test-smb.bat stop           # Stop server
echo.
goto end

:unknown
echo [ERROR] Unknown command: %1
echo.
goto help

:end
endlocal
