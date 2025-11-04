@echo off
cd /d "%~dp0"
echo Starting WebSocket Server...
"C:\Program Files\Apache\Maven\bin\mvn.cmd" clean compile exec:java -Dexec.mainClass=org.example.server.WebSocketWhiteboardServer
