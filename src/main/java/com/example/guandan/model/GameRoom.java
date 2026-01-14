package com.example.guandan.model;

import lombok.Data;
import java.util.*;

@Data
public class GameRoom {
    private String roomId;
    private String gameType; // SINGLE or MULTIPLE
    private int level = 2;
    private Player[] players = new Player[4];
    private int currentPlayer;
    private boolean started;
    private boolean finished;
    private boolean paused;
    private CardPattern lastPattern;
    private int lastPlayerId;
    private List<Integer> finishedPlayers = new ArrayList<>();
    private int[] ranks = new int[4];
    private Map<Integer, List<Card>> currentRoundCards = new HashMap<>();
    private TributeState tributeState;
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
