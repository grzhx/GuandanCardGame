package com.example.guandan.service;

import com.example.guandan.entity.GameHistory;
import com.example.guandan.mapper.GameHistoryMapper;
import com.example.guandan.model.Card;
import com.example.guandan.model.CardPattern;
import com.example.guandan.model.GameRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameHistoryMapper gameHistoryMapper;
    private final UserService userService;

    /**
     * Start a new SINGLE hand (deal + reset trick state).
     */
    public void initGame(GameRoom room) {
        List<Card> deck = createDeck();
        Collections.shuffle(deck);

        for (int i = 0; i < 4; i++) {
            List<Card> hand = new ArrayList<>(deck.subList(i * 27, (i + 1) * 27));
            sortHand(hand, room.getLevel());
            room.getPlayers()[i].setHand(hand);
        }

        room.setCurrentPlayer(room.getFirstPlayer());
        room.setStarted(true);
        room.setFinished(false);
        room.setPaused(false);

        // trick state
        room.setLastPattern(null);
        room.setLastPlayerId(room.getFirstPlayer());
        room.setPassCount(0);
        room.setFinishedPlayers(new ArrayList<>());
        room.setRanks(new int[4]);
        room.getCurrentRoundCards().clear();
    }

    /**
     * Create a full 108-card double deck using the existing (legacy) encoding:
     * 1=A, 14=2, 15/16=jokers.
     */
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>(108);
        String[] colors = {"Spade", "Club", "Heart", "Diamond"};

        for (int copy = 0; copy < 2; copy++) {
            for (String color : colors) {
                for (int num = 1; num <= 13; num++) {
                    if (num == 2) continue; // 2 is encoded as 14
                    deck.add(new Card(genCardId(copy, color, num), color, num, false));
                }
                deck.add(new Card(genCardId(copy, color, 14), color, 14, false)); // 2
            }
            deck.add(new Card(genCardId(copy, "Joker", 15), "Joker", 15, false));
            deck.add(new Card(genCardId(copy, "Joker", 16), "Joker", 16, false));
        }

        return deck;
    }

    private String genCardId(int copy, String color, int number) {
        // stable enough for a single game: deckCopy-color-number-randomSuffix
        return copy + "-" + color.charAt(0) + "-" + number + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void sortHand(List<Card> hand, int level) {
        hand.sort(Comparator
                .comparingInt((Card c) -> c.getRank(level))
                .thenComparing(Card::getColor)
        );
    }

    /**
     * Pattern analysis (simplified but consistent):
     * - supports single/pair/triple
     * - straight (>=5), pair-straight (>=6, even), triple-straight (>=6, multiple of 3)
     * - bombs: N-of-a-kind (N>=4)
     * - king bomb: 4 jokers
     *
     * Not implemented in this "better" baseline:
     * - 红桃级牌万能替牌
     * - 三带二 / 四带二 / 飞机带翅膀等复杂带牌
     */
    public CardPattern analyzePattern(List<Card> cards, int level) {
        if (cards == null || cards.isEmpty()) {
            return new CardPattern(CardPattern.PatternType.PASS, 0, 0, cards == null ? List.of() : cards);
        }

        int size = cards.size();

        // King Bomb: 4 jokers in double-deck
        if (size == 4 && cards.stream().allMatch(Card::isJoker)) {
            int max = cards.stream().mapToInt(c -> c.getRank(level)).max().orElse(16);
            return new CardPattern(CardPattern.PatternType.KING_BOMB, max, size, cards);
        }

        // Count by face number (legacy number encoding)
        Map<Integer, Long> faceCounts = cards.stream()
                .collect(Collectors.groupingBy(Card::getNumber, Collectors.counting()));

        // Bomb: N>=4 same face (non-joker)
        if (faceCounts.size() == 1 && size >= 4) {
            Card c = cards.get(0);
            if (c.isJoker()) {
                // 2 jokers / 3 jokers not treated as a bomb in this baseline
                return null;
            }
            return new CardPattern(CardPattern.PatternType.BOMB, c.getRank(level), size, cards);
        }

        // Single/Pair/Triple
        if (size == 1) {
            return new CardPattern(CardPattern.PatternType.SINGLE, cards.get(0).getRank(level), 1, cards);
        }
        if (size == 2 && faceCounts.size() == 1) {
            return new CardPattern(CardPattern.PatternType.PAIR, cards.get(0).getRank(level), 2, cards);
        }
        if (size == 3 && faceCounts.size() == 1) {
            return new CardPattern(CardPattern.PatternType.TRIPLE, cards.get(0).getRank(level), 3, cards);
        }

        // Straight families: disallow jokers + disallow level cards (keeps rules predictable)
        if (cards.stream().anyMatch(Card::isJoker)) return null;
        if (cards.stream().anyMatch(c -> c.isLevelCard(level))) return null;

        if (size >= 5 && isConsecutiveByRank(faceCounts, level, 1)) {
            return new CardPattern(CardPattern.PatternType.STRAIGHT, maxRank(faceCounts.keySet(), level), size, cards);
        }
        if (size >= 6 && size % 2 == 0 && isConsecutiveByRank(faceCounts, level, 2)) {
            return new CardPattern(CardPattern.PatternType.PAIR_STRAIGHT, maxRank(faceCounts.keySet(), level), size, cards);
        }
        if (size >= 6 && size % 3 == 0 && isConsecutiveByRank(faceCounts, level, 3)) {
            return new CardPattern(CardPattern.PatternType.TRIPLE_STRAIGHT, maxRank(faceCounts.keySet(), level), size, cards);
        }

        return null;
    }

    private boolean isConsecutiveByRank(Map<Integer, Long> faceCounts, int level, int groupSize) {
        if (faceCounts.values().stream().anyMatch(v -> v != groupSize)) return false;

        List<Integer> ranks = faceCounts.keySet().stream()
                .map(n -> new Card("X", n).getRank(level))
                .sorted()
                .toList();

        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) - ranks.get(i - 1) != 1) return false;
        }
        return true;
    }

    private int maxRank(Collection<Integer> numbers, int level) {
        int max = 0;
        for (int n : numbers) {
            max = Math.max(max, new Card("X", n).getRank(level));
        }
        return max;
    }

    /**
     * Apply a move.
     *
     * @param cards empty => PASS
     */
    public boolean playCards(GameRoom room, int playerId, List<Card> cards) {
        if (room == null || !room.isStarted() || room.isFinished()) return false;
        if (playerId < 0 || playerId > 3) return false;
        if (room.getPlayers()[playerId] == null) return false;
        if (room.getFinishedPlayers().contains(playerId)) return false;
        if (room.getCurrentPlayer() != playerId) return false;

        if (cards == null) cards = List.of();
        boolean isPass = cards.isEmpty();

        // PASS rules
        if (isPass) {
            if (room.getLastPattern() == null) {
                // cannot pass when nobody has led this trick
                return false;
            }

            room.setPassCount(room.getPassCount() + 1);
            room.getCurrentRoundCards().put(playerId, List.of());

            int active = 4 - room.getFinishedPlayers().size();
            int next = nextActivePlayer(room, playerId);

            // all others passed -> new trick, leader is last non-pass player
            if (room.getPassCount() >= active - 1) {
                room.setPassCount(0);
                room.setLastPattern(null);
                room.getCurrentRoundCards().clear();

                int leader = room.getLastPlayerId();
                if (room.getFinishedPlayers().contains(leader)) {
                    leader = nextActivePlayer(room, leader);
                }
                room.setCurrentPlayer(leader);
            } else {
                room.setCurrentPlayer(next);
            }
            return true;
        }

        // Non-pass
        CardPattern pattern = analyzePattern(cards, room.getLevel());
        if (pattern == null) return false;

        if (room.getLastPattern() != null && !pattern.canBeat(room.getLastPattern())) {
            return false;
        }

        GameRoom.Player player = room.getPlayers()[playerId];
        if (!removeCardsFromHand(player.getHand(), cards)) {
            return false;
        }

        room.getCurrentRoundCards().put(playerId, cards);
        room.setLastPattern(pattern);
        room.setLastPlayerId(playerId);
        room.setPassCount(0);

        // Finished?
        if (player.getHand().isEmpty()) {
            room.getFinishedPlayers().add(playerId);
            if (room.getFinishedPlayers().size() == 4) {
                finishGame(room);
                return true;
            }
        }

        room.setCurrentPlayer(nextActivePlayer(room, playerId));
        return true;
    }

    private int nextActivePlayer(GameRoom room, int fromPlayerId) {
        int next = (fromPlayerId + 1) % 4;
        for (int i = 0; i < 4; i++) {
            if (!room.getFinishedPlayers().contains(next)) return next;
            next = (next + 1) % 4;
        }
        return fromPlayerId;
    }

    /**
     * Remove cards from a player's hand.
     *
     * Preferred matching: by id (physical card).
     * Fallback: by (color, number) - removes the first matching card.
     */
    private boolean removeCardsFromHand(List<Card> hand, List<Card> toPlay) {
        for (Card want : toPlay) {
            int idx = -1;
            if (want.getId() != null && !want.getId().isBlank()) {
                for (int i = 0; i < hand.size(); i++) {
                    if (Objects.equals(hand.get(i).getId(), want.getId())) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx < 0) {
                for (int i = 0; i < hand.size(); i++) {
                    Card have = hand.get(i);
                    if (Objects.equals(have.getColor(), want.getColor()) && have.getNumber() == want.getNumber()) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx < 0) return false;
            hand.remove(idx);
        }
        return true;
    }

    private void finishGame(GameRoom room) {
        room.setFinished(true);

        // ranks
        for (int i = 0; i < room.getFinishedPlayers().size(); i++) {
            int seat = room.getFinishedPlayers().get(i);
            room.getRanks()[seat] = i + 1;
        }

        int team0Rank = Math.min(room.getRanks()[0], room.getRanks()[2]);
        int team1Rank = Math.min(room.getRanks()[1], room.getRanks()[3]);

        int w1, w2, l1, l2;
        if (team0Rank < team1Rank) {
            w1 = 0; w2 = 2; l1 = 1; l2 = 3;
        } else {
            w1 = 1; w2 = 3; l1 = 0; l2 = 2;
        }

        int upgrade = calculateUpgrade(room.getRanks(), w1, w2);

        // score update (in-memory)
        room.getPlayers()[w1].setScore(room.getPlayers()[w1].getScore() + upgrade);
        room.getPlayers()[w2].setScore(room.getPlayers()[w2].getScore() + upgrade);
        room.getPlayers()[l1].setScore(room.getPlayers()[l1].getScore() - upgrade);
        room.getPlayers()[l2].setScore(room.getPlayers()[l2].getScore() - upgrade);

        // level progression (simple)
        room.setLevel(room.getLevel() + upgrade);

        // persist history + update stats (best-effort)
        try {
            persistGame(room, w1, w2, upgrade);
        } catch (Exception ignored) {
            // If DB is not configured, game should still be playable.
        }
    }

    private int calculateUpgrade(int[] ranks, int w1, int w2) {
        int r1 = ranks[w1];
        int r2 = ranks[w2];
        if ((r1 == 1 && r2 == 2) || (r1 == 2 && r2 == 1)) return 3;
        if ((r1 == 1 && r2 == 3) || (r1 == 3 && r2 == 1)) return 2;
        return 1;
    }

    private void persistGame(GameRoom room, int w1, int w2, int scoreChange) {
        GameHistory gh = new GameHistory();
        gh.setRoomId(room.getRoomId());
        gh.setGameType(room.getGameType());

        gh.setPlayer0Id(room.getPlayers()[0].getUserId());
        gh.setPlayer1Id(room.getPlayers()[1].getUserId());
        gh.setPlayer2Id(room.getPlayers()[2].getUserId());
        gh.setPlayer3Id(room.getPlayers()[3].getUserId());

        // winner_team: store the representative seat (0/2 or 1/3)
        gh.setWinnerTeam(Math.min(w1, w2));
        gh.setScoreChange(scoreChange);
        gh.setFinalRank(room.getRanks()[0] + "," + room.getRanks()[1] + "," + room.getRanks()[2] + "," + room.getRanks()[3]);

        gameHistoryMapper.insert(gh);

        // update stats for all players
        Set<Integer> winners = Set.of(w1, w2);
        for (int seat = 0; seat < 4; seat++) {
            Long uid = room.getPlayers()[seat].getUserId();
            boolean won = winners.contains(seat);
            int delta = won ? scoreChange : -scoreChange;
            userService.updateStats(uid, won, delta);
        }
    }
}
