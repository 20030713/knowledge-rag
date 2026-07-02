# Docker 一键启动说明

本文档说明如何用 Docker Compose 一次性启动前端、后端、MySQL 和 Redis。

## 1. 启动命令

在项目根目录执行：

```bash
docker compose up --build
```

后台启动：

```bash
docker compose up -d --build
```

启动完成后访问：

```text
前端：http://localhost:5173
后端：http://localhost:8080/api/health
接口文档：http://localhost:8080/doc.html
```

## 2. 服务组成

| 服务 | 容器名 | 端口 | 说明 |
|---|---|---|---|
| MySQL | `knowledge-rag-mysql` | `3307 -> 3306` | 业务数据库 |
| Redis | `knowledge-rag-redis` | `6379` | 限流、缓存、分布式锁 |
| Backend | `knowledge-rag-backend` | `8080` | Spring Boot 后端 |
| Frontend | `knowledge-rag-frontend` | `5173` | Nginx 托管 React 静态资源 |

## 3. 数据卷

Compose 会创建三个 volume：

```text
mysql_data   -> MySQL 数据
redis_data   -> Redis 数据
upload_data  -> 后端上传文件
```

这些数据不会因为容器重启而丢失。

## 4. 容器内配置

后端容器通过环境变量覆盖 `application.yml`：

```yaml
MYSQL_HOST: mysql
MYSQL_PASSWORD: root
REDIS_HOST: redis
UPLOAD_ROOT_PATH: /app/uploads
```

本地直接用 Maven 启动时，仍然使用 `application.yml` 中的默认值：

```text
MySQL: localhost:3306
Redis: localhost:6379
上传目录: uploads
```

## 5. 前端代理

Docker 部署时，前端由 Nginx 托管。

浏览器访问：

```text
http://localhost:5173
```

前端请求 `/api/**` 时，由 Nginx 代理到：

```text
http://backend:8080/api/**
```

这样浏览器不需要直接知道后端容器地址，也可以避免跨域问题。

## 6. 常用命令

查看容器：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose logs -f redis
```

停止服务：

```bash
docker compose down
```

停止并删除数据卷：

```bash
docker compose down -v
```

注意：`down -v` 会删除数据库、Redis 数据和上传文件，只在想重置环境时使用。

## 7. 常见问题

### 端口被占用

如果本机已经有 MySQL、Redis、后端或前端占用了端口，可以修改 `docker-compose.yml` 左侧端口：

```yaml
ports:
  - "5174:80"
```

左侧是宿主机端口，右侧是容器端口。

项目默认把容器 MySQL 映射到宿主机 `3307`：

```yaml
ports:
  - "3307:3306"
```

这样可以避免和本机已有 MySQL 的 `3306` 冲突。后端容器之间通信仍然使用 `mysql:3306`，不受宿主机端口影响。

### 修改 `sql/init.sql` 后没有生效

MySQL 只会在数据目录首次初始化时执行 `/docker-entrypoint-initdb.d` 下的脚本。

如果要重新执行初始化脚本，需要删除数据卷：

```bash
docker compose down -v
docker compose up --build
```

当前后端也有 `DatabaseSchemaInitializer`，会在启动时自动检查并补齐开发阶段需要的表和字段。

### 后端连不上 MySQL

确认后端使用的是容器服务名：

```text
MYSQL_HOST=mysql
```

不要在容器里写 `localhost` 连接 MySQL。容器内的 `localhost` 指的是后端容器自己，不是 MySQL 容器。
