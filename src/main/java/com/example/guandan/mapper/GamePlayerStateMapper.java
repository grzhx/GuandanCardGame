package com.example.guandan.mapper;

import com.example.guandan.entity.GamePlayerState;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GamePlayerStateMapper {

    @Insert("INSERT INTO game_player_state(room_id, seat, user_id, score, level, hand_count) " +
            "VALUES(#{roomId}, #{seat}, #{userId}, #{score}, #{level}, #{handCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GamePlayerState state);

    @Select("SELECT * FROM game_player_state WHERE room_id=#{roomId} ORDER BY seat ASC")
    List<GamePlayerState> listByRoomId(@Param("roomId") String roomId);

    @Update("UPDATE game_player_state SET score=#{score}, level=#{level}, hand_count=#{handCount} WHERE room_id=#{roomId} AND seat=#{seat}")
    int updateState(@Param("roomId") String roomId,
                    @Param("seat") Integer seat,
                    @Param("score") Integer score,
                    @Param("level") Integer level,
                    @Param("handCount") Integer handCount);

    @Update("UPDATE game_player_state SET finished_rank=#{rank}, score=#{score}, level=#{level}, hand_count=#{handCount} WHERE room_id=#{roomId} AND seat=#{seat}")
    int updateFinish(@Param("roomId") String roomId,
                     @Param("seat") Integer seat,
                     @Param("rank") Integer rank,
                     @Param("score") Integer score,
                     @Param("level") Integer level,
                     @Param("handCount") Integer handCount);
}
