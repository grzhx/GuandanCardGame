# Guandan Game Server 部署指南

## 环境要求

### 必需软件

- **JDK**: 17 或更高版本
- **Maven**: 3.6 或更高版本
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本

### 推荐配置

- **CPU**: 2核心或以上
- **内存**: 4GB或以上
- **磁盘**: 10GB可用空间

---

## 本地开发环境部署

### 1. 安装依赖软件

#### Windows

```bash
# 安装MySQL
# 下载并安装 MySQL 8.0+ from https://dev.mysql.com/downloads/mysql/

# 安装Redis
# 下载并安装 Redis for Windows from https://github.com/microsoftarchive/redis/releases

# 安装JDK 17
# 下载并安装 from https://www.oracle.com/java/technologies/downloads/
```

#### Linux (Ubuntu/Debian)

```bash
# 安装MySQL
sudo apt update
sudo apt install mysql-server

# 安装Redis
sudo apt install redis-server

# 安装JDK 17
sudo apt install openjdk-17-jdk

# 安装Maven
sudo apt install maven
```

#### macOS

```bash
# 使用Homebrew安装
brew install mysql
brew install redis
brew install openjdk@17
brew install maven
```

### 2. 启动数据库服务

#### MySQL

```bash
# Windows
net start MySQL80

# Linux
sudo systemctl start mysql

# macOS
brew services start mysql
```

#### Redis

```bash
# Windows
redis-server

# Linux
sudo systemctl start redis

# macOS
brew services start redis
```

### 3. 创建数据库

```bash
# 登录MySQL
mysql -u root -p

# 执行SQL脚本
mysql -u root -p < src/main/resources/schema.sql
```

或者手动执行：

```sql
CREATE DATABASE IF NOT EXISTS guandan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE guandan;

-- 执行 schema.sql 中的所有表创建语句
```

### 4. 配置应用

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/guandan?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_mysql_password  # 修改为你的MySQL密码
  data:
    redis:
      host: localhost
      port: 6379
      password:  # 如果Redis设置了密码，在此填写
```

### 5. 编译项目

```bash
cd D:/IdeaProjects/GuandanGame
mvn clean install
```

### 6. 运行应用

```bash
mvn spring-boot:run
```

或者运行打包后的jar：

```bash
java -jar target/GuandanGame-0.0.1-SNAPSHOT.jar
```

### 7. 验证部署

访问以下URL验证服务是否正常运行：

```bash
# 测试服务器是否启动
curl http://localhost:8080/actuator/health

# 测试注册接口
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123","confirmation":"test123"}'
```

---

## 生产环境部署

### 1. 打包应用

```bash
mvn clean package -DskipTests
```

生成的jar文件位于：`target/GuandanGame-0.0.1-SNAPSHOT.jar`

### 2. 配置生产环境

创建生产环境配置文件 `application-prod.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/guandan?useSSL=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      password: ${REDIS_PASSWORD}

server:
  port: 8080

logging:
  level:
    root: INFO
    com.example.guandan: INFO
  file:
    name: /var/log/guandan/application.log
```

### 3. 使用环境变量

```bash
export DB_USERNAME=guandan_user
export DB_PASSWORD=secure_password
export REDIS_HOST=redis.example.com
export REDIS_PORT=6379
export REDIS_PASSWORD=redis_password
```

### 4. 运行应用

```bash
java -jar -Dspring.profiles.active=prod GuandanGame-0.0.1-SNAPSHOT.jar
```

### 5. 使用systemd管理（Linux）

创建服务文件 `/etc/systemd/system/guandan.service`：

```ini
[Unit]
Description=Guandan Game Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=guandan
WorkingDirectory=/opt/guandan
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod /opt/guandan/GuandanGame-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment="DB_USERNAME=guandan_user"
Environment="DB_PASSWORD=secure_password"
Environment="REDIS_HOST=localhost"
Environment="REDIS_PORT=6379"

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable guandan
sudo systemctl start guandan
sudo systemctl status guandan
```

---

## Docker部署

### 1. 创建Dockerfile

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/GuandanGame-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 2. 创建docker-compose.yml

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: guandan
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql

  redis:
    image: redis:6.2
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  guandan:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/guandan?useSSL=false&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: rootpassword
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
    depends_on:
      - mysql
      - redis

volumes:
  mysql_data:
  redis_data:
```

### 3. 构建和运行

```bash
# 构建应用
mvn clean package -DskipTests

# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f guandan

# 停止服务
docker-compose down
```

---

## 云平台部署

### AWS部署

#### 使用EC2

1. 启动EC2实例（推荐t3.medium或更高）
2. 安装必需软件（JDK, MySQL, Redis）
3. 配置安全组（开放8080端口）
4. 上传jar文件并运行

#### 使用RDS和ElastiCache

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-rds-endpoint:3306/guandan
    username: admin
    password: ${DB_PASSWORD}
  data:
    redis:
      host: your-elasticache-endpoint
      port: 6379
```

### 阿里云部署

#### 使用ECS

1. 创建ECS实例
2. 安装运行环境
3. 配置安全组规则
4. 部署应用

#### 使用RDS和Redis

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-rds.mysql.rds.aliyuncs.com:3306/guandan
    username: guandan
    password: ${DB_PASSWORD}
  data:
    redis:
      host: your-redis.redis.rds.aliyuncs.com
      port: 6379
      password: ${REDIS_PASSWORD}
```

---

## 性能优化

### JVM参数优化

```bash
java -jar \
  -Xms2g \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/guandan/heapdump.hprof \
  GuandanGame-0.0.1-SNAPSHOT.jar
```

### MySQL优化

```sql
-- 调整连接池大小
SET GLOBAL max_connections = 500;

-- 优化查询缓存
SET GLOBAL query_cache_size = 67108864;

-- 添加索引
CREATE INDEX idx_username ON user(username);
CREATE INDEX idx_room ON game_history(room_id);
```

### Redis优化

```bash
# 修改redis.conf
maxmemory 2gb
maxmemory-policy allkeys-lru
```

---

## 监控和日志

### 日志配置

在 `application.yml` 中配置：

```yaml
logging:
  level:
    root: INFO
    com.example.guandan: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/guandan.log
    max-size: 10MB
    max-history: 30
```

### 健康检查

```bash
# 检查应用状态
curl http://localhost:8080/actuator/health

# 检查MySQL连接
mysql -h localhost -u root -p -e "SELECT 1"

# 检查Redis连接
redis-cli ping
```

---

## 故障排查

### 常见问题

#### 1. 无法连接MySQL

```bash
# 检查MySQL是否运行
systemctl status mysql

# 检查端口是否开放
netstat -an | grep 3306

# 测试连接
mysql -h localhost -u root -p
```

#### 2. 无法连接Redis

```bash
# 检查Redis是否运行
systemctl status redis

# 测试连接
redis-cli ping
```

#### 3. 应用启动失败

```bash
# 查看日志
tail -f logs/guandan.log

# 检查端口占用
netstat -an | grep 8080
```

#### 4. WebSocket连接失败

- 检查防火墙设置
- 确认WebSocket端点配置正确
- 查看浏览器控制台错误信息

---

## 安全建议

1. **数据库安全**
   - 使用强密码
   - 限制远程访问
   - 定期备份数据

2. **Redis安全**
   - 设置密码
   - 绑定到内网IP
   - 禁用危险命令

3. **应用安全**
   - 使用HTTPS
   - 实现请求限流
   - 添加身份验证

4. **网络安全**
   - 配置防火墙
   - 使用VPC
   - 启用DDoS防护

---

## 备份和恢复

### 数据库备份

```bash
# 备份
mysqldump -u root -p guandan > backup_$(date +%Y%m%d).sql

# 恢复
mysql -u root -p guandan < backup_20260114.sql
```

### Redis备份

```bash
# 手动触发保存
redis-cli SAVE

# 备份RDB文件
cp /var/lib/redis/dump.rdb /backup/dump_$(date +%Y%m%d).rdb
```

---

## 扩展和升级

### 水平扩展

1. 使用负载均衡器（Nginx/HAProxy）
2. 部署多个应用实例
3. 使用共享Redis和MySQL

### 垂直扩展

1. 增加服务器配置
2. 优化JVM参数
3. 调整数据库配置

---

## 联系支持

如有问题，请查看：
- README.md - 项目概述
- API_DOCUMENTATION.md - API文档
- GitHub Issues - 问题追踪
