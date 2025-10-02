@echo off
echo Live Whiteboard Application
echo ========================

cd /d "%~dp0src\main\java"

echo Compiling...
javac -cp . org\example\Main.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Starting application...
java -cp . org.example.Main

pause