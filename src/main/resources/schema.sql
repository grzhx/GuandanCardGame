-- Guandan Game Database Schema (MySQL)
--
-- Notes:
-- 1) Table name `user` is used to remain compatible with the original codebase.
-- 2) Passwords are stored as BCrypt hashes (see AuthController/UserService).
-- 3) "Current game score" is stored in two places:
--    - user.current_* : convenience snapshot (one active room per user)
--    - game_player_state : per-room authoritative state

CREATE DATABASE IF NOT EXISTS guandan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE guandan;

-- User table
CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,

    -- Current in-game snapshot (best-effort)
    current_room_id VARCHAR(6) NULL,
    current_seat INT NULL,
    current_score INT NOT NULL DEFAULT 0,
    current_level INT NOT NULL DEFAULT 2,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_current_room (current_room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Game session table (one row per room)
CREATE TABLE IF NOT EXISTS game_session (
    room_id VARCHAR(6) PRIMARY KEY,
    game_type VARCHAR(10) NOT NULL COMMENT 'SINGLE or MULTIPLE',
    level INT NOT NULL DEFAULT 2,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/STARTED/FINISHED',
    host_id BIGINT NULL,
    first_player INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-player per-room state (current score, seat, etc.)
CREATE TABLE IF NOT EXISTS game_player_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id VARCHAR(6) NOT NULL,
    seat INT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 2,
    hand_count INT NOT NULL DEFAULT 0,
    finished_rank INT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_room_seat (room_id, seat),
    UNIQUE KEY uniq_room_user (room_id, user_id),
    INDEX idx_room (room_id),
    INDEX idx_user (user_id),
    CONSTRAINT fk_gps_room FOREIGN KEY (room_id) REFERENCES game_session(room_id) ON DELETE CASCADE,
    CONSTRAINT fk_gps_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
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
    INDEX idx_room_history (room_id),
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
