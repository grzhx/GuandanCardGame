# Guandan Game Server

基于Spring Boot、MySQL、Redis的掼蛋游戏后端服务器，支持HTTP REST API和WebSocket两种客户端通信方式。

## 技术栈

- Spring Boot 4.0.1
- MySQL 8.0+
- Redis 6.0+
- MyBatis 4.0.1
- WebSocket
- Lombok

## 项目结构

```
GuandanGame/
├── src/main/java/com/example/guandan/
│   ├── GuandanGameApplication.java          # 主应用入口
│   ├── config/
│   │   ├── RedisConfig.java                 # Redis配置
│   │   └── WebSocketConfig.java             # WebSocket配置
│   ├── controller/
│   │   ├── AuthController.java              # 认证控制器（登录/注册）
│   │   └── GameController.java              # 游戏控制器（HTTP API）
│   ├── entity/
│   │   ├── User.java                        # 用户实体
│   │   ├── GameHistory.java                 # 游戏历史实体
│   │   └── PlayerStats.java                 # 玩家统计实体
│   ├── handler/
│   │   └── GameWebSocketHandler.java        # WebSocket处理器
│   ├── mapper/
│   │   ├── UserMapper.java                  # 用户数据访问
│   │   ├── GameHistoryMapper.java           # 游戏历史数据访问
│   │   └── PlayerStatsMapper.java           # 玩家统计数据访问
│   ├── model/
│   │   ├── Card.java                        # 扑克牌模型
│   │   ├── CardPattern.java                 # 牌型模型
│   │   └── GameRoom.java                    # 游戏房间模型
│   └── service/
│       ├── GameService.java                 # 游戏逻辑服务
│       ├── RoomService.java                 # 房间管理服务
│       └── UserService.java                 # 用户服务
└── src/main/resources/
    ├── application.yml                      # 应用配置
    └── schema.sql                           # 数据库脚本
```

## 快速开始

### 1. 环境准备

确保已安装：
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+

### 2. 数据库配置

执行SQL脚本创建数据库和表：

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 配置文件

修改 `src/main/resources/application.yml` 中的数据库和Redis连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guandan
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 4. 启动服务

```bash
mvn spring-boot:run
```

服务将在 `http://localhost:8080` 启动。

## API 文档

### 客户端1 - HTTP REST API

#### 1. 用户注册

**POST** `/register`

请求体：
```json
{
  "username": "player1",
  "password": "mypassword123",
  "confirmation": "mypassword123"
}
```

成功响应：
```json
{
  "username": "player1"
}
```

错误响应：
```json
{
  "error": "Username already exists"
}
```

#### 2. 用户登录

**POST** `/login`

请求体：
```json
{
  "username": "player1",
  "password": "mypassword123"
}
```

成功响应：
```json
{
  "username": "player1"
}
```

#### 3. 创建游戏

**POST** `/new_game`

请求体：
```json
{
  "level": 2,
  "experimental": false
}
```

响应：
```json
{
  "token": "ABC123"
}
```

#### 4. 加入游戏

**POST** `/join_game/{token}`

请求体：
```json
{
  "username": "player1",
  "token": "ABC123"
}
```

响应：
```json
{
  "player_number": 0
}
```

#### 5. 获取游戏状态

**GET** `/get_game_state/{token}`

响应（游戏进行中）：
```json
{
  "current_player": 2,
  "started": true,
  "game_state": "0: 2 of Spade, 1: 3 of Heart..."
}
```

响应（游戏结束）：
```json
{
  "finished": true,
  "rank": "[1] Player Alice\n[2] Player Bob\n..."
}
```

#### 6. 获取玩家游戏状态

**GET** `/get_player_game_state/{token}/{player_id}`

响应（游戏活跃）：
```json
{
  "turn": 2,
  "deck": [
    {"color": "Spade", "number": 2, "selected": false}
  ],
  "comp": [[], null, [], []],
  "started": true,
  "player_comp": [[], null, [], []],
  "finished_players": [0],
  "finished": false,
  "paused": false
}
```

### 客户端2 - WebSocket

#### 连接

WebSocket端点：`ws://localhost:8080/ws`

#### 1. 心跳检测

发送：
```json
{
  "type": "ping"
}
```

接收：
```json
{
  "type": "pong"
}
```

#### 2. 获取个人数据

发送：
```json
{
  "action": "get_data",
  "username": "player1"
}
```

接收：
```json
{
  "rate": 0,
  "game_num": 0,
  "score": 0,
  "game_list": []
}
```

#### 3. 创建/加入房间

创建房间（发送）：
```json
{
  "roomId": "",
  "type": "MULTIPLE",
  "username": "player1"
}
```

加入房间（发送）：
```json
{
  "roomId": "ABC123",
  "type": "MULTIPLE",
  "username": "player1"
}
```

接收（创建成功）：
```json
{
  "token": "ABC123"
}
```

接收（房间信息广播）：
```json
{
  "roomId": "ABC123",
  "hostId": 101,
  "players": [
    {"seat": 0, "uid": 101, "ready": true, "online": true},
    {"seat": 1, "uid": 0, "ready": false, "online": false}
  ]
}
```

#### 4. 准备状态

发送：
```json
{
  "state": true
}
```

#### 5. 游戏开始

接收：
```json
{
  "game_state": true
}
```

## 游戏规则

### 基础规则

- 4名玩家，分为2个阵营（0-2为队友，1-3为队友）
- 两副扑克牌（108张）
- 初始级牌为2，升级顺序：2→3→...→A
- 打A为必过局，需双下才能通关

### 牌型

1. **单牌**：一张牌
2. **对子**：两张相同点数的牌
3. **三同张**：三张相同点数的牌
4. **顺子**：5张及以上连续点数的牌
5. **三连对**：3组及以上连续的对子
6. **钢板**：2组及以上连续的三同张
7. **炸弹**：4张及以上相同点数的牌
8. **天王炸**：4张王（2大2小）

### 特殊规则

- **级牌**：当前级别的牌，大小高于普通牌
- **红桃级牌**：万能牌，可替代任意牌（除王）
- **进贡还贡**：根据上局排名进行贡牌
- **抗贡**：持有两张大王可免贡

### 升级规则

- 双下（上游+二游）：升3级
- 上游+三游：升2级
- 上游+下游：升1级
- 过A：需双下，成功后额外+10分

## 开发说明

### 添加新功能

1. 在对应的service中添加业务逻辑
2. 在controller或handler中添加接口
3. 更新文档

### 测试

```bash
mvn test
```

### 打包部署

```bash
mvn clean package
java -jar target/GuandanGame-0.0.1-SNAPSHOT.jar
```

## 注意事项

1. 确保MySQL和Redis服务正常运行
2. 客户端1使用4秒轮询获取游戏状态
3. 客户端2使用WebSocket，需每30秒发送心跳
4. 游戏房间数据存储在Redis中，默认24小时过期
5. 游戏历史和统计数据持久化到MySQL

## 许可证

MIT License
