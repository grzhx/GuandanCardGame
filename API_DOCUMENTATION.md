# Guandan Game Server API Documentation

## 概述

本文档详细描述了掼蛋游戏服务器的所有API接口，包括HTTP REST API（客户端1）和WebSocket API（客户端2）。

## 基础信息

- **服务器地址**: `http://localhost:8080`
- **WebSocket地址**: `ws://localhost:8080/ws`
- **数据格式**: JSON
- **字符编码**: UTF-8

---

## HTTP REST API（客户端1）

### 1. 用户认证

#### 1.1 注册

**接口**: `POST /register`

**请求参数**:
```json
{
  "username": "player1",
  "password": "mypassword123",
  "confirmation": "mypassword123"
}
```

**成功响应** (200):
```json
{
  "username": "player1"
}
```

**错误响应**:

| 错误情况 | 响应 |
|---------|------|
| 缺少用户名 | `{"error": "Must provide username"}` |
| 用户名已存在 | `{"error": "Username already exists"}` |
| 缺少密码 | `{"error": "Must provide password"}` |
| 缺少确认密码 | `{"error": "Must provide confirmation of password"}` |
| 密码不匹配 | `{"error": "Passwords do not match"}` |

#### 1.2 登录

**接口**: `POST /login`

**请求参数**:
```json
{
  "username": "player1",
  "password": "mypassword123"
}
```

**成功响应** (200):
```json
{
  "username": "player1"
}
```

**错误响应**:

| 错误情况 | 响应 |
|---------|------|
| 缺少用户名 | `{"error": "Must provide username"}` |
| 缺少密码 | `{"error": "Must provide password"}` |
| 凭证无效 | `{"error": "invalid username and/or password"}` |

---

### 2. 游戏管理

#### 2.1 创建游戏

**接口**: `POST /new_game`

**描述**: 创建一个单局游戏房间（SINGLE类型）

**请求参数**:
```json
{
  "level": 2,
  "experimental": false
}
```

**参数说明**:
- `level`: 级牌点数（2-14，其中14代表A）
- `experimental`: 实验性功能标志（暂无用途）

**成功响应** (200):
```json
{
  "token": "ABC123"
}
```

**响应说明**:
- `token`: 6位字符的房间ID

#### 2.2 加入游戏

**接口**: `POST /join_game/{token}`

**路径参数**:
- `token`: 房间ID

**请求参数**:
```json
{
  "username": "player1",
  "token": "ABC123"
}
```

**成功响应** (200):
```json
{
  "player_number": 0
}
```

**响应说明**:
- `player_number`: 玩家座位号（0-3）

**注意事项**:
- 当第4位玩家加入时，游戏自动开始
- 如果用户名不存在，系统会自动创建账号

#### 2.3 获取游戏状态

**接口**: `GET /get_game_state/{token}`

**路径参数**:
- `token`: 房间ID

**用途**: 获取游戏的通用状态信息，主要用于命令行版本或简单的状态查询

**响应（游戏进行中）**:
```json
{
  "current_player": 2,
  "started": true,
  "game_state": "0: 2 of Spade, 1: 3 of Heart, 2: 5 of Diamond..."
}
```

**响应（游戏结束）**:
```json
{
  "finished": true,
  "rank": "[1] Player Alice\n[2] Player Bob\n[3] Player Carol\n[4] Player Dave\n"
}
```

**字段说明**:
- `current_player`: 当前回合的玩家编号（0-3）
- `started`: 游戏是否已开始
- `game_state`: 字符串格式的玩家手牌信息
- `finished`: 游戏是否结束
- `rank`: 最终排名信息

#### 2.4 获取玩家游戏状态

**接口**: `GET /get_player_game_state/{token}/{player_id}`

**路径参数**:
- `token`: 房间ID
- `player_id`: 玩家编号（0-3）

**用途**: 获取特定玩家的详细游戏状态，这是前端轮询使用的主要接口

**轮询频率**: 每4秒一次

**响应（游戏活跃状态）**:
```json
{
  "turn": 2,
  "deck": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 3},
    {"color": "Diamond", "number": 5}
  ],
  "comp": [
    [{"color": "Club", "number": 7}],
    [],
    null,
    [{"color": "Heart", "number": 10}]
  ],
  "started": true,
  "player_comp": [
    [{"color": "Spade", "number": 4}],
    null,
    null,
    [{"color": "Diamond", "number": 8}]
  ],
  "finished_players": [0],
  "finished": false,
  "paused": false
}
```

**响应（游戏暂停状态）**:
```json
{
  "started": true,
  "paused": true,
  "player_comp": [
    [{"color": "Spade", "number": 4}],
    null,
    null,
    [{"color": "Diamond", "number": 8}]
  ]
}
```

**响应（游戏结束状态）**:
```json
{
  "finished": true,
  "rank": [1, 3, 2, 4]
}
```

**字段详解**:

| 字段 | 类型 | 说明 |
|-----|------|------|
| `turn` | int | 当前轮到的玩家编号 |
| `deck` | array | 该玩家的手牌数组 |
| `comp` | array | 上一次各玩家打出的牌组 |
| `started` | boolean | 游戏是否已开始 |
| `player_comp` | array | 所有玩家当前回合的牌组 |
| `finished_players` | array | 已完成游戏的玩家列表 |
| `finished` | boolean | 游戏是否结束 |
| `paused` | boolean | 游戏是否暂停 |
| `rank` | array | 最终排名数组 |

**卡牌对象结构**:
```json
{
  "color": "Spade",    // 花色: Spade, Club, Heart, Diamond, Joker
  "number": 2          // 牌面值: 1-13 (1=A, 2-10=2-10, 11=J, 12=Q, 13=K), 15=黑王, 16=红王
}
```

---

## WebSocket API（客户端2）

### 连接

**端点**: `ws://localhost:8080/ws`

**协议**: WebSocket

**数据格式**: JSON

### 1. 心跳检测

**频率**: 每30秒

**客户端发送**:
```json
{
  "type": "ping"
}
```

**服务器响应**:
```json
{
  "type": "pong"
}
```

**说明**: 用于保持WebSocket连接活跃，防止超时断开

---

### 2. 获取个人数据

**客户端发送**:
```json
{
  "action": "get_data",
  "username": "player1"
}
```

**服务器响应**:
```json
{
  "rate": 65.5,
  "game_num": 100,
  "score": 250,
  "game_list": []
}
```

**字段说明**:
- `rate`: 胜率（百分比）
- `game_num`: 总游戏场次
- `score`: 总积分
- `game_list`: 游戏历史列表（暂未实现详细数据）

---

### 3.匹配系统

#### 3.1 进入匹配队列

**客户端发送**:
```json
{
  "action": "matchmaking",
  "username": "player1"
}
```

**参数说明**:
- `username`: 玩家用户名

**服务器响应**（进入队列成功）:
```json
{
  "type": "matchmaking",
  "status": "queued",
  "position": 2
}
```

**字段说明**:
- `type`: 消息类型，固定为 "matchmaking"
- `status`: 状态，"queued" 表示已进入队列
- `position`: 当前队列中的位置

**服务器响应**（匹配成功）:
```json
{
  "type": "matchmaking",
  "status": "matched",
  "roomId": "ABC123"
}
```

**字段说明**:
- `status`: 状态，"matched" 表示匹配成功
- `roomId`: 匹配到的房间ID

**错误响应**:
```json
{
  "error": "Already in a room"
}
```

或

```json
{
  "error": "Already in matchmaking queue"
}
```

**匹配流程**:
1. 玩家发送匹配请求进入队列
2. 服务器返回排队确认消息
3. 当队列中有4个玩家时，服务器自动创建匹配房间
4. 服务器向4个玩家发送匹配成功消息
5. 服务器广播房间信息
6. 游戏自动开始（所有玩家默认已准备）

**匹配房间特点**:
- 房间类型为MULTIPLE（多局游戏）
- 无房主（hostId 为 -1）
- 默认等级为 2
- 所有玩家自动设为准备状态
- 匹配成功后立即开始游戏

#### 3.2 取消匹配

**客户端发送**:
```json
{
  "action": "cancel_matchmaking",
  "username": "player1"
}
```

**参数说明**:
- `username`: 玩家用户名

**服务器响应**（取消成功）:
```json
{
  "type": "matchmaking",
  "status": "cancelled"
}
```

**错误响应**:
```json
{
  "error": "Not in matchmaking queue"
}
```

**说明**:
- 只有在匹配队列中的玩家才能取消匹配
- 取消后玩家从队列中移除
- 如果已经匹配成功并进入房间，则无法取消

---

### 4. 房间管理

#### 4.1 创建房间

**客户端发送**:
```json
{
  "roomId": "",
  "type": "MULTIPLE",
  "username": "player1"
}
```

**参数说明**:
- `roomId`: 空字符串表示创建新房间
- `type`: 游戏类型
  - `MULTIPLE`: 多局游戏（完整规则）
  - `SINGLE`: 单局游戏（仅一小局）
- `username`: 玩家用户名

**创建单局房间**:
```json
{
  "roomId": "",
  "type": "SINGLE",
  "level": 2,
  "username": "player1"
}
```

**服务器响应**:
```json
{
  "token": "ABC123"
}
```

#### 3.2 加入房间

**客户端发送**:
```json
{
  "roomId": "ABC123",
  "type": "MULTIPLE",
  "username": "player2"
}
```

**成功响应**: 服务器向房间内所有玩家广播房间信息

**错误响应**:
```json
{
  "code": 3001,
  "msg": "Room not found"
}
```

或

```json
{
  "code": 3001,
  "msg": "Room is full"
}
```

#### 3.3 房间信息广播

**服务器广播**（当玩家加入或状态改变时）:
```json
{
  "roomId": "ABC123",
  "hostId": 101,
  "players": [
    {
      "seat": 0,
      "uid": 101,
      "ready": true,
      "online": true
    },
    {
      "seat": 1,
      "uid": 102,
      "ready": true,
      "online": true
    },
    {
      "seat": 2,
      "uid": 0,
      "ready": false,
      "online": false
    },
    {
      "seat": 3,
      "uid": 0,
      "ready": false,
      "online": false
    }
  ]
}
```

**字段说明**:
- `roomId`: 房间ID
- `hostId`: 房主用户ID
- `players`: 玩家列表（4个座位）
  - `seat`: 座位号（0-3）
  - `uid`: 用户ID（0表示空位）
  - `ready`: 准备状态
  - `online`: 在线状态

#### 4.4 添加 Agent

**客户端发送**:
```json
{
  "action": "add_agent",
  "username": "player1"
}
```

**参数说明**:
- `action`: 固定为 "add_agent"
- `username`: 发送者用户名（必须是房主）

**成功响应**: 服务器向房间内所有玩家广播更新后的房间信息

**错误响应**:

| 错误情况 | 响应 |
|---------|------|
| 用户不在房间中 | `{"error": "Not in a room"}` |
| 不是房主 | `{"error": "Only host can add agent"}` |
| 匹配房间 | `{"error": "Cannot add agent in match room"}` |
| 房间已满 | `{"error": "Room is full"}` |

**说明**:
- 只有房主可以添加 Agent
- 匹配房间（hostId 为 -1）不能添加 Agent
- Agent 添加后自动设置为准备状态
- 如果添加 Agent 后房间满员且所有玩家都准备，游戏自动开始
- 可以添加多个 Agent

#### 4.5 移除 Agent

**客户端发送**:
```json
{
  "action": "remove_agent",
  "username": "player1"
}
```

**参数说明**:
- `action`: 固定为 "remove_agent"
- `username`: 发送者用户名（必须是房主）

**成功响应**: 服务器向房间内所有玩家广播更新后的房间信息

**错误响应**:

| 错误情况 | 响应 |
|---------|------|
| 用户不在房间中 | `{"error": "Not in a room"}` |
| 不是房主 | `{"error": "Only host can remove agent"}` |
| 匹配房间 | `{"error": "Cannot remove agent in match room"}` |
| 房间内没有 Agent | `{"error": "No agent in room"}` |

**说明**:
- 只有房主可以移除 Agent
- 匹配房间不能移除 Agent
- 每次移除一个 Agent（自动选择第一个找到的）
- 移除后会广播更新的房间信息

---

### 5. 准备状态

**客户端发送**:
```json
{
  "state": true
}
```

**参数说明**:
- `state`: `true`表示准备，`false`表示取消准备

**服务器响应**: 广播更新后的房间信息

**注意事项**:
- 玩家加入时默认为准备状态（`ready: true`）
- 当所有4位玩家都准备时，游戏自动开始

---

### 5. 游戏开始

**服务器广播**（当所有玩家准备时）:
```json
{
  "game_state": true
}
```

**说明**: 此消息表示游戏已开始，客户端应切换到游戏界面

---

### 6. 游戏进行中的消息

#### 6.1 获取手牌

**客户端发送**:
```json
{
  "action": "get_cards",
  "username": "player1",
  "roomId": "ABC123"
}
```

**参数说明**:
- `username`: 玩家用户名（必需）
- `roomId`: 房间ID（可选，如果session已关联房间则可省略）

**服务器响应**:
```json
{
  "cards": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 3}
  ],
  "username": "player1"
}
```

#### 6.2 获取上一次出牌

**客户端发送**:
```json
{
  "action": "get_last_combo",
  "roomId": "ABC123"
}
```

**参数说明**:
- `roomId`: 房间ID（可选，如果session已关联房间则可省略）

**服务器响应**（有上一次出牌时）:
```json
{
  "cards": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 2}
  ],
  "pattern": "PAIR"
}
```

**服务器响应**（初始或3人都过牌后）:
```json
{
  "cards": [],
  "pattern": null
}
```

#### 6.3 获取当前回合玩家

**客户端发送**:
```json
{
  "action": "get_turn",
  "roomId": "ABC123"
}
```

**参数说明**:
- `roomId`: 房间ID（可选，如果session已关联房间则可省略）

**服务器响应**:
```json
{
  "seat": 2
}
```

#### 6.4 获取游戏历史记录

**客户端发送**:
```json
{
  "action": "get_history",
  "roomId": "ABC123"
}
```

**参数说明**:
- `roomId`: 房间ID（可选，如果session已关联房间则可省略）

**服务器响应**:
```json
{
  "game1": [
    {"seat": 0, "movement": [{"color": "Spade", "number": 5}]},
    {"seat": 1, "movement": []},
    {"seat": 2, "movement": [{"color": "Heart", "number": 7}]}
  ]
}
```

**说明**:
- `game1` 表示第一小局游戏
- `movement` 为空数组表示过牌
- 单局房间中只包含一小局游戏记录

#### 6.5 出牌

**客户端发送**:
```json
{
  "cards": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 2}
  ]
}
```

**服务器响应**（出牌成功）:
```json
{
  "cards": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 2}
  ],
  "username": "player1"
}
```

**服务器响应**（出牌失败）:
```json
{
  "error": "Invalid play"
}
```

**服务器响应**（不是你的回合）:
```json
{
  "error": "Not your turn"
}
```

#### 6.6 过牌

**客户端发送**:
```json
{
  "cards": []
}
```

**说明**: 发送空的cards数组表示过牌

#### 6.7 轮到你出牌通知

**服务器推送**（当轮到该玩家出牌时）:
```json
{
  "msg": "is your turn"
}
```

**说明**: 
- 游戏开始时，服务器会向第一个出牌的玩家发送此消息
- 每次有玩家出牌后，服务器会向下一个应该出牌的玩家发送此消息

#### 6.8 出牌广播

**服务器广播**（当任意玩家成功出牌时）:
```json
{
  "seat": 0,
  "movement": [
    {"color": "Spade", "number": 5},
    {"color": "Heart", "number": 5}
  ],
  "pattern": "PAIR"
}
```

**说明**:
- 当玩家或 Agent 成功出牌后，服务器会向房间内所有玩家广播此消息
- `seat`: 出牌玩家的座位号（0-3）
- `movement`: 打出的牌，空数组表示过牌
- `pattern`: 出牌的牌型类型（见下方牌型说明）

**牌型类型**:
- `SINGLE`: 单牌
- `PAIR`: 对子
- `TRIPLE`: 三同张
- `STRAIGHT`: 顺子
- `PAIR_STRAIGHT`: 三连对
- `TRIPLE_STRAIGHT`: 钢板
- `FULLHOUSE`: 葫芦
- `BOMB`: 炸弹
- `STRAIGHT_FLUSH`: 同花顺
- `KING_BOMB`: 天王炸
- `PASS`: 过牌（此时 movement 为空数组）

#### 6.9 玩家出完牌通知

**服务器广播**（当玩家打出最后一张牌时）:
```json
{
  "msg": "someone clear his hand",
  "seat": 0,
  "username": "player1"
}
```

**说明**:
- 当玩家手牌全部打完时，服务器会向房间内所有玩家广播此消息
- `msg`: 固定消息 "someone clear his hand"
- `seat`: 出完牌的玩家座位号（0-3）
- `username`: 出完牌的玩家用户名

#### 6.10 接风通知

**服务器广播**（当触发接风时）:
```json
{
  "msg": "turn change",
  "seat": 2
}
```

**说明**:
- 当某位玩家出完牌后，其他三人均过牌，轮到该玩家的队友出任意牌时触发
- `msg`: 固定消息 "turn change"
- `seat`: 接风玩家的座位号（队友座位）

**触发条件**:
1. 某位玩家已经出完所有手牌
2. 其他玩家连续三次过牌（passCount重置为0，lastPattern为null）
3. 当前轮到该玩家的队友出牌

#### 6.11 手牌数量警告

**服务器广播**（当玩家出牌后手牌数量小于10张时）:
```json
{
  "msg": "hand cards warning",
  "seat": 0,
  "cards_num": 5
}
```

**说明**:
- 当玩家（包括Agent）出牌后，手牌数量小于10张时，服务器会向房间内所有玩家广播此消息
- `msg`: 固定消息 "hand cards warning"
- `seat`: 玩家座位号（0-3）
- `cards_num`: 剩余手牌数量（0-9）

**触发时机**:
- 每次出牌后自动检查
- 包括手牌为0的情况（此时会同时触发 "someone clear his hand" 消息）

#### 6.12 快捷聊天

**客户端发送**:
```json
{
  "msg": "quick_chat",
  "seat": 0,
  "text": "你好！"
}
```

**参数说明**:
- `msg`: 固定为 "quick_chat"
- `seat`: 发送者的座位号
- `text`: 聊天内容

**服务器广播**:
```json
{
  "msg": "quick_chat",
  "seat": 0,
  "text": "你好！"
}
```

**说明**:
- 服务器收到快捷聊天消息后，会将其广播给房间内所有玩家
- 用于实现游戏内的快速交流功能

#### 6.13 每一小局结束

**服务器广播**（当一小局游戏结束时）:
```json
{
  "type": "game_end",
  "ranks": [1, 3, 2, 4],
  "scores": [5, -5, 5, -5],
  "winner_team": 0,
  "final_level": 14
}
```

**字段说明**:
- `type`: 消息类型，固定为 "game_end"
- `ranks`: 本局各玩家排名（索引对应玩家编号，1-4）
- `scores`: 各玩家当前总分
- `winner_team`: 本局获胜队伍（0或2代表0-2队，1或3代表1-3队）
- `final_level`: 当前等级（2-14，14代表A级）

**计分规则**:
- **双下（1、2名）**：获胜队伍各得3分，失败队伍各扣3分，等级升3级
- **单下（1、3名）**：获胜队伍各得2分，失败队伍各扣2分，等级升2级
- **其他情况（1、4名）**：获胜队伍各得1分，失败队伍各扣1分，等级升1级

#### 6.12 游戏完全结束

**服务器广播**（当等级达到A级后游戏结束时）:
```json
{
  "msg": "game end in this room"
}
```

**说明**:
- 当房间等级达到14（A级）后，如果再升级会超过14，则等级固定在14，游戏结束
- 此消息在最后一小局结束消息之后立即发送
- 游戏结束后，房间不再接受新的游戏操作

---

## 数据模型

### 卡牌编号对照表

| number | 牌面 | 说明 |
|--------|------|------|
| 1 | A | Ace |
| 2 | 2 | 2（最小的牌） |
| 3-10 | 3-10 | 数字牌 |
| 11 | J | Jack |
| 12 | Q | Queen |
| 13 | K | King |
| 15 | 黑王 | Black Joker |
| 16 | 红王 | Red Joker |

### 花色对照表

| color | 花色 |
|-------|------|
| Spade | 黑桃 ♠ |
| Club | 梅花 ♣ |
| Heart | 红桃 ♥ |
| Diamond | 方块 ♦ |
| Joker | 王 |

### 牌型类型

| PatternType | 说明 | 示例 |
|-------------|------|------|
| SINGLE | 单牌 | 一张牌 |
| PAIR | 对子 | 两张相同点数 |
| TRIPLE | 三同张 | 三张相同点数 |
| STRAIGHT | 顺子 | 5张及以上连续 |
| PAIR_STRAIGHT | 三连对 | 3组及以上连续对子 |
| TRIPLE_STRAIGHT | 钢板 | 2组及以上连续三同张 |
| BOMB | 炸弹 | 4张及以上相同点数 |
| STRAIGHT_FLUSH | 同花顺 | 5张及以上同花色连续 |
| KING_BOMB | 天王炸 | 4张王 |
| PASS | 过牌 | 不出牌 |

**牌型大小规则**:
- 炸弹比较：先比较张数，张数多的大；张数相同时比较点数
- 同花顺 vs 炸弹：同花顺大于4张炸弹，小于5张及以上炸弹
- 天王炸 > 所有炸弹和同花顺

---

## 错误码

| 错误码 | 说明 |
|-------|------|
| 3001 | 房间相关错误（不存在、已满等） |
| 3002 | 游戏逻辑错误（非法操作等） |
| 4001 | 认证错误 |
| 5000 | 服务器内部错误 |

---

## 使用示例

### 客户端1（HTTP）完整流程

```javascript
// 1. 注册
fetch('http://localhost:8080/register', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    username: 'player1',
    password: 'pass123',
    confirmation: 'pass123'
  })
});

// 2. 创建游戏
const gameRes = await fetch('http://localhost:8080/new_game', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({level: 2, experimental: false})
});
const {token} = await gameRes.json();

// 3. 加入游戏
await fetch(`http://localhost:8080/join_game/${token}`, {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({username: 'player1', token})
});

// 4. 轮询游戏状态（每4秒）
setInterval(async () => {
  const state = await fetch(`http://localhost:8080/get_player_game_state/${token}/0`);
  const data = await state.json();
  console.log(data);
}, 4000);
```

### 客户端2（WebSocket）完整流程

```javascript
// 1. 建立连接
const ws = new WebSocket('ws://localhost:8080/ws');

ws.onopen = () => {
  // 2. 创建房间
  ws.send(JSON.stringify({
    roomId: '',
    type: 'MULTIPLE',
    username: 'player1'
  }));
  
  // 3. 心跳检测（每30秒）
  setInterval(() => {
    ws.send(JSON.stringify({type: 'ping'}));
  }, 30000);
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.type === 'pong') {
    console.log('心跳响应');
  } else if (data.token) {
    console.log('房间创建成功:', data.token);
  } else if (data.game_state) {
    console.log('游戏开始');
  }
};

// 4. 获取个人数据
ws.send(JSON.stringify({
  action: 'get_data',
  username: 'player1'
}));

// 5. 改变准备状态
ws.send(JSON.stringify({
  state: true
}));
```

---

## 注意事项

1. **HTTP客户端**：需要每4秒轮询一次游戏状态
2. **WebSocket客户端**：需要每30秒发送一次心跳
3. **房间过期**：Redis中的房间数据默认24小时后过期
4. **自动创建账号**：如果用户名不存在，加入游戏时会自动创建
5. **游戏自动开始**：当4位玩家都准备时，游戏自动开始
6. **断线重连**：WebSocket断开后需要重新连接并加入房间

---

## 版本历史

- **v0.0.6** (2026-01-19): 新功能
  - 新增手牌数量警告功能：玩家出牌后手牌小于10张时自动广播警告
  - 新增快捷聊天功能：支持发送和广播快捷聊天消息
  - 新增房间内添加/移除 Agent 功能（仅房主可操作，匹配房间不支持）
  - Agent 添加后自动设置为准备状态

- **v0.0.5** (2026-01-19): 匹配系统
  - 新增匹配功能，玩家可通过 `matchmaking` action 进入匹配队列
  - 当队列中有4个玩家时，自动创建匹配房间并开始游戏
  - 匹配房间无房主，所有玩家自动准备
  - 支持取消匹配功能（`cancel_matchmaking` action）
  - 匹配房间默认为多局游戏模式（MULTIPLE），等级为2

- **v0.0.4** (2026-01-19): 出牌广播增加牌型字段
  - 出牌广播消息中新增 `pattern` 字段，表示出牌玩家的牌型
  - 支持的牌型包括：SINGLE、PAIR、TRIPLE、STRAIGHT、PAIR_STRAIGHT、TRIPLE_STRAIGHT、FULLHOUSE、BOMB、STRAIGHT_FLUSH、KING_BOMB、PASS
  - 玩家和 Agent 出牌时均会在广播中包含牌型信息

- **v0.0.3** (2026-01-18): 计分系统和游戏结束逻辑
  - 实现完整的计分系统（双下3分、单下2分、其他1分）
  - 添加游戏等级管理（2-14级，14为A级上限）
  - 每一小局结束时广播详细结束信息（type: "game_end"）
  - 游戏完全结束时广播结束消息（msg: "game end in this room"）
  - 等级达到A级后游戏自动结束

- **v0.0.2** (2026-01-16): 通信协议优化
  - 新增 `get_cards` action 获取手牌
  - 新增 `get_last_combo` action 获取上一次出牌
  - 新增 `get_turn` action 获取当前回合玩家
  - 新增 `get_history` action 获取游戏历史记录
  - 向当前玩家推送 "is your turn" 通知
  - 出牌成功后向所有玩家广播 `{"seat": seat, "movement": cards}`
  - 添加游戏历史记录功能

- **v0.0.1** (2026-01-14): 初始版本
  - 实现基础认证功能
  - 实现HTTP REST API
  - 实现WebSocket通信
  - 实现核心游戏逻辑
