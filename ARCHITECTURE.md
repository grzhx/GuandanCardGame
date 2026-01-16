# Guandan Game Server 架构文档

## 系统架构概述

本项目采用经典的三层架构设计，基于Spring Boot框架实现，支持HTTP REST API和WebSocket两种通信协议。

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                              │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │   客户端1 (HTTP)  │         │  客户端2 (WebSocket)│       │
│  │   4秒轮询         │         │   实时通信         │       │
│  └──────────────────┘         └──────────────────┘         │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      应用服务层                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Spring Boot Application                  │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐    │  │
│  │  │ Controller │  │  WebSocket │  │   Service  │    │  │
│  │  │   Layer    │  │   Handler  │  │   Layer    │    │  │
│  │  └────────────┘  └────────────┘  └────────────┘    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据持久层                              │
│  ┌──────────────────┐         ┌──────────────────┐         │
│  │   MySQL 数据库    │         │   Redis 缓存     │         │
│  │   持久化存储      │         │   会话管理       │         │
│  └──────────────────┘         └──────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## 技术栈

### 后端框架
- **Spring Boot 4.0.1**: 核心应用框架
- **Spring WebMVC**: HTTP REST API支持
- **Spring WebSocket**: WebSocket通信支持
- **MyBatis 4.0.1**: ORM框架

### 数据存储
- **MySQL 8.0+**: 关系型数据库，存储用户、游戏历史、统计数据
- **Redis 6.0+**: 内存数据库，存储游戏房间状态、会话信息

### 工具库
- **Lombok**: 简化Java代码
- **Jackson**: JSON序列化/反序列化

## 项目结构

```
GuandanGame/
├── src/main/java/com/example/guandan/
│   ├── GuandanGameApplication.java          # 应用入口
│   │
│   ├── config/                              # 配置层
│   │   ├── RedisConfig.java                 # Redis配置
│   │   └── WebSocketConfig.java             # WebSocket配置
│   │
│   ├── controller/                          # 控制器层（HTTP）
│   │   ├── AuthController.java              # 认证接口
│   │   └── GameController.java              # 游戏接口
│   │
│   ├── handler/                             # 处理器层（WebSocket）
│   │   └── GameWebSocketHandler.java        # WebSocket消息处理
│   │
│   ├── service/                             # 业务逻辑层
│   │   ├── UserService.java                 # 用户服务
│   │   ├── RoomService.java                 # 房间管理服务
│   │   └── GameService.java                 # 游戏逻辑服务
│   │
│   ├── mapper/                              # 数据访问层
│   │   ├── UserMapper.java                  # 用户数据访问
│   │   ├── GameHistoryMapper.java           # 游戏历史数据访问
│   │   └── PlayerStatsMapper.java           # 统计数据访问
│   │
│   ├── entity/                              # 实体层（数据库映射）
│   │   ├── User.java                        # 用户实体
│   │   ├── GameHistory.java                 # 游戏历史实体
│   │   └── PlayerStats.java                 # 统计实体
│   │
│   └── model/                               # 模型层（业务对象）
│       ├── Card.java                        # 扑克牌模型
│       ├── CardPattern.java                 # 牌型模型
│       └── GameRoom.java                    # 游戏房间模型
│
└── src/main/resources/
    ├── application.yml                      # 应用配置
    └── schema.sql                           # 数据库脚本
```

## 核心模块设计

### 1. 认证模块

**职责**: 处理用户注册和登录

**组件**:
- `AuthController`: 提供注册/登录接口
- `UserService`: 用户业务逻辑
- `UserMapper`: 用户数据访问

**流程**:
```
客户端 → AuthController → UserService → UserMapper → MySQL
```

### 2. 房间管理模块

**职责**: 管理游戏房间的创建、加入、状态维护

**组件**:
- `RoomService`: 房间管理逻辑
- `GameRoom`: 房间模型
- Redis: 房间状态存储

**关键功能**:
- 生成6位房间ID
- 管理玩家加入/离开
- 维护房间状态（准备、游戏中、结束）
- 24小时自动过期

**数据结构**:
```java
GameRoom {
    String roomId;              // 房间ID
    String gameType;            // SINGLE/MULTIPLE
    int level;                  // 当前级牌
    Player[] players;           // 4个玩家
    boolean started;            // 是否开始
    boolean finished;           // 是否结束
    CardPattern lastPattern;    // 上一次出牌
    List<Integer> finishedPlayers; // 已完成玩家
    int passCount;              // 连续过牌计数（3人过牌后清空lastPattern）
    int currentGameIndex;       // 当前小局索引
    Map<String, List<Map>> gameHistory; // 游戏历史记录
}
```

**游戏历史记录格式**:
```json
{
  "game1": [
    {"seat": 0, "movement": [...]},
    {"seat": 1, "movement": []},
    ...
  ]
}
```

### 3. 游戏逻辑模块

**职责**: 实现掼蛋游戏核心规则

**组件**:
- `GameService`: 游戏逻辑服务
- `Card`: 扑克牌模型
- `CardPattern`: 牌型模型

**核心算法**:

#### 3.1 洗牌发牌
```java
createDeck() → shuffle() → distribute(27 cards × 4 players)
```

#### 3.2 牌型识别
```java
analyzePattern(cards) {
    if (天王炸) return KING_BOMB;
    if (炸弹) return BOMB;
    if (单牌) return SINGLE;
    if (对子) return PAIR;
    if (三同张) return TRIPLE;
    if (顺子) return STRAIGHT;
    if (三连对) return PAIR_STRAIGHT;
    if (钢板) return TRIPLE_STRAIGHT;
    return null; // 非法牌型
}
```

#### 3.3 牌型比较
```java
canBeat(pattern1, pattern2) {
    // 天王炸 > 炸弹 > 其他牌型
    // 同类型比较点数和张数
}
```

#### 3.4 游戏结算
```java
finishGame() {
    确定排名 → 计算升级 → 更新积分 → 保存历史
}
```

### 4. 通信模块

#### 4.1 HTTP REST API（客户端1）

**特点**:
- 无状态通信
- 4秒轮询获取状态
- 适合简单客户端

**接口设计**:
```
POST /register          - 注册
POST /login             - 登录
POST /new_game          - 创建游戏
POST /join_game/{token} - 加入游戏
GET  /get_game_state/{token} - 获取游戏状态
GET  /get_player_game_state/{token}/{player_id} - 获取玩家状态
```

#### 4.2 WebSocket（客户端2）

**特点**:
- 全双工通信
- 实时推送
- 支持复杂交互

**消息类型**:
```json
// 心跳
{"type": "ping"} → {"type": "pong"}

// 房间操作
{"roomId": "", "type": "MULTIPLE", "username": "player1"}

// 准备状态
{"state": true}

// 游戏查询
{"action": "get_cards", "username": "player1"}  // 获取手牌
{"action": "get_last_combo"}                     // 获取上一次出牌
{"action": "get_turn"}                           // 获取当前回合玩家
{"action": "get_history"}                        // 获取游戏历史记录

// 出牌/过牌
{"cards": [...]}  // 出牌
{"cards": []}     // 过牌

// 服务器推送
{"msg": "is your turn"}  // 轮到你出牌
{"seat": 0, "movement": [...]}  // 出牌广播
```

**通信模式**:
- 当玩家或Agent成功出牌后，服务器向房间内所有玩家广播 `{"seat": seat, "movement": cards}`
- 当轮到某玩家出牌时，服务器向该玩家推送 `{"msg": "is your turn"}`
- 客户端需要主动查询游戏状态（手牌、上一次出牌、当前回合等）

**连接管理**:
```java
sessions: Map<sessionId, WebSocketSession>
sessionToRoom: Map<sessionId, roomId>
```

### 5. 数据持久化模块

#### 5.1 MySQL数据库

**表结构**:

```sql
user                    # 用户表
├── id (PK)
├── username (UNIQUE)
├── password
└── created_at

game_history           # 游戏历史表
├── id (PK)
├── room_id
├── player0_id ~ player3_id
├── winner_team
├── final_rank
├── score_change
└── created_at

player_stats           # 玩家统计表
├── user_id (PK, FK)
├── total_games
├── total_wins
├── total_score
├── win_rate
└── updated_at
```

#### 5.2 Redis缓存

**数据结构**:
```
room:{roomId} → GameRoom对象
TTL: 24小时
```

**序列化**: JSON格式（Jackson）

## 数据流设计

### 创建并加入游戏流程

```
客户端1:
1. POST /new_game → RoomService.createRoom() → Redis
2. POST /join_game/{token} → RoomService.addPlayer() → Redis
3. 4位玩家加入 → GameService.initGame() → 游戏开始
4. 轮询 GET /get_player_game_state/{token}/{id}

客户端2:
1. WebSocket连接
2. 发送 {"roomId": "", "type": "MULTIPLE"} → 创建房间
3. 其他玩家发送 {"roomId": "ABC123"} → 加入房间
4. 服务器广播房间信息
5. 所有玩家准备 → 服务器广播 {"game_state": true}
6. 实时接收游戏状态更新
```

### 游戏进行流程

```
1. 玩家A出牌 → GameService.playCards()
   ├── 验证轮次
   ├── 分析牌型
   ├── 验证合法性
   ├── 更新手牌
   ├── 检查是否出完
   └── 更新当前玩家

2. 更新Redis中的GameRoom

3. 通知客户端
   ├── HTTP客户端: 下次轮询时获取
   └── WebSocket客户端: 立即广播

4. 游戏结束
   ├── 计算排名
   ├── 计算升级
   ├── 更新积分
   ├── 保存到MySQL
   └── 更新玩家统计
```

## 关键设计决策

### 1. 为什么使用Redis存储游戏状态？

**优势**:
- 高性能读写（内存操作）
- 支持复杂数据结构
- 自动过期机制
- 易于扩展（多实例共享）

**劣势**:
- 数据易失（需要持久化重要数据到MySQL）

### 2. 为什么支持两种客户端？

**HTTP客户端**:
- 简单易实现
- 无需维护连接
- 适合学习和测试

**WebSocket客户端**:
- 实时性好
- 减少网络开销
- 更好的用户体验

### 3. 房间ID为什么是6位？

- 足够的组合数（36^6 ≈ 21亿）
- 易于记忆和输入
- 避免冲突

### 4. 为什么游戏逻辑在服务端？

- 防止作弊
- 统一规则
- 便于维护
- 支持多种客户端

## 性能优化

### 1. 数据库优化

```sql
-- 索引优化
CREATE INDEX idx_username ON user(username);
CREATE INDEX idx_room ON game_history(room_id);

-- 连接池配置
spring.datasource.hikari.maximum-pool-size=20
```

### 2. Redis优化

```yaml
# 内存策略
maxmemory-policy: allkeys-lru

# 持久化
save 900 1
save 300 10
```

### 3. 应用优化

- 使用Lombok减少样板代码
- 使用@Transactional确保数据一致性
- 异步处理非关键操作

## 扩展性设计

### 水平扩展

```
负载均衡器 (Nginx)
    ├── 应用实例1
    ├── 应用实例2
    └── 应用实例3
         ↓
    共享 MySQL + Redis
```

**注意事项**:
- WebSocket需要会话粘性
- 使用Redis Pub/Sub实现跨实例通信

### 垂直扩展

- 增加服务器配置
- 优化JVM参数
- 数据库读写分离

## 安全考虑

### 1. 认证安全

- 密码应加密存储（建议使用BCrypt）
- 实现Token机制（JWT）
- 防止暴力破解

### 2. 数据安全

- SQL注入防护（MyBatis参数化查询）
- XSS防护
- CSRF防护

### 3. 通信安全

- 使用HTTPS/WSS
- 验证WebSocket连接
- 限流防护

## 监控和日志

### 日志级别

```
ERROR: 系统错误
WARN:  警告信息
INFO:  关键操作
DEBUG: 调试信息
```

### 监控指标

- 在线用户数
- 活跃房间数
- 请求响应时间
- 数据库连接池状态
- Redis内存使用

## 未来改进方向

1. **功能增强**
   - 实现完整的进贡还贡逻辑
   - 添加观战功能
   - 实现排行榜
   - 添加好友系统

2. **性能优化**
   - 实现游戏录像回放
   - 优化牌型识别算法
   - 添加缓存层

3. **用户体验**
   - 添加聊天功能
   - 实现断线重连
   - 添加游戏教程

4. **运维改进**
   - 完善监控告警
   - 自动化部署
   - 灰度发布

## 总结

本架构设计遵循以下原则：

- **分层清晰**: Controller → Service → Mapper → Database
- **职责单一**: 每个类只负责一个功能
- **易于测试**: 依赖注入，便于Mock
- **可扩展性**: 支持水平和垂直扩展
- **高性能**: Redis缓存 + 数据库持久化
- **兼容性**: 支持多种客户端类型

通过合理的架构设计，系统能够支持大量并发用户，提供稳定可靠的游戏服务。
