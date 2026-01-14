package com.example.guandan.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PlayerStats {
    private Long userId;
    private Integer totalGames;
    private Integer totalWins;
    private Integer totalScore;
    private BigDecimal winRate;
    private LocalDateTime updatedAt;
}
