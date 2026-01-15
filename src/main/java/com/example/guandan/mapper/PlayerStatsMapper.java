package com.example.guandan.mapper;

import com.example.guandan.entity.PlayerStats;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlayerStatsMapper {
    
    @Select("SELECT * FROM player_stats WHERE user_id = #{userId}")
    PlayerStats findByUserId(Long userId);
    
    @Insert("INSERT INTO player_stats(user_id, total_games, total_wins, total_score, win_rate) " +
            "VALUES(#{userId}, #{totalGames}, #{totalWins}, #{totalScore}, #{winRate})")
    int insert(PlayerStats stats);
    
    @Update("UPDATE player_stats SET total_games = #{totalGames}, total_wins = #{totalWins}, " +
            "total_score = #{totalScore}, win_rate = #{winRate} WHERE user_id = #{userId}")
    int update(PlayerStats stats);
}
