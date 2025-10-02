@echo off
echo Starting Whiteboard Client...
cd /d "%~dp0src\main\java"

javac -cp . org\example\client\WhiteboardClient.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

java -cp . org.example.client.WhiteboardClient
pause