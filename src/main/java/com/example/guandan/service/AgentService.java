package com.example.guandan.service;

import com.example.guandan.model.Card;
import com.example.guandan.model.CardPattern;
import com.example.guandan.model.GameRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {
    
    private final GameService gameService;
    
    public List<Card> decidePlay(GameRoom room, int agentSeat) {
        GameRoom.Player agent = room.getPlayers()[agentSeat];
        List<Card> hand = agent.getHand();
        
        if (room.getLastPattern() == null || room.getLastPlayerId() == agentSeat) {
            return findSmallestPattern(hand, room.getLevel());
        }
        
        boolean allPassed = true;
        for (int i = 1; i < 4; i++) {
            int checkPlayer = (room.getLastPlayerId() + i) % 4;
            if (checkPlayer == agentSeat) break;
            if (room.getCurrentRoundCards().containsKey(checkPlayer) && 
                !room.getCurrentRoundCards().get(checkPlayer).isEmpty()) {
                allPassed = false;
                break;
            }
        }
        
        if (allPassed) {
            return findSmallestPattern(hand, room.getLevel());
        }
        
        return findSmallestBeatingPattern(hand, room.getLastPattern(), room.getLevel());
    }
    
    private List<Card> findSmallestPattern(List<Card> hand, int level) {
        if (hand.isEmpty()) return new ArrayList<>();
        
        hand.sort(Comparator.comparingInt(c -> c.getRank(level)));
        
        for (Card card : hand) {
            List<Card> single = Collections.singletonList(card);
            CardPattern pattern = gameService.analyzePattern(single, level);
            if (pattern != null) return single;
        }
        
        return Collections.singletonList(hand.get(0));
    }
    
    private List<Card> findSmallestBeatingPattern(List<Card> hand, CardPattern lastPattern, int level) {
        List<List<Card>> allCombinations = generateCombinations(hand, lastPattern.getCards().size());
        
        List<List<Card>> validBeats = new ArrayList<>();
        for (List<Card> combo : allCombinations) {
            CardPattern pattern = gameService.analyzePattern(combo, level);
            if (pattern != null && pattern.canBeat(lastPattern, level)) {
                validBeats.add(combo);
            }
        }
        
        if (validBeats.isEmpty()) return new ArrayList<>();
        
        validBeats.sort((a, b) -> {
            CardPattern pa = gameService.analyzePattern(a, level);
            CardPattern pb = gameService.analyzePattern(b, level);
            return Integer.compare(pa.getRank(), pb.getRank());
        });
        
        return validBeats.get(0);
    }
    
    private List<List<Card>> generateCombinations(List<Card> hand, int size) {
        List<List<Card>> result = new ArrayList<>();
        generateCombinationsHelper(hand, size, 0, new ArrayList<>(), result);
        return result;
    }
    
    private void generateCombinationsHelper(List<Card> hand, int size, int start, 
                                           List<Card> current, List<List<Card>> result) {
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = start; i < hand.size(); i++) {
            current.add(hand.get(i));
            generateCombinationsHelper(hand, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
