package com.example.guandan.controller;

import com.example.guandan.model.Card;
import com.example.guandan.model.GameRoom;
import com.example.guandan.service.GameService;
import com.example.guandan.service.GameStateService;
import com.example.guandan.service.RoomService;
import com.example.guandan.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class GameController {

    private final RoomService roomService;
    private final GameService gameService;
    private final UserService userService;
    private final GameStateService gameStateService;

    /**
     * Create a new room.
     *
     * Request:
     * {"level":2, "gameType":"SINGLE"}
     */
    @PostMapping("/new_game")
    public Map<String, Object> newGame(@RequestBody Map<String, Object> request) {
        int level = asInt(request.getOrDefault("level", 2));
        String gameType = String.valueOf(request.getOrDefault("gameType", "SINGLE"));

        GameRoom room = roomService.createRoom(gameType, level, null);
        // Persist a lightweight session record (best-effort)
        try {
            gameStateService.createSession(room);
        } catch (Exception ignored) {}
        return Map.of("token", room.getRoomId());
    }

    /**
     * Join an existing room. When the room becomes full, the game starts automatically.
     */
    @PostMapping("/join_game/{token}")
    public Map<String, Object> joinGame(@PathVariable String token, @RequestBody Map<String, String> request) {
        String username = request.get("username");
        if (username == null || username.isBlank()) {
            return Map.of("error", "username required");
        }

        var user = userService.findByUsername(username);
        if (user == null) {
            user = userService.register(username, "default");
        }

        int seat = roomService.addPlayer(token, user.getId(), username);
        if (seat < 0) return Map.of("error", "Room not found or full");

        GameRoom room = roomService.getRoom(token);

        // Persist player join + current snapshot
        try {
            gameStateService.addPlayer(token, user, seat, room == null ? 2 : room.getLevel());
        } catch (Exception ignored) {}

        if (roomService.isRoomFull(token) && room != null && !room.isStarted()) {
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);

            try {
                gameStateService.markStarted(room);
            } catch (Exception ignored) {}
        }

        return Map.of("player_number", seat);
    }

    /**
     * Play cards or PASS (empty cards).
     *
     * Body example:
     * {"cards":[{"id":"...","color":"Spade","number":3}]}
     * PASS:
     * {"cards":[]}
     */
    @PostMapping("/play_cards/{token}/{player_id}")
    public Map<String, Object> playCards(@PathVariable String token,
                                         @PathVariable("player_id") int playerId,
                                         @RequestBody Map<String, Object> request) {
        GameRoom room = roomService.getRoom(token);
        if (room == null) return Map.of("success", false, "error", "Room not found");

        List<Card> cards = parseCards(request.get("cards"));
        boolean ok = gameService.playCards(room, playerId, cards);
        if (ok) {
            roomService.saveRoom(room);
            try {
                gameStateService.syncPlayer(room, playerId);
                if (room.isFinished()) {
                    gameStateService.markFinished(room);
                }
            } catch (Exception ignored) {}
            return Map.of("success", true);
        }
        return Map.of("success", false, "error", "Invalid move");
    }

    @GetMapping("/get_game_state/{token}")
    public Map<String, Object> getGameState(@PathVariable String token) {
        GameRoom room = roomService.getRoom(token);
        if (room == null) return Map.of("error", "Room not found");

        Map<String, Object> response = new HashMap<>();
        if (room.isFinished()) {
            response.put("finished", true);
            response.put("rank", room.getRanks());
            return response;
        }

        response.put("current_player", room.getCurrentPlayer());
        response.put("started", room.isStarted());
        response.put("finished", false);
        return response;
    }

    /**
     * Polling endpoint for a player's view.
     * Compatible with the original front-end fields, but now includes card id.
     */
    @GetMapping("/get_player_game_state/{token}/{player_id}")
    public Map<String, Object> getPlayerGameState(@PathVariable String token, @PathVariable("player_id") int playerId) {
        GameRoom room = roomService.getRoom(token);
        if (room == null) return Map.of("error", "Room not found");

        Map<String, Object> response = new HashMap<>();

        if (room.isFinished()) {
            response.put("finished", true);
            response.put("rank", room.getRanks());
            return response;
        }

        if (room.isPaused()) {
            response.put("started", true);
            response.put("paused", true);
            response.put("player_comp", formatComp(room));
            return response;
        }

        response.put("turn", room.getCurrentPlayer());
        response.put("deck", formatCards(room.getPlayers()[playerId].getHand()));
        response.put("comp", formatComp(room));
        response.put("started", room.isStarted());
        response.put("player_comp", formatComp(room));
        response.put("finished_players", room.getFinishedPlayers());
        response.put("finished", false);
        response.put("paused", false);
        return response;
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

    private List<Object> formatComp(GameRoom room) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Card> cards = room.getCurrentRoundCards().get(i);
            if (cards == null) {
                result.add(null);
            } else if (cards.isEmpty()) {
                result.add(Collections.emptyList()); // PASS
            } else {
                result.add(formatCards(cards));
            }
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

            String id = m.get("id") == null ? null : String.valueOf(m.get("id"));
            String color = m.get("color") == null ? null : String.valueOf(m.get("color"));
            int number = asInt(m.get("number"));

            Card c = new Card();
            c.setId(id);
            c.setColor(color);
            c.setNumber(number);
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
}
