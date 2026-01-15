package com.example.guandan.model;

import lombok.Data;

import java.util.*;

@Data
public class GameRoom {
    private String roomId;
    private String gameType; // SINGLE or MULTIPLE

    /** 当前级牌（使用 Card.number 相同编码：1=A, 14=2, 15/16=王） */
    private int level = 2;

    private Player[] players = new Player[4];

    /** 当前轮到的座位号 [0-3] */
    private int currentPlayer;

    private boolean started;
    private boolean finished;
    private boolean paused;

    /** 上一次非PASS的出牌牌型（当为 null 表示新一墩领出） */
    private CardPattern lastPattern;

    /** 上一次非PASS出牌的玩家座位号 */
    private int lastPlayerId;

    /** 当前墩的连续 PASS 次数（有人出牌后重置） */
    private int passCount;

    /** 出完牌的玩家顺序 */
    private List<Integer> finishedPlayers = new ArrayList<>();

    /** 最终名次：ranks[seat] = 1..4 */
    private int[] ranks = new int[4];

    /**
     * 当前墩每个玩家最近一次出牌（PASS 用空列表表示）。
     * 前端可以直接用这个展示“桌面上的牌”。
     */
    private Map<Integer, List<Card>> currentRoundCards = new HashMap<>();

    private TributeState tributeState;

    /** 开局先手 */
    private int firstPlayer;

    private Long hostId;

    @Data
    public static class Player {
        private Long userId;
        private String username;
        private int seat;
        private List<Card> hand = new ArrayList<>();
        private boolean ready;
        private boolean online;
        private int score;

        public Player() {}

        public Player(Long userId, String username, int seat) {
            this.userId = userId;
            this.username = username;
            this.seat = seat;
            this.ready = true;
            this.online = true;
            this.score = 0;
        }
    }

    @Data
    public static class TributeState {
        private boolean required;
        private boolean completed;
        private Map<Integer, Card> tributeCards = new HashMap<>();
        private Map<Integer, Card> returnCards = new HashMap<>();
        private boolean antiTribute;
    }
}
