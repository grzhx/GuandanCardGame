-- Guandan Game Database Schema

CREATE DATABASE IF NOT EXISTS guandan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE guandan;

-- User table
CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Game history table
CREATE TABLE IF NOT EXISTS game_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(6) NOT NULL,
    player0_id BIGINT NOT NULL,
    player1_id BIGINT NOT NULL,
    player2_id BIGINT NOT NULL,
    player3_id BIGINT NOT NULL,
    winner_team INT NOT NULL COMMENT '0 or 2 for team 0-2, 1 or 3 for team 1-3',
    final_rank VARCHAR(20) NOT NULL COMMENT 'comma separated ranks: 0,2,1,3',
    score_change INT NOT NULL COMMENT 'score change for winners',
    game_type VARCHAR(10) NOT NULL COMMENT 'SINGLE or MULTIPLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room (room_id),
    INDEX idx_player0 (player0_id),
    INDEX idx_player1 (player1_id),
    INDEX idx_player2 (player2_id),
    INDEX idx_player3 (player3_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Player statistics table
CREATE TABLE IF NOT EXISTS player_stats (
    user_id BIGINT PRIMARY KEY,
    total_games INT DEFAULT 0,
    total_wins INT DEFAULT 0,
    total_score INT DEFAULT 0,
    win_rate DECIMAL(5,2) DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
