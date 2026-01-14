package com.example.guandan.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class GameHistory {
    private Long id;
    private String roomId;
    private Long player0Id;
    private Long player1Id;
    private Long player2Id;
    private Long player3Id;
    private Integer winnerTeam;
    private String finalRank;
    private Integer scoreChange;
    private String gameType;
    private LocalDateTime createdAt;
}
