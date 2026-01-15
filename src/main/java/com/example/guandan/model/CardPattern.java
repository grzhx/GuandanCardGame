package com.example.guandan.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CardPattern {

    private PatternType type;

    /**
     * Primary rank for comparison.
     * For serial patterns (straight / pair-straight / triple-straight), this is the highest rank in the sequence.
     */
    private int primaryRank;

    /** Number of cards in this pattern. */
    private int size;

    /** Raw cards (as played). */
    private List<Card> cards;

    public enum PatternType {
        SINGLE,
        PAIR,
        TRIPLE,
        STRAIGHT,
        PAIR_STRAIGHT,
        TRIPLE_STRAIGHT,
        BOMB,       // N-of-a-kind, N>=4
        KING_BOMB,  // 4 jokers
        PASS
    }

    public boolean isPass() {
        return type == PatternType.PASS;
    }

    public boolean isBomb() {
        return type == PatternType.BOMB || type == PatternType.KING_BOMB;
    }

    /**
     * Determine whether this pattern can beat another pattern under simplified guandan rules.
     *
     * Rules implemented:
     * - PASS cannot beat anything.
     * - KING_BOMB beats everything.
     * - BOMB beats any non-bomb; bombs compare by size then rank.
     * - Otherwise types must match and sizes must match; compare by primaryRank.
     */
    public boolean canBeat(CardPattern other) {
        if (other == null || other.isPass()) {
            return !this.isPass();
        }
        if (this.isPass()) return false;

        if (other.type == PatternType.KING_BOMB) {
            return this.type == PatternType.KING_BOMB && this.primaryRank > other.primaryRank;
        }
        if (this.type == PatternType.KING_BOMB) return true;

        if (other.type == PatternType.BOMB) {
            if (this.type != PatternType.BOMB) return false;
            if (this.size != other.size) return this.size > other.size;
            return this.primaryRank > other.primaryRank;
        }
        if (this.type == PatternType.BOMB) return true;

        if (this.type != other.type) return false;
        if (this.size != other.size) return false;

        return this.primaryRank > other.primaryRank;
    }
}
