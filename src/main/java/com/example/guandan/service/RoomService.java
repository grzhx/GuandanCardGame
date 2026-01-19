package com.example.guandan.service;

import com.example.guandan.model.GameRoom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class RoomService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final Random random = new Random();
    
    @Value("${game.auto-agent:false}")
    private boolean autoAgent;
    
    public RoomService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    public String generateRoomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    public GameRoom createRoom(String gameType, int level, Long hostId) {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setGameType(gameType);
        room.setLevel(level);
        room.setHostId(hostId);
        room.setStarted(false);
        room.setFinished(false);
        
        saveRoom(room);
        return room;
    }
    
    public GameRoom createMatchRoom(int level) {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom();
        room.setRoomId(roomId);
        room.setGameType("MULTIPLE");
        room.setLevel(level);
        room.setHostId(-1L); // 匹配房间无房主
        room.setStarted(false);
        room.setFinished(false);
        
        saveRoom(room);
        return room;
    }
    
    public GameRoom getRoom(String roomId) {
        return (GameRoom) redisTemplate.opsForValue().get("room:" + roomId);
    }
    
    public void saveRoom(GameRoom room) {
        redisTemplate.opsForValue().set("room:" + room.getRoomId(), room, 1, TimeUnit.MINUTES);
    }
    
    public void deleteRoom(String roomId) {
        redisTemplate.delete("room:" + roomId);
    }
    
    public int addPlayer(String roomId, Long userId, String username) {
        GameRoom room = getRoom(roomId);
        if (room == null) return -1;
        
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] == null) {
                room.getPlayers()[i] = new GameRoom.Player(userId, username, i);
                if (autoAgent) {
                    fillWithAgents(room, i + 1);
                }
                saveRoom(room);
                return i;
            }
        }
        return -1;
    }
    
    private void fillWithAgents(GameRoom room, int startSeat) {
        for (int i = startSeat; i < 4; i++) {
            if (room.getPlayers()[i] == null) {
                room.getPlayers()[i] = GameRoom.Player.createAgent(i);
            }
        }
    }
    
    public boolean isRoomFull(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;
        
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] == null) return false;
        }
        return true;
    }
    
    public boolean allPlayersReady(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room == null) return false;
        
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] == null || !room.getPlayers()[i].isReady()) {
                return false;
            }
        }
        return true;
    }
}
