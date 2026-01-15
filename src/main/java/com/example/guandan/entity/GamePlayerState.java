package com.example.guandan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GamePlayerState {
    private Long id;
    private String roomId;
    private Integer seat;
    private Long userId;
    private Integer score;
    private Integer level;
    private Integer handCount;
    private Integer finishedRank;
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
}
