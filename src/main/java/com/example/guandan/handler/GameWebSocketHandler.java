package com.example.guandan.handler;

import com.example.guandan.entity.PlayerStats;
import com.example.guandan.model.Card;
import com.example.guandan.model.GameRoom;
import com.example.guandan.service.GameService;
import com.example.guandan.service.GameStateService;
import com.example.guandan.service.RoomService;
import com.example.guandan.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {

    private final RoomService roomService;
    private final GameService gameService;
    private final UserService userService;
    private final GameStateService gameStateService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<String, Integer> sessionToSeat = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        Map<String, Object> msg = objectMapper.readValue(payload, Map.class);

        String type = (String) msg.get("type");
        if ("ping".equals(type)) {
            sendMessage(session, Map.of("type", "pong"));
            return;
        }

        String action = (String) msg.get("action");
        if ("get_data".equals(action)) {
            handleGetData(session, msg);
            return;
        }

        // room create/join
        if (msg.containsKey("roomId")) {
            handleRoomAction(session, msg);
            return;
        }

        // ready
        if (msg.containsKey("state")) {
            handleReadyState(session, msg);
            return;
        }

        // play/pass
        if ("play_cards".equals(action) || "pass".equals(action) || msg.containsKey("cards")) {
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

        // create
        if (roomId == null || roomId.isBlank()) {
            String gameType = "SINGLE".equals(type) ? "SINGLE" : "MULTIPLE";
            int level = msg.containsKey("level") ? asInt(msg.get("level")) : 2;

            GameRoom room = roomService.createRoom(gameType, level, user.getId());
            int seat = roomService.addPlayer(room.getRoomId(), user.getId(), username);

            // DB mirror (best-effort)
            try {
                gameStateService.createSession(room);
                gameStateService.addPlayer(room.getRoomId(), user, seat, room.getLevel());
            } catch (Exception ignored) {}

            sessionToRoom.put(session.getId(), room.getRoomId());
            sessionToSeat.put(session.getId(), seat);

            sendMessage(session, Map.of("token", room.getRoomId(), "seat", seat));
            broadcastRoomInfo(room.getRoomId());
            return;
        }

        // join
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) {
            sendMessage(session, Map.of("code", 3001, "msg", "Room not found"));
            return;
        }
        if (roomService.isRoomFull(roomId)) {
            sendMessage(session, Map.of("code", 3001, "msg", "Room is full"));
            return;
        }

        int seat = roomService.addPlayer(roomId, user.getId(), username);
        sessionToRoom.put(session.getId(), roomId);
        sessionToSeat.put(session.getId(), seat);

        // DB mirror (best-effort)
        try {
            gameStateService.addPlayer(roomId, user, seat, room.getLevel());
        } catch (Exception ignored) {}

        broadcastRoomInfo(roomId);

        // auto-start when everyone ready
        room = roomService.getRoom(roomId);
        if (room != null && roomService.allPlayersReady(roomId) && !room.isStarted()) {
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);
            broadcastToRoom(roomId, Map.of("type", "game_state", "started", true));
            try { gameStateService.markStarted(room); } catch (Exception ignored) {}
        }

        sendMessage(session, Map.of("type", "joined", "seat", seat));
    }

    private void handleReadyState(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        Integer seat = sessionToSeat.get(session.getId());
        if (roomId == null || seat == null) return;

        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;

        boolean ready = Boolean.TRUE.equals(msg.get("state"));

        if (room.getPlayers()[seat] != null) {
            room.getPlayers()[seat].setReady(ready);
        }

        roomService.saveRoom(room);
        broadcastRoomInfo(roomId);

        if (roomService.allPlayersReady(roomId) && !room.isStarted()) {
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);
            broadcastToRoom(roomId, Map.of("type", "game_state", "started", true));
            try { gameStateService.markStarted(room); } catch (Exception ignored) {}
        }
    }

    private void handlePlayCards(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        Integer seat = sessionToSeat.get(session.getId());
        if (roomId == null || seat == null) return;

        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;

        String action = (String) msg.get("action");
        List<Card> cards = "pass".equals(action) ? List.of() : parseCards(msg.get("cards"));

        boolean ok = gameService.playCards(room, seat, cards);
        if (!ok) {
            sendMessage(session, Map.of("type", "error", "msg", "Invalid move"));
            return;
        }

        roomService.saveRoom(room);

        // DB mirror (best-effort)
        try {
            gameStateService.syncPlayer(room, seat);
            if (room.isFinished()) {
                gameStateService.markFinished(room);
            }
        } catch (Exception ignored) {}

        // broadcast updated turn + table
        Map<String, Object> update = new HashMap<>();
        update.put("type", "game_update");
        update.put("turn", room.getCurrentPlayer());
        update.put("finished", room.isFinished());
        update.put("finished_players", room.getFinishedPlayers());
        update.put("comp", formatComp(room));

        if (room.isFinished()) {
            update.put("rank", room.getRanks());
        }

        broadcastToRoom(roomId, update);
    }

    private void broadcastRoomInfo(String roomId) throws Exception {
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) return;

        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("type", "room_info");
        roomInfo.put("roomId", roomId);
        roomInfo.put("hostId", room.getHostId());
        roomInfo.put("started", room.isStarted());

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
                playerInfo.put("ready", false);
                playerInfo.put("online", false);
            }
            players.add(playerInfo);
        }
        roomInfo.put("players", players);

        broadcastToRoom(roomId, roomInfo);
    }

    private List<Object> formatComp(GameRoom room) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Card> cards = room.getCurrentRoundCards().get(i);
            if (cards == null) {
                result.add(null);
            } else if (cards.isEmpty()) {
                result.add(Collections.emptyList());
            } else {
                result.add(formatCards(cards));
            }
        }
        return result;
    }

    private List<Map<String, Object>> formatCards(List<Card> cards) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Card card : cards) {
            Map<String, Object> cardMap = new HashMap<>();
            cardMap.put("id", card.getId());
            cardMap.put("color", card.getColor());
            cardMap.put("number", card.getNumber());
            cardMap.put("selected", card.isSelected());
            result.add(cardMap);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Card> parseCards(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> rawList)) return List.of();

        List<Card> result = new ArrayList<>();
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) continue;

            Card c = new Card();
            if (m.get("id") != null) c.setId(String.valueOf(m.get("id")));
            if (m.get("color") != null) c.setColor(String.valueOf(m.get("color")));
            c.setNumber(asInt(m.get("number")));
            c.setSelected(Boolean.TRUE.equals(m.get("selected")));
            result.add(c);
        }
        return result;
    }

    private int asInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        for (Map.Entry<String, String> entry : sessionToRoom.entrySet()) {
            if (roomId.equals(entry.getValue())) {
                WebSocketSession s = sessions.get(entry.getKey());
                if (s != null && s.isOpen()) {
                    s.sendMessage(new TextMessage(json));
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) throws Exception {
        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        sessionToRoom.remove(session.getId());
        sessionToSeat.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        sessions.remove(session.getId());
        sessionToRoom.remove(session.getId());
        sessionToSeat.remove(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
