# Agent集成说明

## 概述

本项目已将AI Agent机制从本地决策改为通过WebSocket连接到外部Agent服务器。

## 架构变更

### 删除的组件
- `AgentService.java` - 本地AI决策逻辑已删除
- `RoomService` 中的 `autoAgent` 配置和 `fillWithAgents()` 方法

### 新增组件
- `AgentWebSocketClient.java` - 负责与Agent服务器通信的WebSocket客户端

### 修改的组件
- `GameWebSocketHandler.java` - 使用新的AgentWebSocketClient替代AgentService
- `RoomService.java` - 移除了自动填充Agent的逻辑
- `application.yml` - 添加了Agent服务器配置

## Agent服务器通信协议

### 请求格式
游戏服务器向Agent服务器发送：
```json
{
  "msg": "ai_call",
  "level": 2,
  "last_move": [
    {"color": "♠", "number": 3}
  ],
  "your_cards": [
    {"color": "♥", "number": 5},
    {"color": "♦", "number": 7}
  ],
  "request_id": "uuid-string"
}
```

### 响应格式
Agent服务器返回：
```json
{
  "action": "play_cards",
  "cards": [
    {"color": "♥", "number": 5}
  ],
  "request_id": "uuid-string"
}
```

### 错误处理
- 如果Agent服务器未连接，AI自动pass（出空牌）
- 如果请求超时（默认5秒），AI自动pass
- 连接断开后会自动重连（每5秒尝试一次）

## "AGENT"房间功能

当玩家尝试加入ID为"AGENT"的房间时：
1. 系统自动创建该房间
2. 玩家占据seat 0
3. 自动创建3个AI玩家（seat 1, 2, 3）
4. 所有玩家自动ready
5. 立即开始游戏

这允许玩家快速开始与AI对战。

## 配置

在 `application.yml` 中配置Agent服务器：

```yaml
agent:
  server:
    url: ws://192.168.1.4:8000/ws  # Agent服务器WebSocket地址
    timeout: 5000                   # 请求超时时间（毫秒）
```

## Agent服务器要求

Agent服务器需要：
1. 提供WebSocket端点（当前配置：`ws://192.168.1.4:8080/ws`）
2. 接收上述格式的请求消息
3. 返回包含`request_id`和`cards`的响应
4. 响应时间应在5秒内

## 游戏流程

1. 轮到AI玩家时，`triggerAgentIfNeeded()` 被调用
2. 通过 `AgentWebSocketClient.requestAgentPlay()` 异步请求Agent服务器
3. Agent服务器返回出牌决策
4. 游戏服务器执行AI的出牌动作
5. 广播给所有玩家
6. 继续游戏流程

## 开发注意事项

- Agent请求是异步的，使用CompletableFuture处理
- 需要重新获取最新的room状态以避免并发问题
- AI出牌触发与正常玩家相同的游戏流程和广播
- 保持了原有的GameRoom.Player结构，agent字段用于标识AI玩家

## 测试

1. 启动Agent服务器（在配置的URL上）
2. 启动游戏服务器
3. 玩家加入roomId="AGENT"
4. 观察AI玩家的出牌行为

如果Agent服务器未启动，AI将自动pass所有回合。
