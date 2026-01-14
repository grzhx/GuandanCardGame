package com.example.guandan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    private String color; // Spade, Club, Heart, Diamond, Joker
    private int number; // 1-14 (1=A, 11=J, 12=Q, 13=K, 14=2), 15=BlackJoker, 16=RedJoker
    private boolean selected;
    
    public Card(String color, int number) {
        this.color = color;
        this.number = number;
        this.selected = false;
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
    
    public int getRank(int level) {
        if (number == 16) return 16; // Red Joker
        if (number == 15) return 15; // Black Joker
        if (isLevelCard(level)) return 14; // Level card
        if (number == 1) return 13; // A
        if (number == 13) return 12; // K
        if (number == 12) return 11; // Q
        if (number == 11) return 10; // J
        if (number == 10) return 9;
        if (number == 9) return 8;
        if (number == 8) return 7;
        if (number == 7) return 6;
        if (number == 6) return 5;
        if (number == 5) return 4;
        if (number == 4) return 3;
        if (number == 3) return 2;
        if (number == 14) return 1; // 2
        return 0;
    }
}
