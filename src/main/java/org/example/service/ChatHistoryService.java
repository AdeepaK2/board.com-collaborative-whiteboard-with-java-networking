package org.example.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to manage chat history for different rooms
 */

public class ChatHistoryService {
    private final Map<String, List<String>> roomChats = new HashMap<>();

    public void addMessage(String roomId, String message) {
        roomChats.computeIfAbsent(roomId, k -> new ArrayList<>()).add(message);
    }

    public List<String> getHistory(String roomId) {
        return roomChats.getOrDefault(roomId, new ArrayList<>());
    }
}
