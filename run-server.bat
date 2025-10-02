@echo off
echo Starting Whiteboard Server...
cd /d "%~dp0src\main\java"

javac -cp . org\example\server\WhiteboardServer.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

java -cp . org.example.server.WhiteboardServer
pause