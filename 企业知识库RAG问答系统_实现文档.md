# Java 实习项目实现文档：企业知识库 RAG 问答系统

> **项目主题**：基于 Spring Boot + Redis + LangChain4j 的企业知识库 RAG 问答系统  
> **适用对象**：研一学生 / Java 后端实习 / 小厂项目展示  
> **生成日期**：2026-06-06

---

## 目录

1. [项目定位与最终效果](#1-项目定位与最终效果)
2. [技术栈选型](#2-技术栈选型)
3. [功能边界：MVP、增强版、简历版](#3-功能边界mvp增强版简历版)
4. [总体架构与核心业务流程](#4-总体架构与核心业务流程)
5. [数据库与缓存设计](#5-数据库与缓存设计)
6. [完整实现顺序](#6-完整实现顺序)
7. [各模块实现思路与关键技术点](#7-各模块实现思路与关键技术点)
8. [接口设计建议](#8-接口设计建议)
9. [面试重点与可讲难点](#9-面试重点与可讲难点)
10. [开发计划与验收标准](#10-开发计划与验收标准)
11. [简历写法模板](#11-简历写法模板)
12. [可选第二项目衔接](#12-可选第二项目衔接)
13. [参考资料](#13-参考资料)

---

## 1. 项目定位与最终效果

**项目名称建议**：企业知识库 RAG 问答系统。

这个项目的目标不是做一个简单聊天机器人，而是做一个有完整后端业务闭环的 AI 应用：用户上传资料，系统解析文档、切分文本、生成向量、建立索引；用户提问时，系统先检索知识库，再结合大模型生成答案，并返回引用来源。

### 适合写进简历的原因

这个项目同时覆盖：

- Java 后端基础
- Redis
- MySQL
- 文件存储
- 异步任务
- 权限控制
- 向量检索
- LangChain4j

对小厂 Java 实习来说，这个项目足够新，但又不是无法落地的“纯概念项目”。

### 最终演示效果

- 用户登录后创建一个知识库，例如“Java 八股资料库”或“公司制度知识库”。
- 用户上传 PDF、Word、Markdown、TXT 等文档。
- 系统显示文档处理状态：待解析、解析中、已完成、失败。
- 用户在对话框提问，系统返回基于文档内容的答案。
- 答案下方展示引用片段和来源文档，避免让面试官觉得只是“套壳调用大模型”。
- 后台可以查看文档数量、问答次数、热门问题、接口耗时和失败记录。

---

## 2. 技术栈选型

| 层次 | 推荐技术 | 作用 | 简历价值 |
|---|---|---|---|
| 后端框架 | Spring Boot 3.x | 接口、依赖注入、配置管理、Web 服务 | Java 后端基本盘 |
| 数据库 | MySQL 8.x | 用户、知识库、文档、会话、问答日志 | 表设计、索引、事务 |
| ORM | MyBatis-Plus | 快速实现 CRUD 与分页查询 | 小厂常见技术栈 |
| 缓存 | Redis + Redisson | 缓存、限流、分布式锁、会话上下文 | Redis 八股落地 |
| AI 框架 | LangChain4j | AI Service、Prompt、Chat Memory、RAG、Tool Calling | Java AI 项目亮点 |
| 向量存储 | Milvus / Elasticsearch / PostgreSQL pgvector | 存储向量并做相似度检索 | RAG 核心能力 |
| 文件存储 | MinIO / 本地存储 | 保存上传文档和解析结果 | 对象存储经验 |
| 异步处理 | RabbitMQ / Spring Async | 文档解析、向量化、失败重试 | 系统吞吐与解耦 |
| 安全认证 | JWT / Sa-Token / Spring Security | 登录、鉴权、接口保护 | 企业项目必备 |
| 部署 | Docker Compose | 一键启动 MySQL、Redis、向量库、后端 | 可演示、可复现 |

> 如果时间有限，向量存储可以先用 LangChain4j 支持的内存 `EmbeddingStore` 跑通流程，再升级为 Milvus、Elasticsearch 或 pgvector。简历版建议至少使用一种真正的持久化向量存储。

---

## 3. 功能边界：MVP、增强版、简历版

| 版本 | 必须实现的功能 | 可以暂缓的功能 | 目标 |
|---|---|---|---|
| MVP 版 | 登录、文档上传、文档解析、简单问答 | 复杂权限、后台统计、MQ | 先跑通业务闭环 |
| 增强版 | 文本切分、Embedding、向量检索、RAG 问答、来源引用 | 多租户、精细化监控 | 能讲清 AI 技术点 |
| 简历版 | Redis 缓存、限流、分布式锁、异步任务、文档状态机、异常处理 | 复杂前端 UI | 体现后端工程能力 |
| 加分版 | Tool Calling、Prompt 模板管理、重排序、答案评分、Docker 部署 | 多 Agent 架构 | 提升项目区分度 |

---

## 4. 总体架构与核心业务流程

推荐采用经典三层架构，并单独抽象 AI 服务层和文档处理流水线。

### 4.1 推荐代码包结构

```text
controller
service
repository
domain
ai
rag
vector
storage
job
config
common
```

### 4.2 核心架构图（文字版）

```text
前端 Vue / Apifox
    ↓
Spring Boot Controller
    ↓
业务服务层：用户、知识库、文档、问答、会话
    ↓
基础设施层：MySQL、Redis、MinIO、MQ、向量数据库
    ↓
AI 能力层：LangChain4j、EmbeddingModel、ChatModel、RetrievalAugmentor、ChatMemory
    ↓
大模型服务：OpenAI / 通义千问 / DeepSeek / Ollama 本地模型
```

### 4.3 文档入库流程

1. 用户上传文档，后端校验文件大小、类型、用户权限。
2. 文件保存到 MinIO 或本地目录，MySQL 记录 `document` 表，状态为 `UPLOADED`。
3. 发送异步任务，状态变为 `PARSING`。
4. 解析文档文本，清洗空白字符、页眉页脚、乱码。
5. 按固定长度、标题层级或语义规则切分文本片段。
6. 调用 `EmbeddingModel` 为每个片段生成向量。
7. 向量和片段元数据写入向量库，MySQL 记录 `chunk` 表。
8. 文档状态改为 `COMPLETED`；失败则记录错误信息并支持重试。

### 4.4 用户问答流程

1. 用户选择知识库并输入问题。
2. 先做登录校验、知识库权限校验和 Redis 限流。
3. 检查 Redis 中是否存在相同问题的缓存答案。
4. 如果没有缓存，将问题向量化，在向量库中检索 TopK 相关片段。
5. 对召回片段做过滤、去重，必要时重排序。
6. 组装 Prompt：系统角色 + 用户问题 + 检索片段 + 输出格式要求。
7. 调用 LangChain4j AI Service 生成答案。
8. 保存问答日志、会话消息和引用片段。
9. 将热门问题答案写入 Redis，设置合理过期时间。

---

## 5. 数据库与缓存设计

### 5.1 MySQL 表设计

| 表名 | 核心字段 | 说明 |
|---|---|---|
| `user` | `id`, `username`, `password`, `role`, `created_at` | 用户信息，密码需要加密存储 |
| `knowledge_base` | `id`, `user_id`, `name`, `description`, `visibility` | 知识库，一个用户可创建多个知识库 |
| `document` | `id`, `kb_id`, `file_name`, `file_type`, `file_url`, `status`, `error_msg` | 文档元数据和处理状态 |
| `document_chunk` | `id`, `document_id`, `chunk_no`, `content`, `token_count`, `vector_id` | 文档切片元数据，向量可存向量库 |
| `chat_session` | `id`, `user_id`, `kb_id`, `title`, `created_at`, `updated_at` | 一次连续问答会话 |
| `chat_message` | `id`, `session_id`, `role`, `content`, `created_at` | 用户消息和 AI 回复 |
| `qa_record` | `id`, `user_id`, `kb_id`, `question`, `answer`, `latency_ms`, `success` | 问答日志，用于统计和排查问题 |
| `citation` | `id`, `qa_id`, `document_id`, `chunk_id`, `content_snapshot`, `score` | 答案引用来源 |

### 5.2 Redis Key 设计

| Key 模式 | 数据结构 | 过期时间 | 用途 |
|---|---|---|---|
| `rag:qa:{kbId}:{questionHash}` | String | 1-24 小时 | 缓存热门问题答案 |
| `rag:limit:{userId}:{minute}` | String / Counter | 60 秒 | 限制用户每分钟调用次数 |
| `rag:doc:lock:{docId}` | String / Lock | 自动续期 | 防止同一文档重复解析 |
| `rag:doc:progress:{docId}` | Hash | 1 天 | 保存文档解析进度 |
| `rag:chat:memory:{sessionId}` | List / String | 1-7 天 | 保存多轮会话短期上下文 |
| `rag:hot:question:{kbId}` | ZSet | 长期 | 统计热门问题 |
| `rag:blacklist:token:{token}` | String | 到 token 失效 | 退出登录后的 token 黑名单 |

**面试时重点讲**：Redis 不保存所有历史消息。历史消息长期存 MySQL，Redis 只保存短期高频上下文或热点数据，避免内存无限膨胀。

---

## 6. 完整实现顺序

### 第 0 步：准备环境与仓库

创建 Spring Boot 项目，接入 MySQL、Redis、MyBatis-Plus、统一响应、全局异常、日志配置、Swagger/Knife4j 或 Apifox。先保证项目结构清晰，后续模块才能稳定扩展。

### 第 1 步：用户登录与权限

实现注册、登录、JWT 鉴权、用户角色。所有知识库、文档、会话都必须绑定 `user_id`，避免演示时出现越权访问。

### 第 2 步：知识库管理

实现知识库新增、删除、修改、列表查询。知识库是 RAG 的隔离单位，不同知识库的文档不能混在一起检索。

### 第 3 步：文档上传与存储

实现文件上传接口，校验文件大小和后缀，保存到 MinIO 或本地目录，并在 `document` 表记录元数据。

### 第 4 步：文档解析与清洗

使用 PDFBox、Apache POI 或 LangChain4j 文档加载器解析文本。清洗连续空格、空行、无意义页码，保留标题结构。

### 第 5 步：文本切分 Chunk

按 500-1000 字左右切分，设置 50-150 字重叠区。切分结果写入 `document_chunk` 表，方便追踪来源。

### 第 6 步：Embedding 与向量入库

接入 `EmbeddingModel`，将 chunk 转成向量，写入向量数据库。先用内存向量库跑通，再替换成持久化方案。

### 第 7 步：RAG 问答主流程

用户问题向量化，检索 TopK 片段，拼接上下文，调用 `ChatModel` 生成答案，保存答案和引用。

### 第 8 步：多轮会话与 Chat Memory

每个 session 保存最近 N 轮对话。MySQL 保存完整历史，Redis 或 LangChain4j `ChatMemory` 保存短期上下文。

### 第 9 步：Redis 工程增强

加入热点问答缓存、接口限流、文档解析锁、任务进度缓存、热门问题统计。

### 第 10 步：异步任务与失败重试

文档解析和向量化不要阻塞上传接口。使用 Spring Async 或 RabbitMQ 异步处理，并支持失败重试。

### 第 11 步：后台统计与部署

统计知识库数量、文档数量、问答次数、平均耗时、失败率。用 Docker Compose 启动依赖，写 README 和演示脚本。

---

## 7. 各模块实现思路与关键技术点

### 7.1 用户模块

- 密码使用 BCrypt 加密，不要明文保存。
- JWT 中放 `userId` 和 `role`，接口通过拦截器解析用户身份。
- 退出登录可以将 token 加入 Redis 黑名单。

### 7.2 知识库模块

- 知识库是权限边界，也是检索边界。
- 查询知识库时必须带 `user_id` 条件，防止越权。
- 删除知识库时要同步删除文档、chunk、向量索引和缓存。

### 7.3 文档模块

- 文档上传后立即返回，不在接口里同步解析。
- `document.status` 使用枚举：`UPLOADED`、`PARSING`、`EMBEDDING`、`COMPLETED`、`FAILED`。
- 解析失败要保存 `error_msg`，并提供重试接口。

### 7.4 文本切分模块

- 切分太大：上下文超长，召回不精准。
- 切分太小：语义不完整。
- 建议先用固定长度 + overlap，后续再按标题、段落优化。
- 每个 chunk 记录 `document_id`、`chunk_no`、`content`、`token_count`。

### 7.5 向量检索模块

- 入库时保存 `kb_id`、`document_id`、`chunk_id` 等元数据。
- 检索时必须加 `kb_id` 过滤，保证只查当前知识库。
- TopK 可先设为 5，后续根据答案质量调整。

### 7.6 问答模块

- Prompt 中明确要求：只能根据给定资料回答，不知道就说明资料不足。
- 答案要返回引用来源，提升可信度。
- 调用大模型前后都要记录耗时和异常，方便排查。

### 7.7 会话模块

- 会话列表类似 ChatGPT 左侧历史记录。
- 完整聊天历史存 MySQL，最近几轮上下文进入 ChatMemory。
- 长会话要做摘要或窗口裁剪，避免上下文过长。

### 7.8 Redis 模块

- 热点答案缓存降低大模型调用成本。
- 限流保护大模型接口和后端服务。
- 分布式锁避免重复解析同一文档。

### 7.9 异步任务模块

- 上传接口只负责落库和投递任务。
- 消费者负责解析、切分、向量化、更新状态。
- 失败任务需要记录失败原因，支持手动重试。

---

## 8. 接口设计建议

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录，返回 JWT |
| `/api/kb` | POST | 创建知识库 |
| `/api/kb` | GET | 查询我的知识库列表 |
| `/api/kb/{id}` | DELETE | 删除知识库 |
| `/api/doc/upload` | POST | 上传文档 |
| `/api/doc/{id}/status` | GET | 查询文档处理状态 |
| `/api/doc/{id}/retry` | POST | 重试文档解析 |
| `/api/chat/session` | POST | 创建会话 |
| `/api/chat/session` | GET | 查询会话列表 |
| `/api/chat/ask` | POST | 向知识库提问 |
| `/api/chat/{sessionId}/messages` | GET | 查询会话消息 |
| `/api/admin/stat` | GET | 后台统计 |

---

## 9. 面试重点与可讲难点

| 面试问题 | 建议回答方向 |
|---|---|
| 你的项目和普通聊天机器人有什么区别？ | 普通聊天机器人直接问大模型；本项目先检索用户上传的私有文档，再结合上下文回答，并返回引用来源。 |
| 为什么要做文本切分？ | 大模型上下文有限，文档太长不能全部传入；切分后可以只召回相关片段，提高准确性和成本控制。 |
| Redis 在项目里怎么用？ | 热点问答缓存、用户限流、文档解析分布式锁、处理进度缓存、热门问题排行榜、token 黑名单。 |
| 如何避免大模型胡说？ | Prompt 限制回答范围，RAG 提供依据，返回引用来源；资料不足时要求回答“不知道”。 |
| 如何保证文档不会重复解析？ | 上传后 document 有状态机，解析任务使用 Redis/Redisson 分布式锁，重复任务直接跳过。 |
| 为什么要异步处理文档？ | 解析和向量化耗时较长，同步处理会导致上传接口超时；异步任务可以提升用户体验并支持失败重试。 |
| MySQL 和向量库分别存什么？ | MySQL 存业务元数据和可审计信息；向量库存 chunk 的向量和检索元数据。 |
| 多轮对话怎么实现？ | MySQL 保存完整历史，LangChain4j ChatMemory 或 Redis 保存最近 N 轮上下文，长会话做窗口裁剪。 |

---

## 10. 开发计划与验收标准

| 时间 | 开发内容 | 验收标准 |
|---|---|---|
| 第 1 周 | 项目脚手架、登录鉴权、知识库 CRUD、文档上传 | 能登录并上传文档，数据库有完整记录 |
| 第 2 周 | 文档解析、文本切分、Embedding、向量入库 | 文档状态能从上传变为完成，chunk 可查询 |
| 第 3 周 | RAG 问答、多轮会话、引用来源、问答日志 | 能基于文档回答问题，并展示来源 |
| 第 4 周 | Redis 缓存/限流/锁、异步任务、后台统计、Docker 部署 | 项目可演示，README 完整，能讲技术难点 |

---

## 11. 简历写法模板

**项目名称**：基于 Spring Boot + LangChain4j 的企业知识库 RAG 问答系统

- 基于 Spring Boot + LangChain4j 实现企业知识库问答系统，支持文档上传、文本切分、向量化入库、相似度检索、RAG 问答和答案来源追踪。
- 使用 Redis 实现热点问答缓存、用户接口限流、文档解析分布式锁和任务进度缓存，降低大模型重复调用成本并提升系统稳定性。
- 设计文档处理状态机和异步解析流程，通过 MQ/Spring Async 完成文档解析、chunk 切分、Embedding 生成和向量库写入，避免上传接口阻塞。
- 结合 MySQL 保存业务元数据和问答日志，向量数据库保存文本片段向量，并在检索时通过 `kb_id` 做知识库隔离，避免跨知识库召回。
- 通过 Prompt 模板、Chat Memory、引用片段返回和“资料不足拒答”策略，提升回答准确性并减少大模型幻觉。

---

## 12. 可选第二项目衔接

如果还想再做一个项目，建议做“秒杀预约系统 + AI 运营助手”。这样简历上一个项目体现 AI/RAG/LangChain4j，另一个项目体现 Redis 高并发、MQ、库存扣减和订单一致性。

| 项目 | 核心技术 | 和主项目形成的互补 |
|---|---|---|
| AI 知识库 RAG 问答系统 | LangChain4j、RAG、向量检索、Redis、MySQL | 体现 Java AI 应用与完整后端工程 |
| 秒杀预约系统 + AI 运营助手 | Redis、Lua、Redisson、MQ、MySQL 事务、限流降级 | 体现高并发和 Redis 深度使用 |

---

## 13. 参考资料

- LangChain4j 官方文档：介绍 Prompt、Chat Memory、Output Parsing、Tools、Agents、RAG 等能力。  
  <https://docs.langchain4j.dev/>
- LangChain4j GitHub：说明其工具箱包含 Prompt Templating、Chat Memory、Function Calling、Agents、RAG 等抽象。  
  <https://github.com/langchain4j/langchain4j>
- LangChain4j RAG 教程：展示使用 `EmbeddingStore` 检索相关内容并结合 AI Service 回答问题。  
  <https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/rag.md>
- LangChain4j Chat Memory 教程：说明 ChatMemory 可作为 ChatMessage 容器，支持淘汰策略和持久化。  
  <https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/chat-memory.md>
- LangChain4j AI Services 教程：说明 Spring Boot starter 可简化 AI Services 在 Spring Boot 应用中的使用。  
  <https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/ai-services.md>
