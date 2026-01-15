package com.example.guandan.service;

import com.example.guandan.entity.GamePlayerState;
import com.example.guandan.entity.GameSession;
import com.example.guandan.entity.User;
import com.example.guandan.mapper.GamePlayerStateMapper;
import com.example.guandan.mapper.GameSessionMapper;
import com.example.guandan.mapper.UserMapper;
import com.example.guandan.model.GameRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Best-effort persistence for "current game" state.
 *
 * The game still runs on Redis (GameRoom). This service mirrors the latest
 * room/player snapshot into MySQL so that:
 * - user accounts/passwords are stored
 * - "current game score" can be queried/recovered
 */
@Service
@RequiredArgsConstructor
public class GameStateService {

    private final GameSessionMapper gameSessionMapper;
    private final GamePlayerStateMapper gamePlayerStateMapper;
    private final UserMapper userMapper;

    @Transactional
    public void createSession(GameRoom room) {
        GameSession session = new GameSession();
        session.setRoomId(room.getRoomId());
        session.setGameType(room.getGameType());
        session.setLevel(room.getLevel());
        session.setStatus("WAITING");
        session.setHostId(room.getHostId());
        // insert if not exists
        if (gameSessionMapper.findByRoomId(room.getRoomId()) == null) {
            gameSessionMapper.insert(session);
        }
    }

    @Transactional
    public void addPlayer(String roomId, User user, int seat, int roomLevel) {
        // ensure session exists
        if (gameSessionMapper.findByRoomId(roomId) == null) {
            GameSession s = new GameSession();
            s.setRoomId(roomId);
            s.setGameType("SINGLE");
            s.setLevel(roomLevel);
            s.setStatus("WAITING");
            s.setHostId(null);
            gameSessionMapper.insert(s);
        }

        GamePlayerState state = new GamePlayerState();
        state.setRoomId(roomId);
        state.setSeat(seat);
        state.setUserId(user.getId());
        state.setScore(0);
        state.setLevel(roomLevel);
        state.setHandCount(0);
        // insert may fail if duplicate; that's ok (user rejoin)
        try {
            gamePlayerStateMapper.insert(state);
        } catch (Exception ignored) {
        }

        // update user current snapshot
        userMapper.updateCurrentGame(user.getId(), roomId, seat, 0, roomLevel);
    }

    @Transactional
    public void markStarted(GameRoom room) {
        createSession(room);
        gameSessionMapper.markStarted(room.getRoomId(), "STARTED", room.getLevel(), room.getFirstPlayer());
        // sync all players on start (hand_count available)
        syncRoom(room);
    }

    @Transactional
    public void syncPlayer(GameRoom room, int seat) {
        if (room == null || room.getPlayers()[seat] == null) return;
        var p = room.getPlayers()[seat];
        int handCount = p.getHand() == null ? 0 : p.getHand().size();

        gamePlayerStateMapper.updateState(room.getRoomId(), seat, p.getScore(), room.getLevel(), handCount);
        userMapper.updateCurrentScore(p.getUserId(), p.getScore(), room.getLevel());
    }

    @Transactional
    public void syncRoom(GameRoom room) {
        if (room == null) return;
        for (int seat = 0; seat < 4; seat++) {
            if (room.getPlayers()[seat] == null) continue;
            syncPlayer(room, seat);
        }
    }

    @Transactional
    public void markFinished(GameRoom room) {
        if (room == null) return;
        // update per player final snapshot + rank
        for (int seat = 0; seat < 4; seat++) {
            if (room.getPlayers()[seat] == null) continue;
            var p = room.getPlayers()[seat];
            int handCount = p.getHand() == null ? 0 : p.getHand().size();
            Integer rank = room.getRanks() == null ? null : room.getRanks()[seat];
            gamePlayerStateMapper.updateFinish(room.getRoomId(), seat, rank, p.getScore(), room.getLevel(), handCount);
        }
        gameSessionMapper.markFinished(room.getRoomId(), "FINISHED", room.getLevel());

        // clear user current game snapshot
        for (int seat = 0; seat < 4; seat++) {
            if (room.getPlayers()[seat] == null) continue;
            userMapper.clearCurrentGame(room.getPlayers()[seat].getUserId());
        }
    }
}
