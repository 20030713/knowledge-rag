# Redis 模块设计说明

本文档专门记录本系统中所有 Redis 相关模块的用途、技术方案和实现原理。后续新增 Redis 缓存、限流、分布式锁、会话上下文等模块时，都继续追加到本文档。

## 1. 接口限流模块

### 目标

接口限流用于保护系统，避免高频请求拖垮后端服务或被暴力尝试。

当前已接入两个场景：

- 登录接口：限制同一 IP 的登录频率
- RAG 问答接口：限制同一用户的问答频率

### 使用技术

- Spring Data Redis
- `StringRedisTemplate`
- Redis `INCR`
- Redis `EXPIRE`
- Spring MVC Controller 前置校验
- 业务异常 `BusinessException`
- 错误码 `429 TOO_MANY_REQUESTS`

### 限流算法

当前使用固定窗口计数法。

核心流程：

```text
1. 根据接口和访问主体生成 Redis Key
2. 对 Key 执行 INCR
3. 如果 INCR 后的值为 1，说明这是当前窗口内第一次请求，同时设置 EXPIRE
4. 如果计数超过阈值，返回 429 请求过于频繁
5. Key 到期后自动删除，进入下一个窗口
```

固定窗口的优点：

- 实现简单
- Redis 命令少
- 性能开销低
- 适合项目当前阶段

固定窗口的不足：

- 窗口边界处可能出现短时间双倍流量

后续如果要更精确，可以升级为：

- 滑动窗口
- 令牌桶
- 漏桶
- Redisson `RRateLimiter`

### Key 设计

统一前缀：

```text
rate_limit:
```

登录接口：

```text
rate_limit:login:ip:{clientIp}
```

RAG 问答接口：

```text
rate_limit:rag:ask:user:{userId}
```

这样设计的原因：

- 前缀清晰，便于排查
- 区分不同业务接口
- 区分 IP 维度和用户维度
- 后续可继续追加更多限流场景

### 当前配置

配置位置：

```yaml
app:
  rate-limit:
    enabled: true
    login:
      limit: 10
      window-seconds: 60
    rag-ask:
      limit: 20
      window-seconds: 60
```

含义：

- 登录接口：同一 IP 60 秒最多 10 次
- RAG 问答接口：同一用户 60 秒最多 20 次

### 降级策略

当前策略：

```text
Redis 不可用时，跳过限流并打印 warning 日志
```

原因：

- 本项目处于本地开发和演示阶段
- Redis 未启动时，不应该导致登录和问答完全不可用
- 日志中保留告警，方便排查

生产环境可以调整为更严格的策略：

- Redis 不可用时拒绝高风险接口
- 接入本地内存兜底限流
- 使用 Redis Sentinel / Cluster 提高可用性

### 代码位置

配置类：

```text
src/main/java/com/rag/knowledge/config/RateLimitProperties.java
```

限流接口：

```text
src/main/java/com/rag/knowledge/service/RateLimitService.java
```

Redis 实现：

```text
src/main/java/com/rag/knowledge/service/impl/RedisRateLimitServiceImpl.java
```

接入位置：

```text
src/main/java/com/rag/knowledge/controller/AuthController.java
src/main/java/com/rag/knowledge/controller/RagController.java
```

### 面试讲法

可以这样介绍：

```text
系统使用 Redis INCR + EXPIRE 实现固定窗口限流。
登录接口按 IP 限流，防止暴力尝试；RAG 问答接口按用户限流，保护问答链路资源。
限流阈值通过 application.yml 配置，超限返回 429。
本地演示环境 Redis 不可用时采用放行降级并打印 warning，生产环境可以切换为拒绝或本地兜底限流。
```

---

## 2. RAG 问答缓存模块

### 目标

RAG 问答会涉及：

```text
读取 chunk -> 关键词检索 -> 组装引用 -> 生成回答 -> 保存问答历史
```

如果同一个用户在短时间内对同一个知识库重复提出同一个问题，每次都重新检索和生成会浪费资源。

问答缓存用于：

- 减少重复检索
- 提高重复问题响应速度
- 避免重复保存相同问答历史
- 为后续大模型接入后的成本控制打基础

### 使用技术

- Spring Data Redis
- `StringRedisTemplate`
- Jackson JSON 序列化
- Redis String
- Redis TTL
- SHA-256 问题摘要

### 缓存粒度

当前缓存命中条件：

```text
同一用户 + 同一知识库 + 同一问题
```

即使两个用户问了相同问题，也不会共用缓存。

原因：

- 当前知识库是按用户隔离的
- 用户权限边界要优先保证
- 后续企业知识库场景中，不同用户可能拥有不同知识范围

### Key 设计

格式：

```text
rag:answer:user:{userId}:kb:{kbId}:q:{questionHash}
```

示例：

```text
rag:answer:user:123:kb:456:q:7f83b1657ff1fc53...
```

其中 `questionHash` 是规范化问题后的 SHA-256：

```text
trim -> 合并连续空白 -> 小写 -> SHA-256
```

不直接把原始问题放进 Key 的原因：

- 中文和特殊字符会让 Key 不易读
- 问题可能很长，导致 Key 过长
- Hash 后长度稳定，便于管理

### Value 设计

Value 使用 JSON 保存完整 `RagAskResponse`：

```json
{
  "kbId": "知识库ID",
  "question": "用户问题",
  "answer": "回答内容",
  "hitCount": 5,
  "citations": []
}
```

这样缓存命中时可以直接返回给前端，不需要再次查数据库或重新组装引用。

### TTL 策略

当前配置：

```yaml
app:
  rag-cache:
    enabled: true
    ttl-minutes: 10
```

即缓存有效期为 10 分钟。

选择短 TTL 的原因：

- 当前文档内容可能频繁上传、删除、重新解析
- RAG 答案对知识库内容变化敏感
- 短 TTL 可以降低返回旧答案的概率

### 缓存命中行为

命中缓存时：

```text
直接返回缓存答案
不重新检索 chunk
不新增问答历史
```

不新增历史的原因：

- 避免用户连续点击提问导致历史记录刷屏
- 历史记录更接近真实问答行为，而不是缓存读取次数

### 缓存失效策略

以下操作会清理当前用户当前知识库的问答缓存：

- 上传文档
- 文档解析成功
- 删除文档

清理方式：

```text
SCAN rag:answer:user:{userId}:kb:{kbId}:q:* -> DEL
```

使用 `SCAN` 而不是 `KEYS` 的原因：

- `KEYS` 会阻塞 Redis，生产环境风险高
- `SCAN` 是渐进式遍历，更适合在线服务

### 降级策略

当前策略：

```text
Redis 不可用时，跳过缓存读取和写入，继续走正常 RAG 流程
```

原因：

- 缓存是性能优化，不应该影响核心问答功能
- 本地演示环境 Redis 可能未启动
- 日志会打印 warning，便于排查

### 代码位置

配置类：

```text
src/main/java/com/rag/knowledge/config/RagCacheProperties.java
```

缓存接口：

```text
src/main/java/com/rag/knowledge/service/RagAnswerCacheService.java
```

Redis 实现：

```text
src/main/java/com/rag/knowledge/service/impl/RedisRagAnswerCacheServiceImpl.java
```

接入位置：

```text
src/main/java/com/rag/knowledge/service/impl/RagServiceImpl.java
src/main/java/com/rag/knowledge/service/impl/DocumentServiceImpl.java
```

### 面试讲法

可以这样介绍：

```text
系统使用 Redis String 缓存 RAG 问答结果。
缓存粒度是 userId + kbId + questionHash，保证用户和知识库隔离。
缓存 Value 保存完整 RagAskResponse JSON，命中后直接返回，避免重复检索和重复写历史。
为了避免文档更新后返回旧答案，上传、解析、删除文档时会用 SCAN 清理对应知识库下的缓存。
Redis 不可用时缓存自动降级，不影响核心问答链路。
```

---

## 3. 文档解析分布式锁模块

### 目标

文档解析是异步任务。用户可能因为页面卡顿、网络延迟或多窗口操作，短时间内重复点击“解析”。

如果没有并发保护，可能出现：

- 同一个文档被重复提交解析任务
- 多个后台线程同时删除和写入 chunk
- 文档状态被不同任务反复覆盖
- Redis 问答缓存被重复清理

因此系统在提交解析任务前增加 Redis 分布式锁。

### 使用技术

- Spring Data Redis
- `StringRedisTemplate`
- Redis `SET key value NX EX`
- Lua 脚本释放锁
- UUID 锁值
- 业务层降级策略

### Key 设计

格式：

```text
lock:doc:parse:{documentId}
```

示例：

```text
lock:doc:parse:1800000000000000001
```

这样设计的原因：

- 锁粒度精确到单个文档
- 不同文档可以并行解析
- 同一文档只能有一个解析任务运行
- 前缀清晰，便于 Redis 中排查

### 加锁流程

提交解析任务前执行：

```text
SET lock:doc:parse:{documentId} {uuid} NX EX 600
```

含义：

- `NX`：只有 Key 不存在时才能设置成功
- `EX 600`：锁 10 分钟后自动过期，避免任务异常退出后死锁
- `uuid`：当前持锁者的唯一标识

如果加锁成功，继续把文档状态改为 `PARSING` 并提交异步任务。

如果 Redis 可用但加锁失败，说明已有解析任务正在运行，接口直接返回：

```text
文档解析任务已提交，请稍后查看状态
```

### 为什么释放锁要用 Lua

释放锁不能简单执行：

```text
DEL key
```

因为可能出现这种情况：

```text
任务 A 持有锁 -> 任务 A 执行超时 -> 锁自动过期
任务 B 获取同一个 key 的新锁
任务 A 结束时直接 DEL key -> 误删任务 B 的锁
```

所以释放锁时必须先比较 value：

```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

Lua 脚本在 Redis 中原子执行，可以保证“判断持锁者”和“删除锁”不会被其他命令插入。

### 系统中的运行方式

1. 用户点击解析。
2. `DocumentServiceImpl.parse()` 先尝试获取 `lock:doc:parse:{documentId}`。
3. 获取成功后，更新文档状态为 `PARSING`，清理旧 chunk 和 RAG 缓存。
4. 后端提交 `DocumentParseTask.parseAsync()`。
5. 异步任务解析成功或失败后，在 `finally` 中释放锁。
6. 如果提交任务前发生异常，入口方法也会释放锁。

### 降级策略

当前策略：

```text
Redis 不可用时，跳过分布式锁，继续使用数据库文档状态保护。
```

原因：

- 本地演示环境 Redis 可能未启动
- 分布式锁是并发增强，不应该导致文档解析完全不可用
- 原有 `PARSING` 状态仍然能阻止大部分重复提交

生产环境可以切换为更严格策略：

- Redis 不可用时拒绝提交异步任务
- 使用 Redisson Watchdog 自动续期
- 使用任务表做更强的幂等控制

### 代码位置

锁接口：

```text
src/main/java/com/rag/knowledge/service/DistributedLockService.java
```

Redis 实现：

```text
src/main/java/com/rag/knowledge/service/impl/RedisDistributedLockServiceImpl.java
```

加锁位置：

```text
src/main/java/com/rag/knowledge/service/impl/DocumentServiceImpl.java
```

释放锁位置：

```text
src/main/java/com/rag/knowledge/job/DocumentParseTask.java
```

### 面试讲法

可以这样介绍：

```text
文档解析是异步任务，为了防止同一个文档被重复提交，我在提交任务前增加 Redis 分布式锁。锁的粒度是 documentId，使用 SET NX EX 加锁，并设置 10 分钟过期时间避免死锁。释放锁时不用简单 DEL，而是用 Lua 脚本先比较 UUID 再删除，避免锁过期后误删其他任务的新锁。Redis 不可用时，本地演示环境会降级为数据库 PARSING 状态保护，生产环境可以改成拒绝提交或使用 Redisson Watchdog。
```
