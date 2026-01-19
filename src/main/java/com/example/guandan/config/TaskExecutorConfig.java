package com.example.guandan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfig {
    
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("agent-task-");
        
        // 关键：设置线程工厂，确保新线程使用正确的类加载器（DevTools的RestartClassLoader）
        // 这样Redis反序列化时就能正确加载GameRoom类
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        executor.setThreadFactory(r -> {
            Thread thread = new Thread(r);
            thread.setContextClassLoader(classLoader);
            return thread;
        });
        
        executor.initialize();
        return executor;
    }
}
