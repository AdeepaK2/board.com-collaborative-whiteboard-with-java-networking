@echo off
echo ========================================
echo   Whiteboard Server
echo ========================================
echo.
echo Starting WebSocket server...
echo.

cd /d "%~dp0"
mvn exec:java -Dexec.mainClass="org.example.Main"

pause
