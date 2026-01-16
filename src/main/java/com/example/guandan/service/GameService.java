package com.example.guandan.service;

import com.example.guandan.model.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GameService {
    
    public void initGame(GameRoom room) {
        List<Card> deck = createDeck();
        Collections.shuffle(deck);
        
        for (int i = 0; i < 4; i++) {
            room.getPlayers()[i].setHand(new ArrayList<>(deck.subList(i * 27, (i + 1) * 27)));
        }
        
        room.setCurrentPlayer(room.getFirstPlayer());
        room.setStarted(true);
        room.setFinished(false);
        room.setLastPattern(null);
        room.setFinishedPlayers(new ArrayList<>());
        room.getCurrentRoundCards().clear();
        room.setPassCount(0);
        
        String gameKey = "game" + room.getCurrentGameIndex();
        room.getGameHistory().put(gameKey, new ArrayList<>());
    }
    
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        String[] colors = {"Spade", "Club", "Heart", "Diamond"};
        
        for (int copy = 0; copy < 2; copy++) {
            for (String color : colors) {
                for (int num = 1; num <= 13; num++) {
                    deck.add(new Card(color, num));
                }
                deck.add(new Card(color, 14)); // 2
            }
            deck.add(new Card("Joker", 15)); // Black Joker
            deck.add(new Card("Joker", 16)); // Red Joker
        }
        
        return deck;
    }
    
    public CardPattern analyzePattern(List<Card> cards, int level) {
        if (cards == null || cards.isEmpty()) {
            return new CardPattern(CardPattern.PatternType.PASS, 0, 0, cards);
        }
        
        int size = cards.size();
        
        // King Bomb
        if (size == 4 && cards.stream().allMatch(c -> c.getNumber() >= 15)) {
            return new CardPattern(CardPattern.PatternType.KING_BOMB, 16, 4, cards);
        }
        
        // Bomb
        Map<Integer, Long> counts = cards.stream()
            .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));
        
        if (counts.size() == 1 && size >= 4) {
            int num = cards.get(0).getNumber();
            return new CardPattern(CardPattern.PatternType.BOMB, cards.get(0).getRank(level), size, cards);
        }
        
        // Single
        if (size == 1) {
            return new CardPattern(CardPattern.PatternType.SINGLE, cards.get(0).getRank(level), 1, cards);
        }
        
        // Pair
        if (size == 2 && counts.size() == 1) {
            return new CardPattern(CardPattern.PatternType.PAIR, cards.get(0).getRank(level), 2, cards);
        }
        
        // Triple
        if (size == 3 && counts.size() == 1) {
            return new CardPattern(CardPattern.PatternType.TRIPLE, cards.get(0).getRank(level), 3, cards);
        }
        
        // Straight
        if (size >= 5 && isConsecutive(cards, level, 1)) {
            return new CardPattern(CardPattern.PatternType.STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        // Pair Straight
        if (size >= 6 && size % 2 == 0 && isConsecutive(cards, level, 2)) {
            return new CardPattern(CardPattern.PatternType.PAIR_STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        // Triple Straight
        if (size >= 6 && size % 3 == 0 && isConsecutive(cards, level, 3)) {
            return new CardPattern(CardPattern.PatternType.TRIPLE_STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        return null;
    }
    
    private boolean isConsecutive(List<Card> cards, int level, int groupSize) {
        Map<Integer, Long> counts = cards.stream()
            .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));
        
        if (counts.values().stream().anyMatch(c -> c != groupSize)) return false;
        
        List<Integer> nums = new ArrayList<>(counts.keySet());
        Collections.sort(nums);
        
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) - nums.get(i - 1) != 1) return false;
        }
        
        return true;
    }
    
    private int getMaxRank(List<Card> cards, int level) {
        return cards.stream().mapToInt(c -> c.getRank(level)).max().orElse(0);
    }
    
    public boolean playCards(GameRoom room, int playerId, List<Card> cards) {
        if (room.getCurrentPlayer() != playerId) return false;
        
        CardPattern pattern = analyzePattern(cards, room.getLevel());
        if (pattern == null && !cards.isEmpty()) return false;
        
        boolean isPass = cards.isEmpty();
        
        if (!isPass && room.getLastPattern() != null && room.getPassCount() < 3 && !pattern.canBeat(room.getLastPattern(), room.getLevel())) {
            return false;
        }
        
        // 记录历史
        String gameKey = "game" + room.getCurrentGameIndex();
        Map<String, Object> move = new HashMap<>();
        move.put("seat", playerId);
        move.put("movement", new ArrayList<>(cards));
        room.getGameHistory().get(gameKey).add(move);
        
        GameRoom.Player player = room.getPlayers()[playerId];
        player.getHand().removeAll(cards);
        room.getCurrentRoundCards().put(playerId, cards);
        
        if (player.getHand().isEmpty()) {
            room.getFinishedPlayers().add(playerId);
            if (room.getFinishedPlayers().size() == 4) {
                finishGame(room);
                return true;
            }
        }
        
        if (isPass) {
            room.setPassCount(room.getPassCount() + 1);
            if (room.getPassCount() >= 3) {
                room.setLastPattern(null);
                room.setPassCount(0);
            }
        } else {
            room.setLastPattern(pattern);
            room.setLastPlayerId(playerId);
            room.setPassCount(0);
        }
        
        room.setCurrentPlayer((playerId + 1) % 4);
        
        return true;
    }
    
    private void finishGame(GameRoom room) {
        room.setFinished(true);
        
        for (int i = 0; i < room.getFinishedPlayers().size(); i++) {
            room.getRanks()[room.getFinishedPlayers().get(i)] = i + 1;
        }
        
        int team0Rank = Math.min(room.getRanks()[0], room.getRanks()[2]);
        int team1Rank = Math.min(room.getRanks()[1], room.getRanks()[3]);
        
        if (team0Rank < team1Rank) {
            updateScores(room, 0, 2);
        } else {
            updateScores(room, 1, 3);
        }
    }
    
    private void updateScores(GameRoom room, int winner1, int winner2) {
        int upgrade = calculateUpgrade(room, winner1, winner2);
        
        room.getPlayers()[winner1].setScore(room.getPlayers()[winner1].getScore() + upgrade);
        room.getPlayers()[winner2].setScore(room.getPlayers()[winner2].getScore() + upgrade);
        
        int loser1 = (winner1 + 1) % 4;
        int loser2 = (winner1 + 3) % 4;
        room.getPlayers()[loser1].setScore(room.getPlayers()[loser1].getScore() - upgrade);
        room.getPlayers()[loser2].setScore(room.getPlayers()[loser2].getScore() - upgrade);
        
        room.setLevel(room.getLevel() + upgrade);
    }
    
    private int calculateUpgrade(GameRoom room, int w1, int w2) {
        int[] ranks = room.getRanks();
        
        if ((ranks[w1] == 1 && ranks[w2] == 2) || (ranks[w1] == 2 && ranks[w2] == 1)) {
            return 3;
        } else if ((ranks[w1] == 1 && ranks[w2] == 3) || (ranks[w1] == 3 && ranks[w2] == 1)) {
            return 2;
        } else {
            return 1;
        }
    }
}
