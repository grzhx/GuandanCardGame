package com.example.guandan.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    /** BCrypt hash. */
    private String password;

    /** Best-effort snapshot of the user's current active room (nullable). */
    private String currentRoomId;
    private Integer currentSeat;
    private Integer currentScore;
    private Integer currentLevel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
