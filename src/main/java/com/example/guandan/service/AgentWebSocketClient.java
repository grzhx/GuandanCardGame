package com.example.guandan.service;

import com.example.guandan.model.Card;
import com.example.guandan.model.GameRoom;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class AgentWebSocketClient {
    
    @Value("${agent.server.url:ws://localhost:8081/agent}")
    private String agentServerUrl;
    
    @Value("${agent.server.timeout:5000}")
    private long timeout;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketSession session;
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final Map<String, CompletableFuture<List<Card>>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final TaskExecutor taskExecutor;
    
    @Autowired
    public AgentWebSocketClient(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }
    
    @PostConstruct
    public void connect() {
        try {
            log.info("Connecting to agent server: {}", agentServerUrl);
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            session = client.execute(new AgentWebSocketHandler(), headers, URI.create(agentServerUrl)).get(10, TimeUnit.SECONDS);
            log.info("Connected to agent server successfully: {}", agentServerUrl);
        } catch (Exception e) {
            log.error("Failed to connect to agent server: {}", e.getMessage());
            // 启动重连任务
            scheduleReconnect();
        }
    }
    
    @PreDestroy
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                log.error("Error closing agent connection", e);
            }
        }
        scheduler.shutdown();
    }
    
    private void scheduleReconnect() {
        scheduler.schedule(() -> {
            if (session == null || !session.isOpen()) {
                connect();
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 请求Agent出牌
     * @param room 游戏房间
     * @param seat Agent座位号
     * @return Agent出的牌，如果失败返回空列表（表示pass）
     */
    public CompletableFuture<List<Card>> requestAgentPlay(GameRoom room, int seat) {
        CompletableFuture<List<Card>> future = new CompletableFuture<>();
        
        // 检查连接状态
        if (session == null || !session.isOpen()) {
            log.warn("Agent server not connected, auto pass");
            future.complete(new ArrayList<>());
            return future;
        }
        
        try {
            GameRoom.Player agent = room.getPlayers()[seat];
            List<Card> yourCards = agent.getHand();
            
            // 获取上一手牌
            List<Card> lastMove = new ArrayList<>();
            if (room.getLastPattern() != null && room.getPassCount() < 3) {
                lastMove = room.getLastPattern().getCards();
            }
            
            // 构建请求消息
            Map<String, Object> request = new HashMap<>();
            request.put("msg", "ai_call");
            request.put("level", room.getLevel());
            request.put("last_move", lastMove);
            request.put("your_cards", yourCards);
            
            // 生成请求ID
            String requestId = UUID.randomUUID().toString();
            request.put("request_id", requestId);
            
            // 保存待处理请求
            pendingRequests.put(requestId, future);
            
            // 设置超时
            scheduler.schedule(() -> {
                CompletableFuture<List<Card>> timeoutFuture = pendingRequests.remove(requestId);
                if (timeoutFuture != null && !timeoutFuture.isDone()) {
                    log.warn("Agent request timeout, auto pass");
                    timeoutFuture.complete(new ArrayList<>());
                }
            }, timeout, TimeUnit.MILLISECONDS);
            
            // 发送请求
            String json = objectMapper.writeValueAsString(request);
            session.sendMessage(new TextMessage(json));
            log.info("Sent agent request: {}", json);
            
        } catch (Exception e) {
            log.error("Error requesting agent play", e);
            future.complete(new ArrayList<>());
        }
        
        return future;
    }
    
    private class AgentWebSocketHandler implements WebSocketHandler {
        
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("Agent WebSocket connection established");
        }
        
        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
            // 将消息处理调度到Spring管理的线程池，确保使用正确的类加载器
            taskExecutor.execute(() -> {
                try {
                    String payload = message.getPayload().toString();
                    log.info("Received from agent server: {}", payload);
                    
                    Map<String, Object> response = objectMapper.readValue(payload, Map.class);
                    String requestId = (String) response.get("request_id");
                    
                    if (requestId != null) {
                        CompletableFuture<List<Card>> future = pendingRequests.remove(requestId);
                        if (future != null) {
                            // 解析返回的牌
                            List<Card> cards = new ArrayList<>();
                            if (response.containsKey("cards")) {
                                List<Map<String, Object>> cardMaps = (List<Map<String, Object>>) response.get("cards");
                                for (Map<String, Object> cardMap : cardMaps) {
                                    String color = (String) cardMap.get("color");
                                    Integer number = (Integer) cardMap.get("number");
                                    cards.add(new Card(color, number));
                                }
                            }
                            future.complete(cards);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error handling agent response", e);
                }
            });
        }
        
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("Agent WebSocket transport error", exception);
            scheduleReconnect();
        }
        
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
            log.warn("Agent WebSocket connection closed: {}", closeStatus);
            scheduleReconnect();
        }
        
        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }
}
