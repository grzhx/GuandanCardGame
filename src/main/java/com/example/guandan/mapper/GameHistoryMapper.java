package com.example.guandan.mapper;

import com.example.guandan.entity.GameHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface GameHistoryMapper {
    
    @Insert("INSERT INTO game_history(room_id, player0_id, player1_id, player2_id, player3_id, winner_team, final_rank, score_change, game_type) " +
            "VALUES(#{roomId}, #{player0Id}, #{player1Id}, #{player2Id}, #{player3Id}, #{winnerTeam}, #{finalRank}, #{scoreChange}, #{gameType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GameHistory gameHistory);
    
    @Select("SELECT * FROM game_history WHERE player0_id = #{userId} OR player1_id = #{userId} OR player2_id = #{userId} OR player3_id = #{userId} ORDER BY created_at DESC")
    List<GameHistory> findByUserId(Long userId);
}
