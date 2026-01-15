package com.example.guandan.controller;

import com.example.guandan.entity.User;
import com.example.guandan.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Query the server-side snapshot of a user's current in-game state.
     *
     * This is persisted in MySQL (user.current_*), and updated when joining a room
     * and after each valid move.
     */
    @GetMapping("/user_current_state/{username}")
    public Map<String, Object> currentState(@PathVariable String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            return Map.of("error", "User not found");
        }
        return Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "currentRoomId", user.getCurrentRoomId(),
                "currentSeat", user.getCurrentSeat(),
                "currentScore", user.getCurrentScore(),
                "currentLevel", user.getCurrentLevel()
        );
    }
}
