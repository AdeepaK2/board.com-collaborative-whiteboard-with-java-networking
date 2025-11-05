# Whiteboard Export/Import System - Comprehensive Guide

## 📋 Overview

This system adds **save/load functionality** to your collaborative whiteboard with full network programming principles:

### ✅ Network Programming Principles Demonstrated

1. **TCP (Transmission Control Protocol)**

   - HTTP REST API server listening on port 8081
   - WebSocket server on port 8080
   - Reliable connection-oriented communication

2. **URI (Uniform Resource Identifier)**

   - RESTful endpoint design:
     - `POST /api/boards` - Save a board
     - `GET /api/boards` - List all boards
     - `GET /api/boards/{id}` - Load specific board
     - `DELETE /api/boards/{id}` - Delete a board

3. **Multithreading**

   - Thread pool (`ExecutorService`) for handling multiple HTTP requests
   - Thread pool for WebSocket connections
   - Each client connection handled in separate thread
   - Concurrent data structures (`ConcurrentHashMap`)

4. **NIO (Non-blocking I/O)**
   - `AsynchronousFileChannel` for non-blocking file operations
   - `CompletableFuture` for async operations
   - Efficient file reads/writes without blocking threads

---

## 🏗️ Architecture

```
┌─────────────────────┐
│  React Frontend     │
│  (Port 3000)        │
└──────────┬──────────┘
           │
           ├─── WebSocket (Port 8080) ───┐
           │    Real-time drawing        │
           │                             │
           └─── HTTP REST (Port 8081) ───┤
                Save/Load boards         │
                                         │
                                         ▼
                            ┌────────────────────────┐
                            │  Java Backend          │
                            │                        │
                            │  ┌─────────────────┐  │
                            │  │ WebSocket Server│  │
                            │  │  (Rooms-based)  │  │
                            │  └─────────────────┘  │
                            │                        │
                            │  ┌─────────────────┐  │
                            │  │ HTTP REST Server│  │
                            │  │  (Thread Pool)  │  │
                            │  └─────────────────┘  │
                            │                        │
                            │  ┌─────────────────┐  │
                            │  │ Storage Service │  │
                            │  │  (NIO Async)    │  │
                            │  └─────────────────┘  │
                            └────────────────────────┘
                                         │
                                         ▼
                                   File System
                                 saved_boards/
                                   ├─ board1.json
                                   ├─ board2.json
                                   └─ board3.json
```

---

## 📦 File Format: JSON

### Board JSON Structure

```json
{
  "id": "board-1699200000",
  "name": "My Whiteboard",
  "createdBy": "Alice",
  "createdAt": 1699200000000,
  "lastModified": 1699200000000,
  "strokes": [
    {
      "id": "alice-1699200001",
      "username": "Alice",
      "points": [
        { "x": 100, "y": 100, "color": "#000000", "size": 2 },
        { "x": 102, "y": 102, "color": "#000000", "size": 2 }
      ]
    }
  ],
  "shapes": [
    {
      "id": "alice-1699200002",
      "type": "rectangle",
      "x": 200,
      "y": 200,
      "width": 100,
      "height": 50,
      "color": "#ff0000",
      "size": 2,
      "fillColor": "#ffcccc",
      "username": "Alice"
    }
  ]
}
```

**Why JSON?**

- Human-readable and debuggable
- Easy to parse with GSON library
- Standard web format
- Can be imported/exported easily
- Compatible with many tools (including Excalidraw if needed)

---

## 🚀 How It Works

### 1. **Saving a Board**

**Frontend → Backend Flow:**

```
User clicks "Save"
    ↓
React collects all strokes + shapes
    ↓
Creates Board JSON object
    ↓
HTTP POST to /api/boards
    ↓
HTTPRestServer receives request
    ↓
ThreadPool assigns worker thread
    ↓
BoardStorageService saves via NIO
    ↓
AsynchronousFileChannel writes to disk
    ↓
Success response sent back
```

**Code Example (Frontend):**

```typescript
const saveBoard = async () => {
  const board = {
    id: `board-${Date.now()}`,
    name: "My Whiteboard",
    createdBy: username,
    createdAt: Date.now(),
    lastModified: Date.now(),
    strokes: strokes,
    shapes: shapes,
  };

  const response = await fetch("http://localhost:8081/api/boards", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(board),
  });

  const result = await response.json();
  console.log("Saved:", result.id);
};
```

### 2. **Loading a Board**

**Frontend → Backend Flow:**

```
User selects board from list
    ↓
HTTP GET /api/boards/{id}
    ↓
HTTPRestServer routes to loadBoard
    ↓
ThreadPool handles request
    ↓
BoardStorageService.loadBoard(id)
    ↓
AsynchronousFileChannel reads file
    ↓
JSON parsed to Board object
    ↓
Board sent to frontend
    ↓
Frontend renders all strokes/shapes
    ↓
WebSocket connects to room with board ID
```

**Code Example (Frontend):**

```typescript
const loadBoard = async (boardId: string) => {
  const response = await fetch(`http://localhost:8081/api/boards/${boardId}`);
  const board = await response.json();

  // Clear current canvas
  setStrokes([]);
  setShapes([]);

  // Load board data
  setStrokes(board.strokes);
  setShapes(board.shapes);

  // Connect to room for collaboration
  connectToRoom(boardId);
};
```

### 3. **Editing After Import** ✅

**YES! Full editing is supported:**

- ✅ Draw new strokes
- ✅ Add new shapes
- ✅ Drag existing elements
- ✅ Resize shapes
- ✅ Fill shapes with color
- ✅ Delete elements
- ✅ Real-time collaboration with other users

**How it works:**

1. Board is loaded into frontend state (`strokes`, `shapes`)
2. User joins WebSocket room with board ID
3. All drawing operations work normally
4. Changes are synced via WebSocket to all users in the room
5. User can save again to update the board

### 4. **Listing All Boards**

Since there's **no authentication**, all boards are public and shared:

**Frontend → Backend Flow:**

```
User opens "Load Board" dialog
    ↓
HTTP GET /api/boards
    ↓
HTTPRestServer.listBoards()
    ↓
BoardStorageService scans saved_boards/ directory
    ↓
Returns list of board metadata:
  - ID, name, creator, date, element count
    ↓
Frontend displays as selectable list
```

**Response Example:**

```json
[
  {
    "id": "board-1699200000",
    "name": "Meeting Notes",
    "createdBy": "Alice",
    "createdAt": 1699200000000,
    "lastModified": 1699200500000,
    "elementCount": 25
  },
  {
    "id": "board-1699300000",
    "name": "Design Mockup",
    "createdBy": "Bob",
    "createdAt": 1699300000000,
    "lastModified": 1699300200000,
    "elementCount": 42
  }
]
```

---

## 🔧 Network Programming Details

### TCP Communication

**HTTP REST Server (Port 8081):**

```java
ServerSocket serverSocket = new ServerSocket(8081);  // TCP listener
while (running) {
    Socket clientSocket = serverSocket.accept();  // TCP handshake
    threadPool.execute(() -> handleRequest(clientSocket));  // Multithreading
}
```

**WebSocket Server (Port 8080):**

```java
ServerSocket serverSocket = new ServerSocket(8080);  // TCP listener
Socket clientSocket = serverSocket.accept();  // TCP handshake
// Upgrade to WebSocket protocol
```

### URI/REST Design

**Resource-oriented endpoints:**

```
Resource: Board Collection
URI: /api/boards
Methods: GET (list), POST (create)

Resource: Specific Board
URI: /api/boards/{id}
Methods: GET (read), DELETE (delete)
```

### Multithreading with Thread Pool

**Why Thread Pool?**

- Efficient resource management
- Handles multiple clients concurrently
- Prevents thread exhaustion
- Better than creating new thread per request

```java
ExecutorService threadPool = Executors.newFixedThreadPool(10);

// Each request gets a thread from pool
threadPool.execute(() -> {
    handleRequest(clientSocket);  // Process in separate thread
});
```

**Room-based Architecture:**

```java
// Multiple rooms, each with own client set and history
Map<String, WhiteboardRoom> rooms = new ConcurrentHashMap<>();

// Thread-safe operations
room.addClient(socket, username);  // Concurrent access
room.broadcastMessage(message);    // Thread-safe broadcast
```

### NIO (Non-blocking I/O)

**AsynchronousFileChannel Example:**

```java
// Opens file without blocking
AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
    filePath,
    StandardOpenOption.WRITE,
    StandardOpenOption.CREATE
);

// Write asynchronously with callback
fileChannel.write(buffer, 0, null, new CompletionHandler<Integer, Object>() {
    @Override
    public void completed(Integer result, Object attachment) {
        // Success! File written without blocking thread
    }

    @Override
    public void failed(Throwable exc, Object attachment) {
        // Handle error
    }
});
```

**Benefits:**

- Thread not blocked waiting for I/O
- Can handle more concurrent operations
- Better scalability
- Event-driven architecture

---

## 🎯 Answers to Your Questions

### Q1: What format are we exporting?

**A: JSON format** - Easy to read, parse, and compatible with web standards.

### Q2: How do we import it?

**A: Via HTTP REST API:**

1. GET /api/boards → See list of all saved boards
2. Select board from list
3. GET /api/boards/{id} → Load that board's JSON data
4. Frontend parses JSON and renders strokes/shapes
5. WebSocket connects to room for real-time collaboration

### Q3: Can we edit after importing?

**A: YES! Absolutely!**

- All drawing tools work: draw, shapes, drag, resize, fill
- Changes are real-time synced via WebSocket
- Can save again to update the board
- Multiple users can collaborate on the loaded board

### Q4: Listing boards shows all boards from all clients?

**A: YES!**

- No authentication = no user isolation
- All boards are shared/public
- Anyone can see and load any board
- Good for team collaboration
- (If you want private boards, you'd need to add auth later)

---

## 🚀 Running the System

### Step 1: Start HTTP REST Server

```bash
cd board.com-collaborative-whiteboard-with-java-networking
mvn compile
mvn exec:java -Dexec.mainClass="org.example.server.HTTPRestServer"
```

**Output:**

```
Starting HTTP REST Server on port 8081
Endpoints:
  POST   http://localhost:8081/api/boards       - Save a board
  GET    http://localhost:8081/api/boards       - List all boards
  GET    http://localhost:8081/api/boards/{id}  - Load a board by ID
  DELETE http://localhost:8081/api/boards/{id}  - Delete a board
```

### Step 2: Start WebSocket Server (with Rooms)

```bash
mvn exec:java -Dexec.mainClass="org.example.server.WebSocketWhiteboardServerRooms"
```

**Output:**

```
Starting Whiteboard WebSocket Server (Room-based) on port 8080
React frontend should connect to: ws://localhost:8080
To join a specific room: ws://localhost:8080?room=ROOM_ID
```

### Step 3: Start React Frontend

```bash
cd board.com-front-end
npm run dev
```

### Step 4: Test the System

**Test Save:**

1. Draw something
2. Click "Save Board" (you'll need to add this button)
3. Check `saved_boards/` folder for JSON file

**Test Load:**

1. Click "Load Board"
2. See list of all saved boards
3. Select one
4. Board loads into canvas
5. Continue editing!

---

## 📝 Next Steps: Frontend Integration

You'll need to add UI components to the React app:

1. **Save Button**
2. **Load Button + Board List Dialog**
3. **API calls to REST server**
4. **Room-based WebSocket connection**

Would you like me to create those frontend components next?

---

## 🎓 What You Can Explain in Your Project Report

1. **TCP**: "I implemented TCP-based HTTP REST API and WebSocket servers listening on ports 8081 and 8080"

2. **URI**: "RESTful URIs for resource management: /api/boards for collection, /api/boards/{id} for specific resources"

3. **Multithreading**: "Used ExecutorService thread pool to handle multiple concurrent client requests efficiently"

4. **NIO**: "Implemented AsynchronousFileChannel for non-blocking file I/O operations with CompletionHandler callbacks"

5. **Rooms**: "Room-based architecture allows multiple boards to exist simultaneously with isolated collaboration spaces"

---

## 📊 Network Programming Principles Summary

| Principle          | Implementation              | Location                                    |
| ------------------ | --------------------------- | ------------------------------------------- |
| **TCP**            | ServerSocket listening      | HTTPRestServer.java line 30                 |
| **URI**            | REST endpoints              | HTTPRestServer.java handleRoute()           |
| **Multithreading** | ExecutorService thread pool | HTTPRestServer.java line 23                 |
| **NIO**            | AsynchronousFileChannel     | BoardStorageService.java line 48            |
| **Concurrency**    | ConcurrentHashMap for rooms | WebSocketWhiteboardServerRooms.java line 26 |

---

## ✅ Checklist

- [x] Model classes (Board, StrokeData, ShapeData, DrawPoint)
- [x] Storage service with NIO (BoardStorageService)
- [x] HTTP REST server with thread pool (HTTPRestServer)
- [x] Room-based WebSocket server (WebSocketWhiteboardServerRooms)
- [x] Room management (WhiteboardRoom)
- [ ] Frontend save/load UI (TODO)
- [ ] Frontend API integration (TODO)
- [ ] Testing (TODO)

---

Need help with the frontend integration? Let me know!
