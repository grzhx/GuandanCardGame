package com.example.guandan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.*;

@Data
@AllArgsConstructor
public class CardPattern {
    private PatternType type;
    private int rank;
    private int count;
    private List<Card> cards;
    
    public enum PatternType {
        SINGLE, PAIR, TRIPLE, STRAIGHT, TRIPLE_STRAIGHT, PAIR_STRAIGHT, BOMB, KING_BOMB, PASS
    }
    
    public boolean canBeat(CardPattern other, int level) {
        if (other.type == PatternType.PASS) return true;
        if (this.type == PatternType.PASS) return false;
        
        if (other.type == PatternType.KING_BOMB) {
            return this.type == PatternType.KING_BOMB && this.rank > other.rank;
        }
        
        if (this.type == PatternType.KING_BOMB) return true;
        
        if (other.type == PatternType.BOMB) {
            if (this.type != PatternType.BOMB) return false;
            if (this.count != other.count) return this.count > other.count;
            return this.rank > other.rank;
        }
        
        if (this.type == PatternType.BOMB) return true;
        
        if (this.type != other.type) return false;
        if (this.count != other.count) return false;
        
        return this.rank > other.rank;
    }
}
