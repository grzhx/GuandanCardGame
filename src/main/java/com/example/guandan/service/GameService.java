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
            if (room.getPlayers()[i] != null) {
                room.getPlayers()[i].setHand(new ArrayList<>(deck.subList(i * 27, (i + 1) * 27)));
            }
        }
        
        room.setCurrentPlayer(room.getFirstPlayer());
        room.setStarted(true);
        room.setFinished(false);
        room.setPaused(false);
        room.setLastPattern(null);
        room.setLastPlayerId(-1);
        room.setFinishedPlayers(new ArrayList<>());
        room.getCurrentRoundCards().clear();
        room.setPassCount(0);
        room.setTributeState(null);
        
        String gameKey = "game" + room.getCurrentGameIndex();
        room.getGameHistory().put(gameKey, new ArrayList<>());
        
        processTribute(room);
    }
    
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        String[] colors = {"Spade", "Club", "Heart", "Diamond"};
        
        for (int copy = 0; copy < 2; copy++) {
            for (String color : colors) {
                for (int num = 1; num <= 13; num++) {
                    deck.add(new Card(color, num));
                }
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
        
        // Separate wildcards (red heart level cards) from regular cards
        List<Card> wildcards = cards.stream()
            .filter(c -> c.isRedHeartLevelCard(level))
            .collect(Collectors.toList());
        List<Card> regularCards = cards.stream()
            .filter(c -> !c.isRedHeartLevelCard(level))
            .collect(Collectors.toList());
        
        // Bomb (wildcards can form bombs with same number cards)
        Map<Integer, Long> counts = regularCards.stream()
            .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));
        
        if (!wildcards.isEmpty()) {
            for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
                if (entry.getValue() + wildcards.size() >= 4) {
                    return new CardPattern(CardPattern.PatternType.BOMB, 
                        new Card("", entry.getKey()).getRank(level), 
                        (int)(entry.getValue() + wildcards.size()), cards);
                }
            }
        } else if (counts.size() == 1 && size >= 4) {
            return new CardPattern(CardPattern.PatternType.BOMB, cards.get(0).getRank(level), size, cards);
        }
        
        // Single
        if (size == 1) {
            return new CardPattern(CardPattern.PatternType.SINGLE, cards.get(0).getRank(level), 1, cards);
        }
        
        // Pair (wildcard can complete a pair)
        if (size == 2) {
            if (wildcards.isEmpty() && counts.size() == 1) {
                return new CardPattern(CardPattern.PatternType.PAIR, cards.get(0).getRank(level), 2, cards);
            } else if (wildcards.size() == 1 && regularCards.size() == 1) {
                return new CardPattern(CardPattern.PatternType.PAIR, regularCards.get(0).getRank(level), 2, cards);
            }
        }
        
        // Triple (wildcards can complete a triple)
        if (size == 3) {
            if (wildcards.isEmpty() && counts.size() == 1) {
                return new CardPattern(CardPattern.PatternType.TRIPLE, cards.get(0).getRank(level), 3, cards);
            } else if (!wildcards.isEmpty() && counts.size() == 1) {
                int num = regularCards.get(0).getNumber();
                if (counts.get(num) + wildcards.size() == 3) {
                    return new CardPattern(CardPattern.PatternType.TRIPLE, 
                        new Card("", num).getRank(level), 3, cards);
                }
            }
        }
        
        // Full House (3+2)
        if (size == 5) {
            List<Map.Entry<Integer, Long>> groups = new ArrayList<>(counts.entrySet());
            groups.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            
            if (wildcards.isEmpty() && groups.size() == 2 && 
                groups.get(0).getValue() == 3 && groups.get(1).getValue() == 2) {
                return new CardPattern(CardPattern.PatternType.FULLHOUSE, 
                    new Card("", groups.get(0).getKey()).getRank(level), 5, cards);
            } else if (!wildcards.isEmpty()) {
                if (groups.size() == 1 && groups.get(0).getValue() + wildcards.size() == 5) {
                    return new CardPattern(CardPattern.PatternType.FULLHOUSE, 
                        new Card("", groups.get(0).getKey()).getRank(level), 5, cards);
                } else if (groups.size() == 2) {
                    long max = groups.get(0).getValue();
                    long min = groups.get(1).getValue();
                    if ((max == 3 && min + wildcards.size() == 2) || 
                        (max + wildcards.size() == 3 && min == 2) ||
                        (max == 2 && min + wildcards.size() == 3) ||
                        (max + wildcards.size() >= 3 && min + (wildcards.size() - (3 - max)) == 2)) {
                        return new CardPattern(CardPattern.PatternType.FULLHOUSE, 
                            new Card("", groups.get(0).getKey()).getRank(level), 5, cards);
                    }
                }
            }
        }
        
        // Straight (wildcards can fill gaps)
        if (size >= 5 && isConsecutiveWithWildcards(cards, level, 1, wildcards.size())) {
            return new CardPattern(CardPattern.PatternType.STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        // Pair Straight (wildcards can complete pairs or fill gaps)
        if (size >= 6 && size % 2 == 0 && isConsecutiveWithWildcards(cards, level, 2, wildcards.size())) {
            return new CardPattern(CardPattern.PatternType.PAIR_STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        // Triple Straight (wildcards can complete triples or fill gaps)
        if (size >= 6 && size % 3 == 0 && isConsecutiveWithWildcards(cards, level, 3, wildcards.size())) {
            return new CardPattern(CardPattern.PatternType.TRIPLE_STRAIGHT, getMaxRank(cards, level), size, cards);
        }
        
        // Straight Flush (at least 5 cards, same suit, consecutive)
        if (size >= 5 && isStraightFlush(cards, level, wildcards.size())) {
            return new CardPattern(CardPattern.PatternType.STRAIGHT_FLUSH, getMaxRank(cards, level), size, cards);
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
    
    private boolean isConsecutiveWithWildcards(List<Card> cards, int level, int groupSize, int wildcardCount) {
        if (wildcardCount == 0) {
            return isConsecutive(cards, level, groupSize);
        }
        
        List<Card> regularCards = cards.stream()
            .filter(c -> !c.isRedHeartLevelCard(level))
            .collect(Collectors.toList());
        
        Map<Integer, Long> counts = regularCards.stream()
            .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));
        
        if (counts.isEmpty()) return false;
        
        List<Integer> nums = new ArrayList<>(counts.keySet());
        Collections.sort(nums);
        
        int expectedGroups = cards.size() / groupSize;
        int minNum = nums.get(0);
        int maxNum = nums.get(nums.size() - 1);
        
        if (maxNum - minNum + 1 != expectedGroups) return false;
        
        int needed = 0;
        for (int num = minNum; num <= maxNum; num++) {
            long count = counts.getOrDefault(num, 0L);
            if (count < groupSize) {
                needed += groupSize - count;
            }
        }
        
        return needed <= wildcardCount;
    }
    
    private boolean isStraightFlush(List<Card> cards, int level, int wildcardCount) {
        List<Card> regularCards = cards.stream()
            .filter(c -> !c.isRedHeartLevelCard(level))
            .collect(Collectors.toList());
        
        if (regularCards.isEmpty()) return false;
        
        // Check if all regular cards have the same suit
        String suit = regularCards.get(0).getColor();
        if (!regularCards.stream().allMatch(c -> c.getColor().equals(suit))) {
            return false;
        }
        
        // Check if cards form a consecutive sequence (with wildcards filling gaps)
        Map<Integer, Long> counts = regularCards.stream()
            .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));
        
        List<Integer> nums = new ArrayList<>(counts.keySet());
        Collections.sort(nums);
        
        int expectedCards = cards.size();
        int minNum = nums.get(0);
        int maxNum = nums.get(nums.size() - 1);
        
        if (maxNum - minNum + 1 != expectedCards) return false;
        
        int needed = 0;
        for (int num = minNum; num <= maxNum; num++) {
            if (!counts.containsKey(num)) {
                needed++;
            }
        }
        
        return needed <= wildcardCount;
    }
    
    private int getMaxRank(List<Card> cards, int level) {
        return cards.stream().mapToInt(c -> c.getRank(level)).max().orElse(0);
    }
    
    public boolean playCards(GameRoom room, int playerId, List<Card> cards) {
        if (room.getCurrentPlayer() != playerId) return false;
        
        CardPattern pattern = analyzePattern(cards, room.getLevel());
        if (pattern == null && !cards.isEmpty()) return false;
        
        boolean isPass = cards.isEmpty();
        
        // 当可以出任意牌时（lastPattern为null），不允许pass
        if (isPass && room.getLastPattern() == null) {
            return false;
        }
        
        if (!isPass && room.getLastPattern() != null && !pattern.canBeat(room.getLastPattern(), room.getLevel())) {
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
            // 检查队友是否也出完牌
            int teammate = (playerId + 2) % 4;
            if (room.getFinishedPlayers().contains(teammate)) {
                finishGame(room);
                return true;
            }
            // 或者检查是否所有人都出完
            if (room.getFinishedPlayers().size() == 4) {
                finishGame(room);
                return true;
            }
        }
        
        if (isPass) {
            room.setPassCount(room.getPassCount() + 1);
            int remainingPlayers = 4 - room.getFinishedPlayers().size();
            // 如果最后出牌的玩家已经出完牌，需要所有剩余玩家都过牌才能接风
            int requiredPasses;
            if (room.getLastPlayerId() != -1 && room.getFinishedPlayers().contains(room.getLastPlayerId())) {
                requiredPasses = remainingPlayers;
            } else {
                requiredPasses = remainingPlayers - 1;
            }
            if (room.getPassCount() >= requiredPasses) {
                room.setLastPattern(null);
                room.setPassCount(0);
            }
        } else {
            room.setLastPattern(pattern);
            room.setLastPlayerId(playerId);
            room.setPassCount(0);
        }
        
        // 单人测试房间：出牌后仍轮到自己
        if (isSinglePlayerRoom(room)) {
            room.setCurrentPlayer(playerId);
        } else {
            int nextPlayer = (playerId + 1) % 4;
            // 三次过牌后，下一个玩家应该是最后出牌的玩家或其队友
            if (room.getPassCount() == 0 && room.getLastPattern() == null && room.getLastPlayerId() != -1) {
                nextPlayer = room.getLastPlayerId();
                if (room.getFinishedPlayers().contains(nextPlayer)) {
                    int teammate = getTeammate(nextPlayer);
                    if (!room.getFinishedPlayers().contains(teammate)) {
                        nextPlayer = teammate;
                    }
                }
            }
            // 跳过已完成的玩家
            while (room.getFinishedPlayers().contains(nextPlayer) && 
                   room.getFinishedPlayers().size() < 4) {
                nextPlayer = (nextPlayer + 1) % 4;
            }
            room.setCurrentPlayer(nextPlayer);
        }
        
        return true;
    }
    
    private void finishGame(GameRoom room) {
        room.setFinished(true);
        
        List<Integer> order = new ArrayList<>(new LinkedHashSet<>(room.getFinishedPlayers()));
        for (int i = 0; i < 4; i++) {
            if (!order.contains(i)) {
                order.add(i);
            }
        }
        room.setFinishedPlayers(order);
        
        for (int i = 0; i < order.size(); i++) {
            room.getRanks()[order.get(i)] = i + 1;
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
        
        int newLevel = room.getLevel() + upgrade;
        if (newLevel > 14) {
            room.setLevel(14);
            room.setGameOver(true);
        } else {
            room.setLevel(newLevel);
        }
    }
    
    private boolean isSinglePlayerRoom(GameRoom room) {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            if (room.getPlayers()[i] != null) count++;
        }
        return count == 1;
    }
    
    private int getTeammate(int seat) {
        return (seat + 2) % 4;
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

    // 结算上一局后处理进贡/还贡、首家确定
    private void processTribute(GameRoom room) {
        GameRoom.TributeState state = new GameRoom.TributeState();
        room.setTributeState(state);
        
        if (!hasValidRanks(room.getRanks())) {
            state.setRequired(false);
            state.setCompleted(true);
            state.setAntiTribute(false);
            room.setCurrentPlayer(room.getFirstPlayer());
            return;
        }

        int upSeat = getSeatByRank(room, 1);
        int secondSeat = getSeatByRank(room, 2);
        int thirdSeat = getSeatByRank(room, 3);
        int downSeat = getSeatByRank(room, 4);

        int team0Rank = Math.min(room.getRanks()[0], room.getRanks()[2]);
        int team1Rank = Math.min(room.getRanks()[1], room.getRanks()[3]);
        int winnerTeam = team0Rank < team1Rank ? 0 : 1;
        Set<Integer> losingSeats = winnerTeam == 0 ? Set.of(1, 3) : Set.of(0, 2);

        int payerFourth = losingSeats.contains(downSeat) ? downSeat : -1;
        int payerThird = losingSeats.contains(thirdSeat) ? thirdSeat : -1;
        boolean doubleTribute = payerThird != -1 && payerFourth != -1;

        if (payerFourth == -1) {
            state.setRequired(false);
            state.setCompleted(true);
            state.setAntiTribute(false);
            room.setCurrentPlayer(room.getFirstPlayer());
            return;
        }

        List<Integer> payers = doubleTribute ? List.of(payerThird, payerFourth) : List.of(payerFourth);
        if (hasEnoughBigJokers(room, payers)) {
            state.setRequired(true);
            state.setCompleted(true);
            state.setAntiTribute(true);
            room.setCurrentPlayer(upSeat);
            room.setFirstPlayer(upSeat);
            return;
        }

        state.setRequired(true);
        state.setAntiTribute(false);

        if (!doubleTribute) {
            Card tributeCard = selectTributeCard(room.getPlayers()[payerFourth].getHand(), room.getLevel());
            moveCard(room.getPlayers()[payerFourth].getHand(), room.getPlayers()[upSeat].getHand(), tributeCard);

            Card returnCard = selectReturnCard(room.getPlayers()[upSeat].getHand(), room.getLevel());
            moveCard(room.getPlayers()[upSeat].getHand(), room.getPlayers()[payerFourth].getHand(), returnCard);

            state.getTributeCards().put(payerFourth, tributeCard);
            state.getReturnCards().put(payerFourth, returnCard);
            state.setCompleted(true);
            room.setCurrentPlayer(payerFourth);
            room.setFirstPlayer(payerFourth);
            return;
        }

        Card tributeThird = selectTributeCard(room.getPlayers()[payerThird].getHand(), room.getLevel());
        Card tributeFourth = selectTributeCard(room.getPlayers()[payerFourth].getHand(), room.getLevel());
        int compare = compareCards(tributeThird, tributeFourth, room.getLevel());

        int payerForUp;
        int payerForSecond;
        Card cardForUp;
        Card cardForSecond;

        if (compare > 0) {
            payerForUp = payerThird;
            payerForSecond = payerFourth;
            cardForUp = tributeThird;
            cardForSecond = tributeFourth;
        } else if (compare < 0) {
            payerForUp = payerFourth;
            payerForSecond = payerThird;
            cardForUp = tributeFourth;
            cardForSecond = tributeThird;
        } else {
            boolean thirdCloser = clockwiseDistance(upSeat, payerThird) < clockwiseDistance(upSeat, payerFourth);
            payerForUp = thirdCloser ? payerThird : payerFourth;
            payerForSecond = thirdCloser ? payerFourth : payerThird;
            cardForUp = payerForUp == payerThird ? tributeThird : tributeFourth;
            cardForSecond = payerForUp == payerThird ? tributeFourth : tributeThird;
        }

        moveCard(room.getPlayers()[payerForUp].getHand(), room.getPlayers()[upSeat].getHand(), cardForUp);
        moveCard(room.getPlayers()[payerForSecond].getHand(), room.getPlayers()[secondSeat].getHand(), cardForSecond);

        Card returnToPayerUp = selectReturnCard(room.getPlayers()[upSeat].getHand(), room.getLevel());
        Card returnToPayerSecond = selectReturnCard(room.getPlayers()[secondSeat].getHand(), room.getLevel());
        moveCard(room.getPlayers()[upSeat].getHand(), room.getPlayers()[payerForUp].getHand(), returnToPayerUp);
        moveCard(room.getPlayers()[secondSeat].getHand(), room.getPlayers()[payerForSecond].getHand(), returnToPayerSecond);

        state.getTributeCards().put(payerForUp, cardForUp);
        state.getTributeCards().put(payerForSecond, cardForSecond);
        state.getReturnCards().put(payerForUp, returnToPayerUp);
        state.getReturnCards().put(payerForSecond, returnToPayerSecond);
        state.setCompleted(true);

        room.setCurrentPlayer(payerForUp);
        room.setFirstPlayer(payerForUp);
    }

    private boolean hasValidRanks(int[] ranks) {
        boolean[] seen = new boolean[5];
        for (int rank : ranks) {
            if (rank < 1 || rank > 4 || seen[rank]) {
                return false;
            }
            seen[rank] = true;
        }
        return true;
    }

    private int getSeatByRank(GameRoom room, int rank) {
        for (int i = 0; i < room.getRanks().length; i++) {
            if (room.getRanks()[i] == rank) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasEnoughBigJokers(GameRoom room, List<Integer> seats) {
        int count = 0;
        for (int seat : seats) {
            List<Card> hand = room.getPlayers()[seat].getHand();
            for (Card card : hand) {
                if (card.getNumber() == 16) {
                    count++;
                }
            }
        }
        return count >= 2;
    }

    private Card selectTributeCard(List<Card> hand, int level) {
        return hand.stream()
            .filter(c -> !c.isRedHeartLevelCard(level))
            .max(Comparator.comparingInt(c -> c.getRank(level)))
            .orElseGet(() -> hand.stream()
                .max(Comparator.comparingInt(c -> c.getRank(level)))
                .orElse(null));
    }

    private Card selectReturnCard(List<Card> hand, int level) {
        Comparator<Card> cmp = Comparator.comparingInt((Card c) -> c.getRank(level))
            .thenComparing(Card::getColor, Comparator.nullsFirst(String::compareTo));
        return hand.stream()
            .filter(c -> c.getNumber() <= 10)
            .min(cmp)
            .orElseGet(() -> hand.stream().min(cmp).orElse(null));
    }

    private int compareCards(Card a, Card b, int level) {
        return Integer.compare(a.getRank(level), b.getRank(level));
    }

    private int clockwiseDistance(int from, int to) {
        int dist = to - from;
        if (dist < 0) {
            dist += 4;
        }
        return dist;
    }

    private void moveCard(List<Card> from, List<Card> to, Card card) {
        if (card == null) return;
        from.remove(card);
        to.add(card);
    }
}
