package com.example.guandan.mapper;

import com.example.guandan.entity.GameSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GameSessionMapper {

    @Insert("INSERT INTO game_session(room_id, game_type, level, status, host_id) " +
            "VALUES(#{roomId}, #{gameType}, #{level}, #{status}, #{hostId})")
    int insert(GameSession session);

    @Select("SELECT * FROM game_session WHERE room_id=#{roomId}")
    GameSession findByRoomId(@Param("roomId") String roomId);

    @Update("UPDATE game_session SET status=#{status}, level=#{level}, first_player=#{firstPlayer}, started_at=NOW() WHERE room_id=#{roomId}")
    int markStarted(@Param("roomId") String roomId,
                    @Param("status") String status,
                    @Param("level") Integer level,
                    @Param("firstPlayer") Integer firstPlayer);

    @Update("UPDATE game_session SET status=#{status}, level=#{level}, finished_at=NOW() WHERE room_id=#{roomId}")
    int markFinished(@Param("roomId") String roomId,
                     @Param("status") String status,
                     @Param("level") Integer level);
}
