package com.example.guandan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GameSession {
    private String roomId;
    private String gameType;
    private Integer level;
    private String status; // WAITING/STARTED/FINISHED
    private Long hostId;
    private Integer firstPlayer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
