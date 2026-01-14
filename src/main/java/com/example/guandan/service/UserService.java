package com.example.guandan.service;

import com.example.guandan.entity.User;
import com.example.guandan.entity.PlayerStats;
import com.example.guandan.mapper.UserMapper;
import com.example.guandan.mapper.PlayerStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    private final PlayerStatsMapper playerStatsMapper;
    
    @Transactional
    public User register(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        userMapper.insert(user);
        
        PlayerStats stats = new PlayerStats();
        stats.setUserId(user.getId());
        stats.setTotalGames(0);
        stats.setTotalWins(0);
        stats.setTotalScore(0);
        stats.setWinRate(BigDecimal.ZERO);
        playerStatsMapper.insert(stats);
        
        return user;
    }
    
    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
    
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
    
    public PlayerStats getPlayerStats(Long userId) {
        return playerStatsMapper.findByUserId(userId);
    }
    
    @Transactional
    public void updateStats(Long userId, boolean won, int scoreChange) {
        PlayerStats stats = playerStatsMapper.findByUserId(userId);
        if (stats == null) {
            stats = new PlayerStats();
            stats.setUserId(userId);
            stats.setTotalGames(0);
            stats.setTotalWins(0);
            stats.setTotalScore(0);
        }
        
        stats.setTotalGames(stats.getTotalGames() + 1);
        if (won) {
            stats.setTotalWins(stats.getTotalWins() + 1);
        }
        stats.setTotalScore(stats.getTotalScore() + scoreChange);
        stats.setWinRate(BigDecimal.valueOf(stats.getTotalWins() * 100.0 / stats.getTotalGames()));
        
        playerStatsMapper.update(stats);
    }
}
