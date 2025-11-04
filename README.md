# Live Whiteboard Application

A real-time collaborative whiteboard application built with Java network programming. Multiple users can join the same whiteboard session and draw, write text, and erase content together in real-time.

## Features

- **Multi-user Support**: Multiple users can connect simultaneously
- **Real-time Synchronization**: All drawing operations are synchronized across all connected clients
- **Drawing Tools**: 
  - Freehand drawing with adjustable stroke width
  - Text insertion
  - Eraser tool
  - Color selection (Black, Red, Blue, Green, Yellow, Orange, Purple, Pink)
- **User Management**: Users join with their name and see when others join/leave
- **Clear Function**: Any user can clear the entire whiteboard
- **Cross-platform**: Works on Windows, Mac, and Linux

## Architecture

The application uses a client-server architecture:

- **Server (`WhiteboardServer`)**: Manages client connections and broadcasts drawing operations
- **Client (`WhiteboardClient`)**: Handles network communication with the server
- **GUI (`WhiteboardGUI`)**: Provides the drawing interface using Java Swing
- **Models**: `DrawingMessage` and `User` classes for data exchange

## Network Protocol

The application uses Java Object Serialization over TCP sockets:
- **Port**: 12345 (default)
- **Protocol**: TCP
- **Serialization**: Java Object Streams
- **Message Types**: DRAW, TEXT, ERASE, CLEAR, USER_JOIN, USER_LEAVE

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

### Building the Application

```bash
mvn clean compile
```

## 🌐 Local Network Setup (Connect Multiple Devices)

**Want to use this with multiple PCs on your WiFi? See the [Network Setup Guide](NETWORK_SETUP_GUIDE.md)**

### Quick Network Setup:

1. **On your laptop (Server):**
   ```bash
   start-server-network.bat
   ```
   Note the IP address displayed (e.g., `192.168.1.100`)

2. **On other PCs (Clients):**
   ```bash
   start-client-network.bat
   ```
   - Enter your username
   - Enter the server IP from step 1
   - Port: `12345`

3. **Important:**
   - All devices must be on the **same WiFi**
   - Windows Firewall may need to allow port 12345
   - Server must stay running while others are connected

📖 **For detailed instructions, troubleshooting, and firewall setup, see [NETWORK_SETUP_GUIDE.md](NETWORK_SETUP_GUIDE.md)**

---

### Running the Application

#### Option 1: Using the Main Class (Recommended)

Run the main class and choose your mode:

```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

This will show a dialog where you can choose to start either a server or client.

#### Option 2: Command Line Arguments

Start the server:
```bash
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="server"
```

Start a client:
```bash
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="client"
```

#### Option 3: Direct Class Execution

Start the server directly:
```bash
mvn exec:java -Dexec.mainClass="org.example.server.WhiteboardServer"
```

Start a client directly:
```bash
mvn exec:java -Dexec.mainClass="org.example.client.WhiteboardClient"
```

### Usage Instructions

1. **Start the Server**: 
   - Run the application and choose "Start Server" or use command line
   - The server will start on port 12345
   - Keep the server running while clients connect

2. **Connect Clients**:
   - Run the application and choose "Start Client"
   - Enter your username when prompted
   - The whiteboard window will open if connection is successful

3. **Drawing**:
   - **Draw Mode**: Click and drag to draw freehand
   - **Text Mode**: Click where you want to place text, then type in the dialog
   - **Erase Mode**: Click and drag to erase content
   - **Clear All**: Button to clear the entire whiteboard

4. **Collaboration**:
   - All drawing operations are instantly shared with other users
   - See status messages when users join or leave
   - All users see the same content in real-time

## Configuration

### Server Configuration

The server can be configured by modifying `WhiteboardServer.java`:
- **PORT**: Change the port number (default: 12345)
- **MAX_CLIENTS**: Add client limits if needed

### Client Configuration

The client can be configured by modifying `WhiteboardClient.java`:
- **SERVER_HOST**: Change server address (default: "localhost")
- **SERVER_PORT**: Must match server port (default: 12345)

## Troubleshooting

### Common Issues

1. **Connection Refused**:
   - Make sure the server is running
   - Check if the port 12345 is available
   - Verify firewall settings

2. **Username Already Exists**:
   - Each user must have a unique username
   - Try a different username

3. **Drawing Not Synchronizing**:
   - Check network connection
   - Restart both server and clients

### Network Configuration

For usage across different machines:
1. Start the server - it will display the network IP address automatically
2. On client machines, enter the server's IP address in the connection dialog
3. Ensure all devices are on the same WiFi network
4. Make sure the firewall allows connections on port 12345

**Windows Firewall Command:**
```powershell
# Run as Administrator
netsh advfirewall firewall add rule name="Whiteboard Server" dir=in action=allow protocol=TCP localport=12345
```

📖 **For complete network setup instructions, see [NETWORK_SETUP_GUIDE.md](NETWORK_SETUP_GUIDE.md)**

## Technical Details

### Threading Model
- Server uses one thread per client connection
- GUI updates are performed on the Event Dispatch Thread
- Network operations are handled on separate threads

### Message Flow
1. Client connects and sends USER_JOIN message
2. Server adds client and sends drawing history
3. Client drawing operations create DrawingMessage objects
4. Server broadcasts messages to all other clients
5. Clients update their GUI based on received messages

### Data Persistence
- Drawing history is stored in server memory during session
- All data is lost when server stops
- New clients receive the current drawing state when they join

## Future Enhancements

Potential improvements:
- Persistent storage of whiteboard sessions
- User authentication and sessions
- Multiple whiteboard rooms
- More drawing tools (shapes, lines, etc.)
- Undo/Redo functionality
- File export (PNG, PDF)
- WebSocket support for web clients

## License

This project is for educational purposes demonstrating Java network programming concepts.