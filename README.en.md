# Aura · Agent Workbench

> 🫧 **Pure Talk** — an all-in-one AI agent workbench built on LLM and multi-agent technology.
> Supports agent creation, knowledge-base Q&A, multi-turn streaming chat, long-term memory, web search, AI avatar generation, team collaboration, and admin management.
> Powered by **ThinkUnderStar**.

Aura is a decoupled three-service project. The **Java backend (`Aura-backend-server`) is the self-built core**, carrying all business logic, authentication, rate limiting, sensitive-word filtering, and streaming-chat orchestration.

> 中文版：[README.md](./README.md)

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Backend Design (self-built core)](#backend-design-self-built-core)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Overview](#api-overview)

---

## Overview

- **Agents**: create / search / archive / bind knowledge bases.
- **Streaming chat**: SSE typewriter-style multi-turn output with tool-call interruption confirmation (e.g. "save this memory?").
- **RAG Q&A**: multi-query rewrite + cross-encoder rerank + summary compression.
- **Long-term memory**: user-level memory (cross-agent, stored in PostgreSQL) and agent-level session memory (stored in Milvus).
- **Web search**: Tavily integration.
- **AI avatars**: ComfyUI workflow.
- **Collaboration**: workspaces, members, roles, invite codes, operation logs.
- **Governance**: notifications, feedback, reports, banning, admin console.

## Architecture

```
┌─────────────────────┐
│   Aura-frontend      │  Vue 3 + TypeScript + Vite (5173)
└──────────┬──────────┘
           │ HTTP / SSE (context-path /aura)
           ▼
┌─────────────────────┐   WebClient  ┌─────────────────────┐
│  Aura-backend-server │────────────▶│  Aura-backend-ai     │
│  Java 21 / Spring    │◀────────────│  Python / FastAPI    │
│  Boot 3.5 (8001)     │     SSE      │  + LangGraph (8002)  │
│  ★ self-built core   │             │  AI / RAG / memory   │
└──────┬──────┬───────┘             └──────┬──────┬───────┘
   MySQL   Redis                    PostgreSQL  Milvus
```

| Service | Stack | Port | Responsibility |
| --- | --- | --- | --- |
| `Aura-backend-server` | Java 21 / Spring Boot 3.5 | 8001 | **Self-built core**: auth, CRUD, rate limiting, sensitive words, SSE orchestration |
| `Aura-backend-ai` | Python / FastAPI + LangGraph | 8002 | AI inference: LangGraph agent, RAG, memory, image gen, web search |
| `Aura-frontend` | Vue 3 + TS + Vite | 5173 | UI, talks to the main backend over HTTP/SSE |

## Tech Stack

**Backend (self-built)**

- Java 21, Spring Boot 3.5.16, virtual threads
- MyBatis-Plus 3.5.15 + MySQL 8
- Sa-Token 1.45.0 (auth, Redis-backed)
- Spring Data Redis + commons-pool2
- Spring WebFlux / WebClient (SSE proxy to the AI service)
- JBCrypt, Hutool 5.8 (DFA sensitive words), Lombok, Actuator

**AI service**

- FastAPI, Uvicorn
- LangChain 1.3 / LangGraph 1.2 / langmem (summarization)
- PyMilvus, SQLAlchemy (async) + asyncmy, `langgraph-checkpoint-postgres`
- Ollama, transformers + sentence-transformers (reranker)
- OpenAI-compatible client (DeepSeek), ComfyUI, Tavily

**Frontend**

- Vue 3 + TypeScript + Vite 5, Pinia, Vue Router 4, Axios
- Tailwind CSS 3, marked + DOMPurify

## Backend Design (self-built core)

### Layering

```
controller → service/core → service/wrapper → mapper (MyBatis-Plus)
```

- `controller`: HTTP layer, parameter validation, `@SaCheckLogin` / `@SaCheckRole` annotations.
- `service/core`: core business logic (login, chat orchestration, knowledge bases, workspaces, …).
- `service/wrapper`: MyBatis-Plus `IService` wrapper, isolating data access.
- `mapper`: MyBatis-Plus mappers with a few custom SQL queries (e.g. paginated VO queries).

### Authentication (Sa-Token)

Login issues a token; the frontend sends `satoken: <token>` in the header. Endpoints are protected by `@SaCheckLogin` / `@SaCheckRole("admin")`. Tokens live in Redis.

### Rate limiting (Redis token bucket)

`RedisTokenBucketLimiter` implements a token-bucket algorithm in **Lua** for atomicity, keyed per user. Chat: capacity 20, rate 1/s; destructive ops (clear / backtrack): capacity 5, rate 0.1/s.

### Sensitive-word filtering (DFA trie)

`SensitiveWordManager` loads words from the DB at startup into a Hutool `WordTree` (DFA trie) and marks messages before they reach the AI service.

### Streaming orchestration (SSE proxy + interruption)

The main backend calls the Python AI service via WebClient and relays its SSE stream to the frontend. When the AI side needs confirmation (e.g. save memory), it emits `[INTERRUPT]<json>`; the backend stores a `tool_confirm` message and pushes `event: interrupt` to the frontend. The frontend then calls `/chat/tool_allow` to resume.

### Dual memory

- **User memory**: cross-agent, in PostgreSQL `store` (`users_memory:{user_id}`), with user confirmation.
- **Agent memory**: one Milvus collection per agent (`aura_agent_{agent_id}_session_memory`).

## Project Structure

```
Aura/
├── Aura-backend-server/        # ★ Java main backend (self-built core)
│   └── src/main/java/thinkunderstar/aura/aurabackendserver/
│       ├── controller/         # REST controllers (auth/agent/chat/kb/document/workspace/...)
│       ├── service/
│       │   ├── core/           # core business (incl. SensitiveWordManager)
│       │   └── wrapper/        # MyBatis-Plus wrapper layer
│       ├── mapper/             # data access
│       ├── entity/             # entities (User/Agent/Message/Workspace/...)
│       ├── dto/                # request/response DTOs
│       ├── config/             # config (Redis/WebClient/SaToken/MybatisPlus/...)
│       ├── util/               # utils (limiter/desensitize/sms/mail/...)
│       ├── handler/            # global exception handling, field filling
│       └── common/Result.java  # unified response wrapper
│   └── src/main/resources/     # application.yaml, banner.txt
├── Aura-backend-ai/            # Python AI service (FastAPI + LangGraph)
│   └── app/
│       ├── api/v1/             # chat/document/kb/avatar/agent routers
│       ├── services/
│       │   ├── agent/          # LangGraph graph, nodes, tools, prompts
│       │   ├── rag/            # RAG retrieval, embedding, vector store
│       │   └── v1/             # service implementations
│       ├── db/                 # mysql/milvus/postgresql connections
│       ├── core/               # config, LLM instances, logging, ComfyUI, Tavily
│       └── models/             # request/response/state models
├── Aura-frontend/              # Vue 3 frontend
│   └── src/                    # api/ components/ views/ stores/ router/ utils/ ...
├── README.md
├── README.en.md
└── LICENSE
```

## Prerequisites

| Dependency | Version | Purpose |
| --- | --- | --- |
| JDK | 21 | backend runtime |
| Maven | 3.8+ | backend build |
| MySQL | 8.x | business data |
| Redis | 6+ | sessions + rate limiting |
| PostgreSQL | 14+ | checkpoints + memory |
| Milvus | 2.x | vector search |
| Python | 3.12 | AI service runtime |
| Node.js | 18+ | frontend build |
| Ollama | optional | local embed/summary models |
| ComfyUI | optional | avatar generation |
| DeepSeek / OpenAI-compatible | required | chat model |
| Tavily API Key | optional | web search |

## Quick Start

### 1. Main backend

```bash
cd Aura-backend-server
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
# edit application.yaml, fill in your MySQL password
mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Listens on `http://localhost:8001/aura`.

### 2. AI service

```bash
cd Aura-backend-ai
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
# edit .env: MySQL/PostgreSQL passwords, model API key, Tavily key
uvicorn main:app --host 0.0.0.0 --port 8002
```

Listens on `http://localhost:8002` (prefix `/api/v1`).

### 3. Frontend

```bash
cd Aura-frontend
npm install
npm run dev              # http://localhost:5173
```

**Suggested order**: infra (MySQL → Redis → PostgreSQL → Milvus → Ollama) → AI service → main backend → frontend.

## Configuration

Real config files are git-ignored; only templates are committed.

| File | Template | Notes |
| --- | --- | --- |
| `Aura-backend-server/src/main/resources/application.yaml` | `application-example.yaml` | MySQL/Redis/Sa-Token/port/logging |
| `Aura-backend-ai/.env` | `.env.example` | MySQL/Milvus/PostgreSQL/models/ComfyUI/Tavily |
| `Aura-frontend/.env` | (committed, non-sensitive) | `VITE_API_BASE` |

Ports: backend `8001` (context-path `/aura`), AI `8002` (`/api/v1`), frontend `5173`. The backend calls the AI service at `http://localhost:8002` (see `WebClientConfig`).

## API Overview

All endpoints return `Result<T> = { code, msg, data }`; `code === 200` means success.

| Module | Main endpoints |
| --- | --- |
| Auth | `POST /auth/login`, `/auth/code`, `/auth/register/user`, `/auth/register/admin`, `DELETE /auth/logout`, `PUT /auth/ban`, `/auth/unban` |
| Agent | `POST /agent/create`, `GET /agent/get`, `/agent/search`, `PUT /agent/update`, `PUT /agent/{id}/kbs`, `DELETE /agent/delete` |
| Chat | `GET /chat/get/{agentId}`, `POST /chat/send/{agentId}` (SSE), `POST /chat/tool_allow/{agentId}` (SSE), `PUT /chat/update/{messageId}` (SSE), `DELETE /chat/clear/{agentId}` |
| Knowledge base | `POST /kb/create`, `GET /kb/get`, `PUT /kb/update/my`, `/kb/update/team`, `DELETE /kb/delete/logic`, `/kb/delete/force`, `GET /kb/search` |
| Document | `POST /document/upload`, `GET /document/get`, `/document/content`, `DELETE /document/delete` |
| Workspace | `GET /workspace/get`, `POST /workspace/create`, `POST /workspace/update`, `PUT /workspace/invite-code/reset`, `DELETE /workspace/delete` |
| Member | `POST /member/join`, `DELETE /member/quit`, `/member/remove`, `PUT /member/set-role`, `/member/owner/transfer` |
| Notification | `GET /notification/get`, `PUT /notification/read`, `/read-all`, `GET /notification/unread-count` |
| Feedback / Report | `POST /feedback/submit`, `GET /feedback/list`, `PUT /feedback/reply`; `POST /report/submit`, `GET /report/list`, `PUT /report/handle` |
| User | `PUT /user/update`, `PUT /user/avatar`, `POST /user/avatar/generate`, `GET /user/get/{userId}` |

## License

See [LICENSE](./LICENSE).
