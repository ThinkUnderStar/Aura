# Aura · 智能体工作台

> 🫧 **Pure Talk** —— 基于大模型与多智能体技术的一体化 AI 智能体工作台。
> 支持智能体创建、知识库问答、多轮流式对话、长期记忆、联网搜索、AI 头像生成、团队协作与后台管理。
> Powered by **ThinkUnderStar**.

Aura 是一个由三个服务组成的前后端分离项目，其中 **Java 主后端（`Aura-backend-server`）为本项目自研核心**，承载了全部业务逻辑、鉴权、限流、敏感词过滤与流式对话编排。

> English version: [README.en.md](./README.en.md)

---

## 目录

- [一、项目简介](#一项目简介)
- [二、系统架构](#二系统架构)
- [三、技术栈](#三技术栈)
- [四、后端架构详解（自研核心）](#四后端架构详解自研核心)
- [五、目录结构](#五目录结构)
- [六、环境依赖](#六环境依赖)
- [七、快速开始](#七快速开始)
- [八、配置说明](#八配置说明)
- [九、核心接口一览](#九核心接口一览)
- [十、已知限制](#十已知限制)

---

## 一、项目简介

Aura 提供以下能力：

- **智能体（Agent）**：创建、搜索、归档、绑定知识库。
- **流式对话**：基于 SSE 的打字机式多轮流式输出，支持工具调用中断确认（如「是否保存记忆」）。
- **RAG 知识库问答**：多查询改写 + 精排序 + 摘要压缩的检索增强流程。
- **长期记忆**：用户级记忆（跨 Agent 共享，存于 PostgreSQL）与 Agent 级会话记忆（存于 Milvus 向量库）双记忆体系。
- **联网搜索**：集成 Tavily，可实时检索外部信息。
- **AI 头像生成**：集成 ComfyUI 工作流。
- **团队协作**：工作空间（Workspace）与成员管理、角色权限、邀请码、操作日志。
- **平台治理**：通知、反馈、举报、封禁、管理员后台。

## 二、系统架构

```
┌─────────────────────┐
│   Aura-frontend      │  Vue 3 + TypeScript + Vite (端口 5173)
│   (浏览器客户端)      │
└──────────┬──────────┘
           │ HTTP / SSE（context-path: /aura，开发环境经 Vite 代理转发）
           ▼
┌─────────────────────┐        ┌─────────────────────┐
│  Aura-backend-server │──────▶│  Aura-backend-ai     │
│  Java 21 / Spring    │ WebClient│ Python / FastAPI     │
│  Boot 3.5 (端口 8001) │◀──────│  + LangGraph (8002)  │
│  ★ 自研业务核心       │  SSE    │  AI 推理 / RAG / 记忆 │
└──────┬──────┬───────┘        └──────┬──────┬───────┘
       │      │                       │      │
   MySQL   Redis               PostgreSQL  Milvus
  (业务数据) (会话/限流)        (检查点/记忆) (向量库)

外部依赖：Ollama（本地模型）、ComfyUI（生图）、DeepSeek（对话模型）、Tavily（联网搜索）
```

三端职责：

| 服务 | 技术 | 端口 | 职责 |
| --- | --- | --- | --- |
| `Aura-backend-server` | Java 21 / Spring Boot 3.5 | 8001 | **业务核心（自研）**：鉴权、业务 CRUD、限流、敏感词、SSE 流式编排与透传 |
| `Aura-backend-ai` | Python / FastAPI + LangGraph | 8002 | AI 推理：LangGraph 智能体、RAG、双记忆、生图、联网搜索 |
| `Aura-frontend` | Vue 3 + TS + Vite | 5173 | 用户界面，通过 HTTP/SSE 与主后端通信 |

## 三、技术栈

**后端主服务（自研）**

- Java 21、Spring Boot 3.5.16、虚拟线程
- MyBatis-Plus 3.5.15（ORM）+ MySQL 8
- Sa-Token 1.45.0（认证鉴权，整合 Redis）
- Spring Data Redis + commons-pool2（RedisTemplate 与连接池）
- Spring WebFlux / WebClient（调用 AI 服务并透传 SSE）
- JBCrypt（密码哈希）、Hutool 5.8（DFA 敏感词过滤）、Lombok、Actuator

**AI 服务**

- FastAPI、Uvicorn
- LangChain 1.3 / LangGraph 1.2 / langmem（对话压缩）
- PyMilvus（向量库）、SQLAlchemy（async）+ asyncmy（MySQL）
- `langgraph-checkpoint-postgres`（PostgreSQL 检查点与 Store 记忆）
- Ollama（本地嵌入/摘要模型）、transformers + sentence-transformers（重排序）
- OpenAI 兼容客户端（DeepSeek）、ComfyUI、Tavily

**前端**

- Vue 3 + TypeScript + Vite 5、Pinia、Vue Router 4、Axios
- Tailwind CSS 3、marked + DOMPurify（Markdown 渲染与消毒）

## 四、后端架构详解（自研核心）

主后端 `Aura-backend-server` 采用经典分层架构，并针对「AI 工作台」场景做了若干专门设计：

### 4.1 分层结构

```
controller → service/core → service/wrapper → mapper（MyBatis-Plus）
     │              │              │
  路由/参数校验   核心业务编排    数据访问封装      实体 → 表
```

- `controller`：接收 HTTP 请求，做参数校验与权限注解（`@SaCheckLogin` / `@SaCheckRole`）。
- `service/core`：核心业务逻辑（登录、对话编排、知识库、工作空间等）。
- `service/wrapper`：MyBatis-Plus `IService` 的封装层，隔离数据访问细节。
- `mapper`：MyBatis-Plus Mapper，含少量自定义 SQL（如分页 VO 查询）。

### 4.2 认证鉴权（Sa-Token）

- 登录成功后签发 token，前端在请求头携带 `satoken: <token>`。
- 接口用 `@SaCheckLogin`（需登录）与 `@SaCheckRole("admin")`（仅管理员）做细粒度控制。
- token 存储于 Redis（`sa-token-redis-template`），支持多端登录、冻结、注销。

### 4.3 限流（Redis 令牌桶）

`RedisTokenBucketLimiter` 用 **Lua 脚本实现令牌桶算法**，按用户维度限流：

- 对话接口：容量 20、速率 1 个/秒；
- 清空会话 / 回溯对话等敏感操作：容量 5、速率 0.1 个/秒（每 10 秒 1 次）。

Lua 脚本保证原子性，避免并发下的超发问题。

### 4.4 敏感词过滤（DFA 字典树）

`SensitiveWordManager` 启动时（`@PostConstruct`）从数据库加载敏感词，构建 Hutool `WordTree`（DFA 字典树），对话前 `checkSensitiveWord` 做 `O(n)` 级匹配，命中则在请求体中标记 `is_sensitive`，由 AI 端图内分流拦截。

### 4.5 流式对话编排（SSE 透传 + 中断确认）

主后端本身不调用大模型，而是通过 **WebClient 调用 Python AI 服务**，并将 AI 端的 SSE 流透传给前端：

1. 前端 `POST /chat/send/{agentId}` 建立 SSE 连接；
2. 主后端组装 `ChatVODto`（含用户 ID、知识库绑定、是否联网、敏感词标记），转发到 `http://localhost:8002/api/v1/chat/send/{agentId}`；
3. 逐块接收 AI 端返回，用 `SseEmitter` 转发；
4. 若 AI 端需要用户确认（如保存记忆），返回 `[INTERRUPT]<json>`，主后端落库一条 `tool_confirm` 消息并向前端推送 `event: interrupt`；
5. 前端确认后调用 `POST /chat/tool_allow/{agentId}`，主后端透传选择（`approve` / `reject` / `edit`）继续执行。

此外支持：清空会话（`/chat/clear`，同时清理 MySQL / PostgreSQL / Milvus）、消息回溯（`/chat/update`，从 checkpoint 恢复重新生成）。

### 4.6 双记忆体系（由主后端 + AI 端协同）

- **用户级记忆**：跨 Agent 共享，存于 PostgreSQL `store`（命名空间 `users_memory:{user_id}`），保存/删除需用户中断确认。
- **Agent 级会话记忆**：每个 Agent 一个 Milvus 集合 `aura_agent_{agent_id}_session_memory`，支持语义检索历史对话。

## 五、目录结构

```
Aura/
├── Aura-backend-server/        # ★ Java 主后端（自研核心）
│   └── src/main/java/thinkunderstar/aura/aurabackendserver/
│       ├── controller/         # REST 控制器（auth/agent/chat/kb/document/workspace/...）
│       ├── service/
│       │   ├── core/           # 核心业务（含 SensitiveWordManager）
│       │   └── wrapper/        # MyBatis-Plus 封装层
│       ├── mapper/             # 数据访问
│       ├── entity/             # 实体（User/Agent/Message/Workspace/...）
│       ├── dto/                # 请求/响应 DTO
│       ├── config/             # 配置（Redis/WebClient/SaToken/MybatisPlus/...）
│       ├── util/               # 工具（限流/脱敏/短信/邮件/...）
│       ├── handler/            # 全局异常处理、字段填充
│       └── common/Result.java  # 统一响应包装
│   └── src/main/resources/     # application.yaml、banner.txt
├── Aura-backend-ai/            # Python AI 服务（FastAPI + LangGraph）
│   └── app/
│       ├── api/v1/             # chat/document/kb/avatar/agent 路由
│       ├── services/
│       │   ├── agent/          # LangGraph 图、节点、工具、提示词
│       │   ├── rag/            # RAG 检索、嵌入、向量库
│       │   └── v1/             # 各服务实现
│       ├── db/                 # mysql/milvus/postgresql 连接
│       ├── core/               # 配置、LLM 实例、日志、ComfyUI、Tavily
│       └── models/             # 请求/响应/状态模型
├── Aura-frontend/              # Vue 3 前端
│   └── src/                    # api/ components/ views/ stores/ router/ utils/ ...
├── README.md
├── README.en.md
└── LICENSE
```

## 六、环境依赖

| 依赖 | 版本要求 | 用途 |
| --- | --- | --- |
| JDK | 21 | 主后端运行 |
| Maven | 3.8+ | 主后端构建 |
| MySQL | 8.x | 业务数据 |
| Redis | 6+ | Sa-Token 会话、令牌桶限流 |
| PostgreSQL | 14+（已测 16） | LangGraph 检查点 + 长期记忆 |
| Milvus | 2.x（已测 2.6） | 向量检索 |
| Python | 3.12 | AI 服务运行 |
| Node.js | 18+ | 前端构建 |
| Ollama | 可选（默认启用） | 本地嵌入/摘要/重排序模型 |
| ComfyUI | 可选 | AI 头像生成 |
| DeepSeek / OpenAI 兼容 API | 必填 | 用户交互对话模型 |
| Tavily API Key | 可选 | 联网搜索 |

## 七、快速开始

> 假设各依赖服务（MySQL / Redis / PostgreSQL / Milvus）已在本机默认端口启动，且已建好数据库 `aura`。

### 1. 启动主后端（Aura-backend-server）

```bash
cd Aura-backend-server
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
# 编辑 application.yaml，填入你的 MySQL 密码

mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
# 或：mvn spring-boot:run
```

启动成功后监听 `http://localhost:8001/aura`。

### 2. 启动 AI 服务（Aura-backend-ai）

```bash
cd Aura-backend-ai
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp .env.example .env
# 编辑 .env，填入 MySQL 密码、PostgreSQL 密码、模型 API Key、Tavily Key

uvicorn main:app --host 0.0.0.0 --port 8002
```

启动成功后监听 `http://localhost:8002`，接口前缀 `/api/v1`。

### 3. 启动前端（Aura-frontend）

```bash
cd Aura-frontend
npm install
npm run dev              # http://localhost:5173
```

浏览器访问 `http://localhost:5173`，注册 / 登录后即可使用。

### 启动顺序建议

依赖服务（MySQL → Redis → PostgreSQL → Milvus → Ollama）→ AI 服务 → 主后端 → 前端。

## 八、配置说明

> 真实配置文件均已被 `.gitignore` 忽略，仓库中只提交样板文件，请勿提交任何含密钥的配置。

| 文件 | 样板 | 说明 |
| --- | --- | --- |
| `Aura-backend-server/src/main/resources/application.yaml` | `application-example.yaml` | 主后端：MySQL/Redis/Sa-Token/端口/日志 |
| `Aura-backend-ai/.env` | `.env.example` | AI 服务：MySQL/Milvus/PostgreSQL/模型/ComfyUI/Tavily |
| `Aura-frontend/.env` | （直接提交，非敏感） | 前端：`VITE_API_BASE` |

关键端口与路径约定：

- 主后端：`8001`，context-path `/aura`
- AI 服务：`8002`，前缀 `/api/v1`
- 前端：`5173`，开发环境将 `/aura/**` 代理到 `localhost:8001`
- 主后端通过 `WebClientConfig` 固定访问 `http://localhost:8002` 调用 AI 服务

## 九、核心接口一览

主后端统一返回 `Result<T> = { code, msg, data }`，`code === 200` 为成功。

| 模块 | 主要接口 |
| --- | --- |
| 认证 | `POST /auth/login`、`/auth/code`、`/auth/register/user`、`/auth/register/admin`、`DELETE /auth/logout`、`PUT /auth/ban`、`/auth/unban` |
| 智能体 | `POST /agent/create`、`GET /agent/get`、`/agent/search`、`PUT /agent/update`、`PUT /agent/{id}/kbs`、`DELETE /agent/delete` |
| 对话 | `GET /chat/get/{agentId}`、`POST /chat/send/{agentId}`（SSE）、`POST /chat/tool_allow/{agentId}`（SSE）、`PUT /chat/update/{messageId}`（SSE）、`DELETE /chat/clear/{agentId}` |
| 知识库 | `POST /kb/create`、`GET /kb/get`、`PUT /kb/update/my`、`/kb/update/team`、`DELETE /kb/delete/logic`、`/kb/delete/force`、`GET /kb/search` |
| 文档 | `POST /document/upload`、`GET /document/get`、`/document/content`、`DELETE /document/delete` |
| 工作空间 | `GET /workspace/get`、`POST /workspace/create`、`POST /workspace/update`、`PUT /workspace/invite-code/reset`、`DELETE /workspace/delete` |
| 成员 | `POST /member/join`、`DELETE /member/quit`、`/member/remove`、`PUT /member/set-role`、`/member/owner/transfer` |
| 通知 | `GET /notification/get`、`PUT /notification/read`、`/read-all`、`GET /notification/unread-count` |
| 反馈 / 举报 | `POST /feedback/submit`、`GET /feedback/list`、`PUT /feedback/reply`；`POST /report/submit`、`GET /report/list`、`PUT /report/handle` |
| 用户 | `PUT /user/update`、`PUT /user/avatar`、`POST /user/avatar/generate`、`GET /user/get/{userId}` |

## 十、已知限制

1. **管理后台「用户管理」按 ID 操作**：`SysUserController` 未提供分页用户列表接口，后台采用「输入用户 ID 查询 → 封禁 / 延长封禁 / 解封」的交互。
2. **知识库 `status` 语义**：逻辑删除后的状态值未在前后端完全约定，列表页未强依赖该字段。
