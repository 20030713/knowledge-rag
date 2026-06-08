# 企业知识库 RAG 问答系统

基于 Spring Boot、MySQL、Redis 和 LangChain4j 的企业知识库 RAG 问答系统。

## 当前阶段

已创建后端项目骨架：

- Spring Boot 3.x Maven 项目
- 统一响应结构
- 全局异常处理
- 基础健康检查接口
- 预留业务包结构：用户、知识库、文档、会话、RAG、向量、存储、异步任务

## 本地启动

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

## 开发路线

1. 用户注册、登录、JWT 鉴权
2. 知识库 CRUD
3. 文档上传与状态机
4. 文档解析、清洗与 chunk 切分
5. Embedding 与向量检索
6. RAG 问答与引用来源
7. Redis 缓存、限流、分布式锁
8. 异步任务、失败重试、后台统计

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
