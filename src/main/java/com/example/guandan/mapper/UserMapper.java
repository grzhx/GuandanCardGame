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
}
