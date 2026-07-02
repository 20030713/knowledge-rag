# 企业知识库 RAG 问答系统

基于 Spring Boot 3、MySQL、Redis 和 React 的企业知识库 RAG 问答系统。系统支持用户创建知识库、上传 PDF/Word/Markdown/TXT 文档、异步解析切分、本地向量检索问答、引用来源展示、任务中心和问答反馈闭环。

## 项目亮点

- **完整 RAG 主流程**：文档上传、文本解析、chunk 切分、本地向量检索、问答、引用来源。
- **多格式文档解析**：使用 PDFBox 和 Apache POI 支持 PDF、DOCX、DOC，同时支持 Markdown 和 TXT。
- **异步任务体系**：文档解析使用 Spring Async，配合状态机、失败重试和任务中心。
- **Redis 工程增强**：实现接口限流、RAG 问答缓存、文档解析分布式锁。
- **检索透明度**：回答引用展示最终分数、向量分数和关键词分数。
- **反馈闭环**：用户可对回答标记有帮助/没帮助，仪表盘统计反馈质量。
- **数据一致性**：删除知识库时级联清理文档、chunk、问答历史、Redis 缓存和本地文件。

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Spring Boot 3, Java 21, MyBatis-Plus |
| 数据库 | MySQL |
| 缓存与锁 | Redis, StringRedisTemplate |
| 文档解析 | PDFBox, Apache POI |
| RAG 检索 | 本地 Hashing Embedding, Cosine Similarity, 关键词混合排序 |
| 前端 | React, Vite, TypeScript, lucide-react |
| 文档 | Knife4j / OpenAPI |

## 展示材料

- [项目架构与流程图](docs/项目架构与流程图.md)
- [面试讲解稿](docs/面试讲解稿.md)
- [演示脚本](docs/演示脚本.md)
- [简历写法](docs/简历写法.md)
- [Docker 一键启动说明](docs/Docker一键启动说明.md)
- [核心技术模块说明](docs/核心技术模块说明.md)
- [Redis 模块设计说明](docs/Redis模块设计说明.md)
- [项目踩坑记录](docs/项目踩坑记录.md)

## 界面预览

登录页：

![登录页](docs/images/login-preview.svg)

知识库工作台：

![知识库工作台](docs/images/workspace-preview.svg)

RAG 问答与引用来源：

![RAG 问答与引用来源](docs/images/rag-preview.svg)

仪表盘与任务中心：

![仪表盘与任务中心](docs/images/dashboard-preview.svg)

## 核心流程

```text
上传文档 -> 异步解析 -> 文本切分 -> 本地向量检索 -> RAG 回答 -> 引用来源 -> 用户反馈
```

文档入库：

```text
PDF/Word/Markdown/TXT -> 文本提取 -> TextChunker -> document_chunk -> 可检索
```

问答链路：

```text
问题 -> Redis 限流 -> Redis 缓存 -> 本地 Embedding -> TopK chunk -> 回答和引用 -> 问答历史
```

## 当前阶段

已完成：

- 用户注册、登录、JWT 鉴权
- 知识库 CRUD
- 文档上传、删除、状态管理
- PDF / Word / Markdown / TXT 解析
- 文本切分与 chunk 预览
- 本地 Embedding + 向量检索
- RAG 问答与引用来源
- 问答历史与反馈
- 仪表盘统计
- 任务中心
- Redis 限流、缓存、分布式锁
- 接口日志
- 知识库删除级联清理

## 本地启动

### Docker 一键启动

推荐演示时使用：

```bash
docker compose up --build
```

后台启动：

```bash
docker compose up -d --build
```

访问地址：

```text
前端：http://localhost:5173
后端健康检查：http://localhost:8080/api/health
接口文档：http://localhost:8080/doc.html
容器 MySQL：localhost:3307
```

更详细说明见：

```text
docs/Docker一键启动说明.md
```

### 本地开发启动

先启动 MySQL 和 Redis：

```bash
docker compose up -d
```

再启动后端：

```bash
mvn spring-boot:run
```

启动后，后端会自动检查并创建当前阶段需要的业务表：

- `user`
- `knowledge_base`
- `document`
- `document_chunk`
- `qa_record`

如果本地数据库还不存在，JDBC URL 需要带上：

```text
createDatabaseIfNotExist=true
```

Docker Compose 首次初始化 MySQL 容器时也会执行 `sql/init.sql`。如果容器已经初始化过，修改 `sql/init.sql` 不会自动重新执行。

健康检查：

```http
GET http://localhost:8080/api/health
```

接口文档入口：

```http
http://localhost:8080/doc.html
```

## 认证接口

注册：

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "demo_user",
  "password": "123456"
}
```

登录：

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "demo_user",
  "password": "123456"
}
```

获取当前用户：

```http
GET /api/auth/me
Authorization: Bearer <token>
```

## 知识库接口

创建知识库：

```http
POST /api/kb
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Java 八股资料库",
  "description": "用于整理 Java 后端面试资料"
}
```

查询我的知识库：

```http
GET /api/kb
Authorization: Bearer <token>
```

更新知识库：

```http
PUT /api/kb/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "公司制度知识库",
  "description": "用于查询公司制度文档"
}
```

删除知识库：

```http
DELETE /api/kb/{id}
Authorization: Bearer <token>
```

## 文档接口

上传文档：

```http
POST /api/doc/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

kbId=<knowledgeBaseId>
file=<pdf/doc/docx/md/txt>
```

查询知识库文档列表：

```http
GET /api/doc?kbId={knowledgeBaseId}
Authorization: Bearer <token>
```

查询文档状态：

```http
GET /api/doc/{id}/status
Authorization: Bearer <token>
```

解析 PDF / Word / Markdown / TXT 文档并生成文本切片：

```http
POST /api/doc/{id}/parse
Authorization: Bearer <token>
```

解析接口采用异步任务：接口会立即返回 `PARSING` 状态，前端轮询状态接口，完成后自动加载切片。
解析失败会记录失败次数，连续失败 3 次后需要检查文件并重新上传。

查询文档切片：

```http
GET /api/doc/{id}/chunks
Authorization: Bearer <token>
```

前端支持对已加载切片进行本地关键词检索和高亮，便于定位引用来源。

删除文档，并同步删除对应切片：

```http
DELETE /api/doc/{id}
Authorization: Bearer <token>
```

## RAG 问答接口

当前阶段已实现本地 Embedding + 向量检索版 RAG：从已解析的 `document_chunk` 中召回相关片段，并返回回答和引用来源。

当前检索层使用 Hashing Vectorizer 生成本地向量，通过余弦相似度召回，并融合关键词分数排序。它不依赖外部模型或向量数据库，适合本地演示；后续可以平滑替换为真实 Embedding 模型、Milvus 或 pgvector。

向量检索参数可在 `application.yml` 中调整：

```yaml
app:
  vector-search:
    top-k: 5
    max-chunks-to-scan: 200
    dimensions: 256
    vector-weight: 0.7
    keyword-weight: 0.3
```

引用来源会返回检索分数：

- `score`：最终混合分数
- `vectorScore`：本地向量余弦相似度
- `keywordScore`：关键词命中分数

```http
POST /api/rag/ask
Authorization: Bearer <token>
Content-Type: application/json

{
  "kbId": "knowledgeBaseId",
  "question": "这个项目的核心功能是什么？"
}
```

问答成功后会自动保存历史记录。

查询问答历史：

```http
GET /api/rag/history?kbId={knowledgeBaseId}
Authorization: Bearer <token>
```

删除问答历史：

```http
DELETE /api/rag/history/{id}
Authorization: Bearer <token>
```

提交问答反馈：

```http
PUT /api/rag/history/{id}/feedback
Authorization: Bearer <token>
Content-Type: application/json

{
  "feedbackScore": 1,
  "feedbackNote": "回答有帮助"
}
```

`feedbackScore` 取值：`1` 表示有帮助，`-1` 表示没帮助，`0` 或 `null` 表示清除反馈。

前端问答历史支持按关键词搜索，并可按反馈状态筛选，便于复盘低质量回答。

## 仪表盘接口

查询当前用户的数据概览：

```http
GET /api/dashboard/overview
Authorization: Bearer <token>
```

返回知识库数量、文档数量、切片数量、问答次数、文档解析状态分布和最近问题。

查询文档解析任务：

```http
GET /api/dashboard/tasks?status=FAILED&limit=20
Authorization: Bearer <token>
```

`status` 可选：`ALL / UPLOADED / PARSING / COMPLETED / FAILED`。前端任务中心支持按状态筛选失败任务，并对未超过重试上限的失败文档重新提交解析。

## 接口日志

后端通过 `OncePerRequestFilter + SLF4J` 记录 `/api/**` 请求日志，包含：

- 请求方法
- 请求路径
- HTTP 状态码
- 当前用户 ID
- 接口耗时
- 未处理异常信息

示例：

```text
API method=GET path=/api/kb status=200 userId=123 durationMs=18
```

## Redis 限流

系统已接入 Redis 固定窗口限流：

- 登录接口：同一 IP 每 60 秒最多 10 次
- RAG 问答接口：同一用户每 60 秒最多 20 次
- 超限返回 `429 请求过于频繁`

Redis 相关模块的技术方案和原理统一记录在：

```text
docs/Redis模块设计说明.md
```

## Redis 问答缓存

系统已接入 RAG 问答缓存：

- 同一用户、同一知识库、同一问题在 10 分钟内重复提问时，直接返回缓存答案
- 缓存命中不新增问答历史，避免历史刷屏
- 上传、解析、删除文档后，会清理对应知识库下的问答缓存
- Redis 不可用时自动降级，继续走正常问答流程

## Redis 分布式锁

系统已接入文档解析分布式锁：

- 同一个文档同一时间只能提交一个解析任务
- 锁 Key 为 `lock:doc:parse:{documentId}`
- 使用 Redis `SET NX EX` 加锁
- 使用 Lua 脚本校验锁值后释放，避免误删其他任务的锁
- Redis 不可用时降级为数据库 `PARSING` 状态保护

## 核心技术文档

系统中适合面试讲解的技术模块统一记录在：

```text
docs/核心技术模块说明.md
```

目前已记录：

- 异步文档解析任务
- 异步任务失败重试保护
- 异步任务监控视图
- 本地 Embedding 与向量检索模块
- 多格式文档解析模块
- 轻量任务中心
- 知识库删除级联清理
- RAG 问答反馈闭环

## 开发路线

1. 用户注册、登录、JWT 鉴权
2. 知识库 CRUD
3. 文档上传与状态机
4. 文档解析、清洗与 chunk 切分
5. 关键词检索版 RAG 问答与引用来源
6. 问答历史记录
7. 仪表盘统计
8. 接口日志与请求耗时统计
9. Redis 接口限流
10. Redis RAG 问答缓存
11. 异步文档解析任务
12. 异步任务失败重试保护
13. 异步任务监控视图
14. Embedding 与向量检索
15. Redis 分布式锁
16. 后台统计与任务监控

## 前端启动

```bash
cd frontend
npm install
npm run dev
```

默认访问：

```http
http://localhost:5173
```
