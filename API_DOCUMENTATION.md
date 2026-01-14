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
- `level`: 级牌点数（2-14，其中14代表2）
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
    {"color": "Spade", "number": 2, "selected": false},
    {"color": "Heart", "number": 3, "selected": false},
    {"color": "Diamond", "number": 5, "selected": false}
  ],
  "comp": [
    [{"color": "Club", "number": 7, "selected": false}],
    [],
    null,
    [{"color": "Heart", "number": 10, "selected": false}]
  ],
  "started": true,
  "player_comp": [
    [{"color": "Spade", "number": 4, "selected": false}],
    null,
    null,
    [{"color": "Diamond", "number": 8, "selected": false}]
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
    [{"color": "Spade", "number": 4, "selected": false}],
    null,
    null,
    [{"color": "Diamond", "number": 8, "selected": false}]
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
  "number": 2,         // 牌面值: 1-14 (1=A, 11=J, 12=Q, 13=K, 14=2), 15=黑王, 16=红王
  "selected": false    // UI选择状态
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

### 3. 房间管理

#### 3.1 创建房间

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

---

### 4. 准备状态

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

#### 6.1 出牌

**客户端发送**:
```json
{
  "action": "play_cards",
  "cards": [
    {"color": "Spade", "number": 2},
    {"color": "Heart", "number": 2}
  ]
}
```

**服务器广播**（游戏状态更新）:
```json
{
  "type": "game_update",
  "current_player": 1,
  "last_play": {
    "player": 0,
    "cards": [
      {"color": "Spade", "number": 2},
      {"color": "Heart", "number": 2}
    ],
    "pattern": "PAIR"
  },
  "finished_players": []
}
```

#### 6.2 过牌

**客户端发送**:
```json
{
  "action": "pass"
}
```

#### 6.3 游戏结束

**服务器广播**:
```json
{
  "type": "game_end",
  "ranks": [1, 3, 2, 4],
  "scores": [3, -3, 3, -3],
  "winner_team": 0
}
```

**字段说明**:
- `ranks`: 各玩家排名（索引对应玩家编号）
- `scores`: 各玩家得分变化
- `winner_team`: 获胜队伍（0或2代表0-2队，1或3代表1-3队）

---

## 数据模型

### 卡牌编号对照表

| number | 牌面 | 说明 |
|--------|------|------|
| 1 | A | Ace |
| 2-10 | 2-10 | 数字牌 |
| 11 | J | Jack |
| 12 | Q | Queen |
| 13 | K | King |
| 14 | 2 | 2（最小的牌） |
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
| KING_BOMB | 天王炸 | 4张王 |
| PASS | 过牌 | 不出牌 |

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

- **v0.0.1** (2026-01-14): 初始版本
  - 实现基础认证功能
  - 实现HTTP REST API
  - 实现WebSocket通信
  - 实现核心游戏逻辑
