# Aura Frontend

Aura 智能体工作台的前端，纯前端工程，独立于后端，通过 HTTP 接口与 Java 后端（`Aura-backend-server`，端口 8001）通信。

## 技术栈

- Vue 3 + TypeScript + Vite 5
- Pinia（状态）、Vue Router 4（路由）、Axios（请求）
- Tailwind CSS 3（样式，暖色单色系设计系统）
- marked + DOMPurify（Markdown 渲染与消毒）
- 自定义线性 SVG 图标（无第三方图标库依赖）

## 目录结构

```
src/
  api/            # 请求层：config / http / sse / 各模块接口
  components/     # ui/ 基础组件、layout/ 布局、chat/ 对话组件
  constants/      # 枚举与导航配置
  stores/         # pinia：auth / toast / notification
  styles/         # main.css（Tailwind + 组件类）
  types/          # 与后端实体对应的类型
  utils/          # format / asset
  views/          # 页面：auth / chat / agent / kb / workspace / notification / feedback / profile / admin
  router/         # 路由与鉴权守卫
```

## 快速开始

```bash
npm install
npm run dev        # 开发，端口 5173
npm run build      # 生产构建（vite build）
npm run preview    # 预览构建产物
```

## 环境与代理

- 后端 Java：`http://localhost:8001`，context-path 为 `/aura`
- 前端开发环境通过 Vite 代理将 `/aura/**` 转发到 `localhost:8001`（见 `vite.config.ts`），同时覆盖接口与静态资源（`/aura/uploads/**`）
- API 基础路径由 `.env` 的 `VITE_API_BASE` 控制，生产环境可改为后端域名或同源

## 关键约定

### 鉴权

- 登录成功返回 `data.token`（Sa-Token 值），前端存于 `localStorage`（key `aura_token`）
- 所有请求统一在请求头携带 `satoken: <token>`（见 `api/http.ts`）
- 401 时自动清除 token 并跳转登录

### 响应包装

后端统一返回 `Result<T> = { code, msg, data }`，`code === 200` 表示成功。Axios 拦截器已统一解包与错误提示，业务代码直接：

```ts
const { data } = await agentApi.list()
// data 为 Result<Page<Agent>>，data.data 为业务数据
```

### 流式对话（SSE）

对话接口是 **POST 返回的 SSE**，不能用原生 `EventSource`，故用 `fetch` 流式解析（见 `api/sse.ts`）。

- 发消息：`POST /chat/send/{agentId}`，body `{ humanContent, enableWebSearch }`
- 工具调用中断续接：`POST /chat/tool_allow/{agentId}`，body `{ choice, edition, enableWebSearch }`
- Python 产出 `data: <text>` / `event: interrupt` + `data: <json>`；Java 用 `SseEmitter` 转发会二次包裹一层 `data:`，前端解析器已做去前缀容错
- 后端不发显式 `done` 事件，流关闭即视为结束

## 设计系统

采用「克制的极简 + 编辑排版」风格：暖色单色系（`#F7F6F3` 画布 / `#FFFFFF` 表面 / `#111111` 墨色 / `#787774` 次级），仅以灰调柔色表达语义（红/蓝/绿/黄），无渐变、无重阴影、无 emoji、系统字体优先。

响应式：

- 桌面（`md+`）：左侧 240px 固定侧栏 + 顶栏
- 移动：底部标签栏 + 顶栏（品牌 + 通知 + 用户菜单）

## 待后端确认 / 已知限制

以下点因后端未提供对应能力或字段语义未完全确认，已在代码中标注，接入时需核对：

1. **用户分页列表接口缺失**：`SysUserController` 无「用户列表」接口，管理后台「用户管理」页暂以「按 ID 封禁/解封」占位，待后端补充列表接口后接入。
2. **DTO 字段语义**：`SetRoleDto`（`type: 'set_admin' | 'remove_admin'`）、`UpdateWorkspaceDto`（`workspaceId` 字段）、`AiImageDto`（AI 生成头像的临时文件名）、`BanUserDto`（`type` 的临时/永久语义）等字段命名/取值，接入真实后端时需按实际 DTO 微调。
3. **知识库 `status` 语义**：逻辑删除后的状态值未完全确认，列表页未强依赖该字段。
