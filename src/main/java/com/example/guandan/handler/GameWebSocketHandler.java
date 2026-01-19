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
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private final Map<String, String> sessionToUsername = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToRoom = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<MatchPlayer> matchQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> matchingUsers = ConcurrentHashMap.newKeySet();
    
    // 匹配玩家信息
    private static class MatchPlayer {
        final WebSocketSession session;
        final String username;
        final Long userId;
        
        MatchPlayer(WebSocketSession session, String username, Long userId) {
            this.session = session;
            this.username = username;
            this.userId = userId;
        }
    }
    
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
        } else if ("get_cards".equals(action)) {
            handleGetCards(session, msg);
        } else if ("get_last_combo".equals(action)) {
            handleGetLastCombo(session, msg);
        } else if ("get_turn".equals(action)) {
            handleGetTurn(session, msg);
        } else if ("get_history".equals(action)) {
            handleGetHistory(session, msg);
        } else if ("play_cards".equals(action)) {
            handlePlayCards(session, msg);
        } else if ("matchmaking".equals(action)) {
            handleMatchmaking(session, msg);
        } else if ("cancel_matchmaking".equals(action)) {
            handleCancelMatchmaking(session, msg);
        } else if (msg.containsKey("roomId")) {
            handleRoomAction(session, msg);
        } else if (msg.containsKey("state")) {
            handleReadyState(session, msg);
        } else if (msg.containsKey("cards")) {
            handlePlayCards(session, msg);
        }
    }
    
    private void handleGetCards(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = (String) msg.get("username");
        if (username == null) {
            username = sessionToUsername.get(session.getId());
        }
        if (username == null) {
            log.warn("get_cards: session {} has no username", session.getId());
            sendMessage(session, Map.of("error", "Username not found"));
            return;
        }
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) {
            log.warn("get_cards: username {} not in any room", username);
            sendMessage(session, Map.of("error", "Not in a room"));
            return;
        }
        
        GameRoom room = roomService.getRoom(roomId);
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] != null && username.equals(room.getPlayers()[i].getUsername())) {
                sendMessage(session, Map.of("cards", room.getPlayers()[i].getHand(), "username", username));
                return;
            }
        }
    }
    
    private void handleGetLastCombo(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = sessionToUsername.get(session.getId());
        if (username == null) {
            log.warn("get_last_combo: session {} has no username", session.getId());
            sendMessage(session, Map.of("error", "Username not found"));
            return;
        }
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) {
            log.warn("get_last_combo: username {} not in any room", username);
            sendMessage(session, Map.of("error", "Not in a room"));
            return;
        }
        
        GameRoom room = roomService.getRoom(roomId);
        Map<String, Object> response = new HashMap<>();
        if (room.getLastPattern() != null && room.getPassCount() < 3) {
            response.put("cards", room.getLastPattern().getCards());
            response.put("pattern", room.getLastPattern().getType().name());
        } else {
            response.put("cards", new ArrayList<>());
            response.put("pattern", null);
        }
        sendMessage(session, response);
    }
    
    private void handleGetTurn(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = sessionToUsername.get(session.getId());
        if (username == null) {
            log.warn("get_turn: session {} has no username", session.getId());
            sendMessage(session, Map.of("error", "Username not found"));
            return;
        }
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) {
            log.warn("get_turn: username {} not in any room", username);
            sendMessage(session, Map.of("error", "Not in a room"));
            return;
        }
        
        GameRoom room = roomService.getRoom(roomId);
        sendMessage(session, Map.of("seat", room.getCurrentPlayer()));
    }
    
    private void handleGetHistory(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = sessionToUsername.get(session.getId());
        if (username == null) {
            log.warn("get_history: session {} has no username", session.getId());
            sendMessage(session, Map.of("error", "Username not found"));
            return;
        }
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) {
            log.warn("get_history: username {} not in any room", username);
            sendMessage(session, Map.of("error", "Not in a room"));
            return;
        }
        
        GameRoom room = roomService.getRoom(roomId);
        String gameKey = "game" + room.getCurrentGameIndex();
        List<Map<String, Object>> history = room.getGameHistory().getOrDefault(gameKey, new ArrayList<>());
        sendMessage(session, Map.of(gameKey, history));
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
    
    private void handleMatchmaking(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = (String) msg.get("username");
        
        // 验证用户
        var user = userService.findByUsername(username);
        if (user == null) {
            user = userService.register(username, "default");
        }
        
        // 检查用户是否已在房间中
        if (usernameToRoom.containsKey(username)) {
            sendMessage(session, Map.of("error", "Already in a room"));
            return;
        }
        
        // 检查用户是否已在匹配队列中
        if (matchingUsers.contains(username)) {
            sendMessage(session, Map.of("error", "Already in matchmaking queue"));
            return;
        }
        
        // 加入匹配队列
        MatchPlayer matchPlayer = new MatchPlayer(session, username, user.getId());
        matchQueue.offer(matchPlayer);
        matchingUsers.add(username);
        
        log.info("Player {} joined matchmaking queue, current queue size: {}", username, matchQueue.size());
        
        // 通知玩家进入队列
        sendMessage(session, Map.of(
            "type", "matchmaking",
            "status", "queued",
            "position", matchQueue.size()
        ));
        
        // 检查是否有足够的玩家开始匹配
        if (matchQueue.size() >= 4) {
            createMatchRoom();
        }
    }
    
    private void handleCancelMatchmaking(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = (String) msg.get("username");
        
        if (!matchingUsers.contains(username)) {
            sendMessage(session, Map.of("error", "Not in matchmaking queue"));
            return;
        }
        
        // 从队列中移除玩家
        matchQueue.removeIf(mp -> mp.username.equals(username));
        matchingUsers.remove(username);
        
        log.info("Player {} left matchmaking queue, current queue size: {}", username, matchQueue.size());
        
        sendMessage(session, Map.of(
            "type", "matchmaking",
            "status", "cancelled"
        ));
    }
    
    private synchronized void createMatchRoom() throws Exception {
        if (matchQueue.size() < 4) {
            return;
        }
        
        // 取出4个玩家
        List<MatchPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            MatchPlayer player = matchQueue.poll();
            if (player != null) {
                players.add(player);
                matchingUsers.remove(player.username);
            }
        }
        
        if (players.size() != 4) {
            // 如果没有足够的玩家，放回队列
            for (MatchPlayer player : players) {
                matchQueue.offer(player);
                matchingUsers.add(player.username);
            }
            return;
        }
        
        // 创建匹配房间
        int level = 2; // 默认等级
        GameRoom room = roomService.createMatchRoom(level);
        String roomId = room.getRoomId();
        
        log.info("Created match room {} for players: {}", roomId, 
            players.stream().map(p -> p.username).toArray());
        
        // 将4个玩家加入房间
        for (int i = 0; i < 4; i++) {
            MatchPlayer player = players.get(i);
            roomService.addPlayer(roomId, player.userId, player.username);
            sessionToUsername.put(player.session.getId(), player.username);
            usernameToRoom.put(player.username, roomId);
            
            // 设置玩家为已准备状态
            GameRoom updatedRoom = roomService.getRoom(roomId);
            updatedRoom.getPlayers()[i].setReady(true);
            roomService.saveRoom(updatedRoom);
        }
        
        // 通知所有玩家匹配成功
        for (MatchPlayer player : players) {
            if (player.session.isOpen()) {
                sendMessage(player.session, Map.of(
                    "type", "matchmaking",
                    "status", "matched",
                    "roomId", roomId
                ));
            }
        }
        
        // 广播房间信息
        broadcastRoomInfo(roomId);
        
        // 自动开始游戏
        GameRoom room2 = roomService.getRoom(roomId);
        room2.setFirstPlayer(new Random().nextInt(4));
        gameService.initGame(room2);
        roomService.saveRoom(room2);
        broadcastToRoom(roomId, Map.of("game_state", true));
        broadcastToRoom(roomId, Map.of("seat", room2.getCurrentPlayer()));
        notifyCurrentPlayer(roomId);
        triggerAgentIfNeeded(roomId);
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
            sessionToUsername.put(session.getId(), username);
            usernameToRoom.put(username, room.getRoomId());
            
            sendMessage(session, Map.of("token", room.getRoomId()));
        } else {
            // TEST 房间：创建单人测试房间
            if ("TEST".equals(roomId)) {
                GameRoom room = new GameRoom();
                room.setRoomId("TEST");
                room.setGameType("SINGLE");
                room.setLevel(2);
                room.setHostId(user.getId());
                room.getPlayers()[0] = new GameRoom.Player(user.getId(), username, 0);
                roomService.saveRoom(room);
                sessionToUsername.put(session.getId(), username);
                usernameToRoom.put(username, "TEST");
                broadcastRoomInfo("TEST");
                
                // 延迟3秒后开始游戏
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                room.setFirstPlayer(0);
                gameService.initGame(room);
                roomService.saveRoom(room);
                broadcastToRoom("TEST", Map.of("game_state", true));
                broadcastToRoom("TEST", Map.of("seat", room.getCurrentPlayer()));
                notifyCurrentPlayer("TEST");
                    } catch (Exception e) {
                        log.error("TEST room start error", e);
                    }
                }).start();
                return;
            }
            
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
            sessionToUsername.put(session.getId(), username);
            usernameToRoom.put(username, roomId);
            broadcastRoomInfo(roomId);
            
            if (roomService.allPlayersReady(roomId)) {
                room = roomService.getRoom(roomId);
                room.setFirstPlayer(new Random().nextInt(4));
                gameService.initGame(room);
                roomService.saveRoom(room);
                broadcastToRoom(roomId, Map.of("game_state", true));
            }
        }
    }
    
    private void handleReadyState(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = sessionToUsername.get(session.getId());
        if (username == null) return;
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) return;
        
        GameRoom room = roomService.getRoom(roomId);
        boolean ready = (Boolean) msg.get("state");
        
        int seat = findPlayerSeat(room, username);
        if (seat >= 0) {
            room.getPlayers()[seat].setReady(ready);
        }
        
        roomService.saveRoom(room);
        broadcastRoomInfo(roomId);
        
        if (roomService.allPlayersReady(roomId) && !room.isStarted()) {
            room = roomService.getRoom(roomId);
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);
            broadcastToRoom(roomId, Map.of("game_state", true));
            broadcastToRoom(roomId, Map.of("seat", room.getCurrentPlayer()));
            notifyCurrentPlayer(roomId);
            triggerAgentIfNeeded(roomId);
        }
    }
    
    private void handlePlayCards(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String username = sessionToUsername.get(session.getId());
        if (username == null) return;
        
        String roomId = usernameToRoom.get(username);
        if (roomId == null) return;
        
        GameRoom room = roomService.getRoom(roomId);
        List<Map<String, Object>> cardMaps = (List<Map<String, Object>>) msg.get("cards");
        List<Card> cards = cardMaps.stream()
            .map(m -> new Card((String) m.get("color"), (Integer) m.get("number")))
            .collect(java.util.stream.Collectors.toList());
        
        int seat = findPlayerSeat(room, username);
        if (seat < 0 || room.getCurrentPlayer() != seat) {
            sendMessage(session, Map.of("error", "Not your turn"));
            return;
        }
        
        boolean wasFinished = room.getFinishedPlayers().contains(seat);
        
        if (gameService.playCards(room, seat, cards)) {
            roomService.saveRoom(room);
            
            // 获取牌型信息
            String patternType = "PASS";
            if (room.getLastPattern() != null) {
                patternType = room.getLastPattern().getType().name();
            }
            
            // 广播时包含牌型
            Map<String, Object> broadcast = new HashMap<>();
            broadcast.put("seat", seat);
            broadcast.put("movement", cards);
            broadcast.put("pattern", patternType);
            broadcastToRoom(roomId, broadcast);
            
            if (!wasFinished && room.getFinishedPlayers().contains(seat)) {
                broadcastToRoom(roomId, Map.of(
                    "msg", "someone clear his hand",
                    "seat", seat,
                    "username", username
                ));
            }
            
            // 检测"接风"：三次过牌后轮到队友出牌
            if (room.getPassCount() == 0 && 
                room.getLastPattern() == null && 
                room.getLastPlayerId() != -1 &&
                room.getFinishedPlayers().contains(room.getLastPlayerId())) {
                
                int teammate = (room.getLastPlayerId() + 2) % 4;
                if (room.getCurrentPlayer() == teammate) {
                    broadcastToRoom(roomId, Map.of(
                        "msg", "turn change",
                        "seat", teammate
                    ));
                }
            }
            
            if (room.isFinished()) {
                broadcastRoundEnd(roomId);
                
                if (room.isGameOver()) {
                    broadcastGameOver(roomId);
                    return;
                }
            }
            
            notifyCurrentPlayer(roomId);
            triggerAgentIfNeeded(roomId);
        } else {
            sendMessage(session, Map.of("error", "Invalid play"));
        }
    }
    
    private int findPlayerSeat(GameRoom room, String username) {
        if (room == null || username == null) return -1;
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] != null && 
                username.equals(room.getPlayers()[i].getUsername())) {
                return i;
            }
        }
        return -1;
    }
    
    private void notifyCurrentPlayer(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null || room.isFinished()) return;
        
        int current = room.getCurrentPlayer();
        GameRoom.Player player = room.getPlayers()[current];
        if (player == null || player.isAgent()) return;
        
        String currentUsername = player.getUsername();
        for (Map.Entry<String, String> entry : sessionToUsername.entrySet()) {
            if (currentUsername.equals(entry.getValue())) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    sendMessage(session, Map.of("msg", "is your turn"));
                    return;
                }
            }
        }
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
                
                // 获取牌型信息
                String patternType = "PASS";
                if (room.getLastPattern() != null) {
                    patternType = room.getLastPattern().getType().name();
                }
                
                // 广播时包含牌型
                Map<String, Object> broadcast = new HashMap<>();
                broadcast.put("seat", current);
                broadcast.put("movement", cards);
                broadcast.put("pattern", patternType);
                broadcastToRoom(roomId, broadcast);
                
                notifyCurrentPlayer(roomId);
                triggerAgentIfNeeded(roomId);
            }
        }
    }
    
    private void broadcastRoundEnd(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        Map<String, Object> message = new HashMap<>();
        message.put("type", "game_end");
        message.put("ranks", room.getRanks());
        
        int[] scores = new int[4];
        for (int i = 0; i < 4; i++) {
            scores[i] = room.getPlayers()[i].getScore();
        }
        message.put("scores", scores);
        
        int team0Score = scores[0] + scores[2];
        int team1Score = scores[1] + scores[3];
        message.put("winner_team", team0Score > team1Score ? 0 : 1);
        message.put("final_level", room.getLevel());
        
        broadcastToRoom(roomId, message);
    }
    
    private void broadcastGameOver(String roomId) throws Exception {
        broadcastToRoom(roomId, Map.of("msg", "game end in this room"));
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
                playerInfo.put("username", room.getPlayers()[i].getUsername());
                playerInfo.put("ready", room.getPlayers()[i].isReady());
                playerInfo.put("online", room.getPlayers()[i].isOnline());
            } else {
                playerInfo.put("uid", 0);
                playerInfo.put("username", null);
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
        for (Map.Entry<String, String> entry : sessionToUsername.entrySet()) {
            String username = entry.getValue();
            if (roomId.equals(usernameToRoom.get(username))) {
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
        String username = sessionToUsername.remove(session.getId());
        if (username != null) {
            usernameToRoom.remove(username);
        }
        sessions.remove(session.getId());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String username = sessionToUsername.remove(session.getId());
        if (username != null) {
            usernameToRoom.remove(username);
        }
        sessions.remove(session.getId());
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
