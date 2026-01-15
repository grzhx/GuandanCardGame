package com.example.guandan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * A single physical card.
 *
 * IMPORTANT:
 * - Guandan uses TWO decks. There are many duplicate "same suit + same number" cards.
 * - We therefore assign each card a stable unique id so that "remove" is unambiguous.
 * - For compatibility with existing front-end payloads, we keep (color, number, selected).
 *   Front-end may send either {id} or {color, number}; id is preferred.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Card {

    /** Unique id for this physical card (stable within a game). */
    private String id;

    /** Spade, Club, Heart, Diamond, Joker */
    private String color;

    /**
     * Card face encoding (kept for backward compatibility):
     * - 1=A
     * - 3..13=3..K
     * - 14=2
     * - 15=BlackJoker
     * - 16=RedJoker
     *
     * NOTE: number==2 should never appear (2 is encoded as 14). We still tolerate it.
     */
    private int number;

    /** UI-only; server logic ignores it. */
    private boolean selected;

    public Card(String color, int number) {
        this(null, color, number, false);
    }

    public boolean isLevelCard(int level) {
        return number == level;
    }

    public boolean isRedHeartLevelCard(int level) {
        return "Heart".equals(color) && number == level;
    }

    public boolean isJoker() {
        return number >= 15;
    }

    /**
     * Rank order used for comparing cards/patterns.
     *
     * Order: 2 (lowest) < 3 < ... < K < A < level < jokers
     *
     * @param level current level card number (same encoding as {@link #number})
     */
    public int getRank(int level) {
        if (number == 16) return 16; // Red Joker
        if (number == 15) return 15; // Black Joker
        if (isLevelCard(level)) return 14; // Level card

        // tolerate "2" accidentally sent as 2
        if (number == 14 || number == 2) return 1; // 2

        // 3..10
        if (number >= 3 && number <= 10) return number - 1; // 3->2 ... 10->9

        // J,Q,K
        if (number == 11) return 10;
        if (number == 12) return 11;
        if (number == 13) return 12;

        // A
        if (number == 1) return 13;

        return 0;
    }

    /**
     * Two cards are considered the same physical card only if their id matches.
     * (Fallback comparisons by color/number are done explicitly where needed.)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card card)) return false;
        return id != null && card.id != null && Objects.equals(id, card.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
