package com.example.guandan.controller;

import com.example.guandan.model.*;
import com.example.guandan.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class GameController {
    
    private final RoomService roomService;
    private final GameService gameService;
    private final UserService userService;
    
    @PostMapping("/new_game")
    public Map<String, Object> newGame(@RequestBody Map<String, Object> request) {
        int level = (Integer) request.get("level");
        GameRoom room = roomService.createRoom("SINGLE", level, null);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", room.getRoomId());
        return response;
    }
    
    @PostMapping("/join_game/{token}")
    public Map<String, Object> joinGame(@PathVariable String token, @RequestBody Map<String, String> request) {
        String username = request.get("username");
        
        var user = userService.findByUsername(username);
        if (user == null) {
            user = userService.register(username, "default");
        }
        
        int seat = roomService.addPlayer(token, user.getId(), username);
        
        Map<String, Object> response = new HashMap<>();
        response.put("player_number", seat);
        
        GameRoom room = roomService.getRoom(token);
        if (roomService.isRoomFull(token) && !room.isStarted()) {
            room.setFirstPlayer(new Random().nextInt(4));
            gameService.initGame(room);
            roomService.saveRoom(room);
        }
        
        return response;
    }
    
    @GetMapping("/get_game_state/{token}")
    public Map<String, Object> getGameState(@PathVariable String token) {
        GameRoom room = roomService.getRoom(token);
        Map<String, Object> response = new HashMap<>();
        
        if (room.isFinished()) {
            response.put("finished", true);
            StringBuilder rank = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (room.getRanks()[j] == i + 1) {
                        rank.append("[").append(i + 1).append("] Player ").append(room.getPlayers()[j].getUsername()).append("\n");
                    }
                }
            }
            response.put("rank", rank.toString());
        } else {
            response.put("current_player", room.getCurrentPlayer());
            response.put("started", room.isStarted());
            
            StringBuilder gameState = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (room.getPlayers()[i] != null) {
                    gameState.append(i).append(": ");
                    for (Card card : room.getPlayers()[i].getHand()) {
                        gameState.append(card.getNumber()).append(" of ").append(card.getColor()).append(", ");
                    }
                }
            }
            response.put("game_state", gameState.toString());
        }
        
        return response;
    }
    
    @GetMapping("/get_player_game_state/{token}/{player_id}")
    public Map<String, Object> getPlayerGameState(@PathVariable String token, @PathVariable int player_id) {
        GameRoom room = roomService.getRoom(token);
        Map<String, Object> response = new HashMap<>();
        
        if (room.isFinished()) {
            response.put("finished", true);
            response.put("rank", room.getRanks());
            return response;
        }
        
        if (room.isPaused()) {
            response.put("started", true);
            response.put("paused", true);
            response.put("player_comp", formatPlayerComp(room));
            return response;
        }
        
        response.put("turn", room.getCurrentPlayer());
        response.put("deck", formatCards(room.getPlayers()[player_id].getHand()));
        response.put("comp", formatComp(room));
        response.put("started", room.isStarted());
        response.put("player_comp", formatPlayerComp(room));
        response.put("finished_players", room.getFinishedPlayers());
        response.put("tribute_state", room.getTributeState());
        response.put("finished", false);
        response.put("paused", false);
        
        return response;
    }
    
    private List<Map<String, Object>> formatCards(List<Card> cards) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Card card : cards) {
            Map<String, Object> cardMap = new HashMap<>();
            cardMap.put("color", card.getColor());
            cardMap.put("number", card.getNumber());
            result.add(cardMap);
        }
        return result;
    }
    
    private List<Object> formatComp(GameRoom room) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Card> cards = room.getCurrentRoundCards().get(i);
            if (cards == null || cards.isEmpty()) {
                result.add(null);
            } else {
                result.add(formatCards(cards));
            }
        }
        return result;
    }
    
    private List<Object> formatPlayerComp(GameRoom room) {
        return formatComp(room);
    }
}
