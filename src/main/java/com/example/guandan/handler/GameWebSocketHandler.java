package com.example.guandan.handler;

import com.example.guandan.entity.PlayerStats;
import com.example.guandan.model.Card;
import com.example.guandan.model.GameRoom;
import com.example.guandan.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {
    
    private final RoomService roomService;
    private final GameService gameService;
    private final UserService userService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        log.info("WS Received: {}", payload);
        Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
        
        String type = (String) msg.get("type");
        
        if ("ping".equals(type)) {
            sendMessage(session, Map.of("type", "pong"));
            return;
        }
        
        String action = (String) msg.get("action");
        
        if ("get_data".equals(action)) {
            handleGetData(session, msg);
        } else if (msg.containsKey("roomId")) {
            handleRoomAction(session, msg);
        } else if (msg.containsKey("state")) {
            handleReadyState(session, msg);
        } else if (msg.containsKey("cards")) {
            handlePlayCards(session, msg);
        }
    }
    
    private void handleGetData(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = (String) msg.get("username");
        var user = userService.findByUsername(username);
        
        if (user == null) {
            sendMessage(session, Map.of("error", "User not found"));
            return;
        }
        
        PlayerStats stats = userService.getPlayerStats(user.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("rate", stats.getWinRate().doubleValue());
        response.put("game_num", stats.getTotalGames());
        response.put("score", stats.getTotalScore());
        response.put("game_list", new ArrayList<>());
        
        sendMessage(session, response);
    }
    
    private void handleRoomAction(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = (String) msg.get("roomId");
        String type = (String) msg.get("type");
        String username = (String) msg.get("username");
        
        var user = userService.findByUsername(username);
        if (user == null) {
            user = userService.register(username, "default");
        }
        
        if (roomId == null || roomId.isEmpty()) {
            String gameType = "SINGLE".equals(type) ? "SINGLE" : "MULTIPLE";
            int level = msg.containsKey("level") ? (Integer) msg.get("level") : 2;
            GameRoom room = roomService.createRoom(gameType, level, user.getId());
            roomService.addPlayer(room.getRoomId(), user.getId(), username);
            sessionToRoom.put(session.getId(), room.getRoomId());
            
            sendMessage(session, Map.of("token", room.getRoomId()));
        } else {
            GameRoom room = roomService.getRoom(roomId);
            if (room == null) {
                sendMessage(session, Map.of("code", 3001, "msg", "Room not found"));
                return;
            }
            
            if (roomService.isRoomFull(roomId)) {
                sendMessage(session, Map.of("code", 3001, "msg", "Room is full"));
                return;
            }
            
            roomService.addPlayer(roomId, user.getId(), username);
            sessionToRoom.put(session.getId(), roomId);
            broadcastRoomInfo(roomId);
            
            if (roomService.allPlayersReady(roomId)) {
                room.setFirstPlayer(new Random().nextInt(4));
                gameService.initGame(room);
                roomService.saveRoom(room);
                broadcastToRoom(roomId, Map.of("game_state", true));
            }
        }
    }
    
    private void handleReadyState(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        if (roomId == null) return;
        
        GameRoom room = roomService.getRoom(roomId);
        boolean ready = (Boolean) msg.get("state");
        
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] != null && sessions.get(session.getId()) != null) {
                room.getPlayers()[i].setReady(ready);
                break;
            }
        }
        
        roomService.saveRoom(room);
        broadcastRoomInfo(roomId);
        
        if (roomService.allPlayersReady(roomId) && !room.isStarted()) {
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);
            broadcastToRoom(roomId, Map.of("game_state", true));
            triggerAgentIfNeeded(roomId);
        }
    }
    
    private void handlePlayCards(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        if (roomId == null) return;
        
        GameRoom room = roomService.getRoom(roomId);
        List<Map<String, Object>> cardMaps = (List<Map<String, Object>>) msg.get("cards");
        List<Card> cards = cardMaps.stream()
            .map(m -> new Card((String) m.get("color"), (Integer) m.get("number")))
            .collect(java.util.stream.Collectors.toList());
        
        int seat = findPlayerSeat(room, session.getId());
        if (seat < 0 || room.getCurrentPlayer() != seat) return;
        
        if (gameService.playCards(room, seat, cards)) {
            roomService.saveRoom(room);
            broadcastGameState(roomId);
            triggerAgentIfNeeded(roomId);
        }
    }
    
    private int findPlayerSeat(GameRoom room, String sessionId) {
        for (Map.Entry<String, String> entry : sessionToRoom.entrySet()) {
            if (entry.getKey().equals(sessionId)) {
                for (int i = 0; i < 4; i++) {
                    if (room.getPlayers()[i] != null && !room.getPlayers()[i].isAgent()) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }
    
    private void triggerAgentIfNeeded(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null || room.isFinished()) return;
        
        int current = room.getCurrentPlayer();
        GameRoom.Player player = room.getPlayers()[current];
        
        if (player != null && player.isAgent()) {
            List<Card> cards = agentService.decidePlay(room, current);
            log.info("Agent {} plays: {}", current, cards);
            
            if (gameService.playCards(room, current, cards)) {
                roomService.saveRoom(room);
                broadcastGameState(roomId);
                triggerAgentIfNeeded(roomId);
            }
        }
    }
    
    private void broadcastGameState(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        Map<String, Object> state = new HashMap<>();
        state.put("currentPlayer", room.getCurrentPlayer());
        state.put("lastPattern", room.getLastPattern());
        state.put("finished", room.isFinished());
        broadcastToRoom(roomId, state);
    }
    
    private void broadcastRoomInfo(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("roomId", roomId);
        roomInfo.put("hostId", room.getHostId());
        
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> playerInfo = new HashMap<>();
            playerInfo.put("seat", i);
            if (room.getPlayers()[i] != null) {
                playerInfo.put("uid", room.getPlayers()[i].getUserId());
                playerInfo.put("ready", room.getPlayers()[i].isReady());
                playerInfo.put("online", room.getPlayers()[i].isOnline());
            } else {
                playerInfo.put("uid", 0);
                playerInfo.put("ready", false);
                playerInfo.put("online", false);
            }
            players.add(playerInfo);
        }
        roomInfo.put("players", players);
        
        broadcastToRoom(roomId, roomInfo);
    }
    
    private void broadcastToRoom(String roomId, Map<String, Object> message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        log.info("WS Broadcast to room {}: {}", roomId, json);
        for (Map.Entry<String, String> entry : sessionToRoom.entrySet()) {
            if (roomId.equals(entry.getValue())) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        }
    }
    
    private void sendMessage(WebSocketSession session, Map<String, Object> message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        log.info("WS Sent: {}", json);
        session.sendMessage(new TextMessage(json));
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        sessionToRoom.remove(session.getId());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        sessions.remove(session.getId());
        sessionToRoom.remove(session.getId());
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
