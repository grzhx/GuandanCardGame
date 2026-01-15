package com.example.guandan.mapper;

import com.example.guandan.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    
    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);
    
    @Insert("INSERT INTO user(username, password) VALUES(#{username}, #{password})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
    
    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Update("UPDATE user SET current_room_id=#{roomId}, current_seat=#{seat}, current_score=#{score}, current_level=#{level} WHERE id=#{userId}")
    int updateCurrentGame(@Param("userId") Long userId,
                          @Param("roomId") String roomId,
                          @Param("seat") Integer seat,
                          @Param("score") Integer score,
                          @Param("level") Integer level);

    @Update("UPDATE user SET current_score=#{score}, current_level=#{level} WHERE id=#{userId}")
    int updateCurrentScore(@Param("userId") Long userId,
                           @Param("score") Integer score,
                           @Param("level") Integer level);

    @Update("UPDATE user SET current_room_id=NULL, current_seat=NULL, current_score=0, current_level=2 WHERE id=#{userId}")
    int clearCurrentGame(@Param("userId") Long userId);
}
