import json
import logging
from typing import AsyncGenerator

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.types import Command

from app.db.milvus.client import milvus_client
from app.db.postgresql import connect as pg_connect
from app.models.request import ChatDto, ToolAllowDto, UpdateMessageDto
from app.models.response import Result
from app.services.agent.prompts import DEFAULT_SYSTEM_PROMPT


async def chat_with_agent_service(agent_id: int,chat_dto: ChatDto) -> AsyncGenerator[str,None]:
    """
    与指定 Agent 进行流式对话（SSE 实时推送）

    该端点接收用户消息，调用 Python 端的 LangGraph Agent 进行流式推理，
    并实时将生成的文本片段通过 Server-Sent Events (SSE) 推送给前端。
    支持打字机效果、中断确认、错误处理等。

    Args:
        agent_id: Agent ID，同时也是会话 ID，用于标识唯一的对话线程。
        chat_dto: 包含用户消息和可选的配置项（如是否启用联网搜索等）。

    Returns:
        StreamingResponse: 使用 `text/event-stream` 格式的 SSE 流式响应，
        前端可通过 EventSource API 监听并实时渲染。

    Raises:
        HTTPException:
            - 404: Agent 不存在或已删除。
            - 403: 用户无权操作该 Agent（权限校验）。
            - 429: 请求频率超限（限流）。
            - 500: 服务内部错误（如 LangGraph 调用失败）。

    Notes:
        - 该接口需登录访问（SaToken 拦截）。
        - 前端应监听以下事件类型：
            - `text`: 文本片段，直接追加显示。
            - `interrupt`: 需要用户确认（如保存记忆），展示对话框。
            - `done`: 对话结束，关闭连接。
            - `error`: 错误信息，展示提示。
        - 中断确认后，前端需调用 `/resume` 接口继续对话。
    """
    snapshot = await pg_connect.checkpoint.aget_tuple(RunnableConfig(
        configurable={"thread_id": "aura-thread-"+str(agent_id)}
    ))

    #创建追加的messages列表
    messages = []

    if snapshot:
        checkpoint_id = snapshot.config["configurable"]["checkpoint_id"]
    else:
        # 处理没有找到对应 checkpoint 的情况
        checkpoint_id = None

    system_message: SystemMessage
    if checkpoint_id == None:
        system_message = SystemMessage(content=DEFAULT_SYSTEM_PROMPT)
        messages.append(system_message)

    messages.append(HumanMessage(content=chat_dto.human_content))

    config = {
        "configurable": {
            "user_id": "aura-user-" + str(chat_dto.user_id),
            "thread_id": "aura-thread-" + str(agent_id),
            "agent_id": agent_id,
            "knowledge_bases": chat_dto.knowledge_bases,
            "from_checkpoint_id": checkpoint_id,
            "enable_web_search": chat_dto.enable_web_search,
            "is_sensitive": chat_dto.is_sensitive
        }
    }

    # 创建异步生成器
    astream = pg_connect.aura_agent.astream_events(
        input={"messages": messages},
        config=config,
        version="v2",
    )
 
    async for event in astream:
        if event["event"] == "on_chat_model_stream" and event["metadata"]["langgraph_node"] == "llm_node":
            content = event["data"]["chunk"].content

            if content != "":
                yield f"data: {json.dumps(content)}\n\n"

        if (
            event["event"] == "on_chain_stream"
            and isinstance(event["data"].get("chunk"), dict)
            and "__interrupt__" in event["data"]["chunk"]
        ):
            value = event["data"]["chunk"]["__interrupt__"][0].value
            value_json = json.dumps(value)

            yield f"data: [INTERRUPT]{value_json}\n\n"

        if event["event"] == "on_chain_end" and event["name"] == "sensitive_content_handler":
            content = event["data"]["output"]["messages"][0].content
            yield f"data: {json.dumps(content)}\n\n"


async def tool_allow_service(
        tool_allow_dto: ToolAllowDto
) -> AsyncGenerator[str, None]:
    """
    处理工具调用中断的恢复逻辑，返回异步生成器。

    该函数在用户确认或拒绝工具调用中断时被调用，它会：
    1. 根据 `user_id` 和 `agent_id` 构建用于恢复图执行的 `config` 配置。
    2. 根据 `choice` 和 `edition` 构建 `resume_value` 字典。
    3. 调用 `agent.ainvoke(Command(resume=resume_value), config)` 恢复中断的图。
    4. 使用 `astream_events` 流式获取图执行过程中的事件，并通过 `yield` 逐块产出原始数据。

    该生成器产生的数据**不包含 SSE 格式包装**（如 `data:` 前缀或 `\n\n` 分隔），
    而是直接产出图执行过程中产生的原始内容块（如文本片段、中断事件 JSON 等）。
    由上层路由函数负责将其包装为符合 SSE 协议的 `StreamingResponse`。

    Args:
        tool_allow_dto (ToolAllowDto): 包含以下字段的 DTO：
            - `user_id` (int): 用户 ID，用于构建 `user_id` 配置。
            - `agent_id` (int): Agent ID，同时也是 `thread_id` 的一部分，用于状态恢复。
            - `choice` (str): 用户的选择，可选值：
                - `"approve"`：同意工具调用。
                - `"reject"`：拒绝工具调用。
                - `"edit"`：编辑后同意，此时 `edition` 字段必须提供。
            - `edition` (str, 可选)：当 `choice` 为 `"edit"` 时，用户编辑后的新内容。

    Yields:
        str: 图执行过程中产出的原始数据块（文本片段或事件 JSON 字符串），
             由上层路由函数包装为 SSE 格式后发送给前端。

    Raises:
        ValueError: 当 `choice` 为 `"edit"` 但 `edition` 为空或 None 时。
        HTTPException: 当用户无权操作该 Agent 或 Agent 不存在时（由上层路由处理）。
        Exception: LangGraph 执行过程中的其他异常。

    Note:
        - 恢复执行时，`config` 中只需包含 `user_id`、`thread_id`（格式为 `"aura-thread-{agent_id}"`）和 `agent_id`。
        - 该函数假设 `tool_allow_dto` 中 `choice` 字段已被前端正确校验，且 `edition` 在必要时已提供。
        - 该生成器产生的内容块与 `chat_with_agent_service` 的输出格式一致，
          可直接交由同一个 SSE 包装函数处理。
        - 该函数返回的是异步生成器，由 FastAPI 的 `StreamingResponse` 驱动消费。
    """
    config = {
        "configurable": {
            "user_id": "aura-user-" + str(tool_allow_dto.user_id),
            "thread_id": "aura-thread-" + str(tool_allow_dto.agent_id),
            "agent_id": tool_allow_dto.agent_id,
            "enable_web_search": tool_allow_dto.enable_web_search,
        }
    }

    astream = pg_connect.aura_agent.astream_events(
        Command(
            resume = {
                "choice": tool_allow_dto.choice,
                "edit": tool_allow_dto.edition
            },
        ),
        config=config,
        version="v2",
    )

    async for event in astream:
        if event["event"] == "on_chat_model_stream":
            content = event["data"]["chunk"].content

            if content != "":
                yield f"data: {json.dumps(content)}\n\n"

        if (
            event["event"] == "on_chain_stream"
            and isinstance(event["data"].get("chunk"), dict)
            and "__interrupt__" in event["data"]["chunk"]
        ):
            value = event["data"]["chunk"]["__interrupt__"][0].value
            value_json = json.dumps(value)

            yield f"data: [INTERRUPT]{value_json}\n\n"

async def clear_session_message_service(
        agent_id: int
) -> Result[None]:
    """
    清空指定 Agent 的所有会话记忆（**不可恢复**）。

    该接口会彻底删除该 Agent 在以下存储中的所有对话相关数据：
    - **MySQL**：`messages` 表中该 Agent 的所有消息记录（包括 Human、AI 和工具确认消息）
    - **PostgreSQL**：`checkpoints` 表中该 Agent 的所有状态快照（通过 LangGraph checkpointer）
    - **Milvus**：该 Agent 对应的向量集合 `aura_agent_{agent_id}_session_memory`（若存在则删除）

    执行后，该 Agent 将恢复到初始状态，所有历史对话、图执行状态和向量记忆均无法恢复。
    建议前端在调用前展示二次确认弹窗，防止误操作。

    Args:
        agent_id (int): 要清空记忆的 Agent ID（路径参数）

    Returns:
        Result[None]: 统一封装的成功响应（data 为 None）
            - 成功时返回 `{"code": 200, "message": "Agent 记忆已清除", "data": None}`
            - 失败时返回对应的错误码和消息

    Raises:
        HTTPException:
            - 404: Agent 不存在或无关联数据（但即使无数据，删除操作也是幂等的，不会报错）
            - 500: 数据库操作失败、Milvus 删除失败或服务内部异常

    Note:
        - **幂等性**：该接口是幂等的，重复调用不会产生副作用（已删除的数据再次删除视为成功）。
        - **跨存储一致性**：MySQL、PostgreSQL 和 Milvus 的删除操作不会在同一个事务中，
          但整体删除逻辑是“尽力而为”，部分失败会记录日志并返回错误。
        - **性能**：如果该 Agent 的消息量或向量数据量较大，删除操作可能需要几秒到几十秒，
          建议客户端设置合理的超时时间。
        - **与用户级记忆的关系**：该接口只删除 Agent 级别的会话记忆，不会影响用户级记忆（store 中按 `user_id` 隔离的数据）。
        - **依赖服务**：该接口依赖 PostgreSQL checkpointer 和 Milvus 服务，请确保相关服务正常运行。
    """
    try:
        await pg_connect.checkpoint.adelete_thread("aura-thread-" + str(agent_id))
        if await milvus_client.has_collection(f"aura_agent_{agent_id}_session_memory"):
            await milvus_client.drop_collection(f"aura_agent_{agent_id}_session_memory")
        return Result.success(msg="会话记录清除成功")

    except Exception as e:
        logging.error(f"agent: {agent_id} 会话记录清空异常")
        return Result.error(msg="会话记录删除异常")

async def update_message_service(
        update_message_dto: UpdateMessageDto
) -> AsyncGenerator[str,None]:
    """
    修改指定消息的内容，并从该消息处重新执行图，返回异步生成器。

    该函数用于用户编辑某条历史消息后，从对应的 checkpoint 恢复图状态，
    并将新内容作为输入重新生成后续对话。MySQL 中的消息删除和更新由 Java 端负责，
    本函数只专注于图的状态恢复和流式事件生成。

    **核心流程**：
    1. 从 `update_message_dto.message`（MessageEntity）中提取 `from_checkpoint_id` 和 `agent_id`。
    2. 如果 `from_checkpoint_id` 不为 `None`，则从该 checkpoint 恢复状态。
    3. 如果 `from_checkpoint_id` 为 `None`，则先清空该 Agent 的所有持久化记忆（PostgreSQL checkpoints + Milvus 集合），
       然后以全新会话状态启动（相当于从头开始新对话）。
    4. 使用 `update_message_dto.human_content` 作为新的 `HumanMessage` 输入，调用 `astream_events`。
    5. 逐块产出事件数据（文本片段或中断 JSON），由上层路由包装为 SSE 格式。

    **与 `chat_with_agent_service` 的区别**：
    - `chat_with_agent_service`：从当前最新状态继续对话（常规场景）。
    - `update_message`：从指定的历史 checkpoint 恢复，或清空状态后重新开始（修改消息场景）。

    Args:
        update_message_dto (UpdateMessageDto): 包含以下字段的请求 DTO：
            - `message` (MessageEntity): 原消息对象（包含 `id`、`agent_id`、`from_checkpoint_id`、`content` 等）。
            - `human_content` (str): 修改后的新消息内容。
            - `enable_web_search` (int): 是否开启联网搜索（1-开启，0-关闭）。
            - `knowledge_bases` (List[KnowledgeBaseDto]): 知识库列表。

    Yields:
        str: 图执行过程中产出的原始数据块（文本片段或中断事件 JSON 字符串），
             不包含 SSE 格式包装（如 `data:` 前缀），由上层路由包装为 SSE。

    Raises:
        ValueError: 当 `from_checkpoint_id` 为 `None` 且清空记忆操作失败时抛出。
        Exception: LangGraph 执行过程中的其他异常（如状态不存在、图执行错误等）。

    Note:
        - 本函数不操作 MySQL，MySQL 的删除和更新由 Java 端在调用本接口前完成。
        - 如果 `from_checkpoint_id` 为 `None`，会先清空该 Agent 的 PostgreSQL checkpoints 和 Milvus 集合，
          确保状态一致性。
        - 恢复或清空后，图从 `llm_node` 继续执行（`relevant_user_memories` 不会重新运行），
          但 `SystemMessage` 中的知识库和记忆信息已在首次对话时注入，不会丢失。
        - 该生成器产生的数据格式与 `chat_with_agent_service` 一致，
          可直接交由 `StreamingResponse` 包装为 SSE 响应。
        - 本函数不包含权限校验，权限校验由 Java 端和路由层负责。
    """
    collection_name = f"aura_agent_{update_message_dto.message.agent_id}_session_memory"
    messages = []
    if update_message_dto.message.from_checkpoint_id == None:
        await clear_session_message_service(update_message_dto.message.agent_id)
        messages.append(SystemMessage(content=DEFAULT_SYSTEM_PROMPT))
    else:
        await milvus_client.delete(
            collection_name=collection_name,
            filter=f"create_time >= '{update_message_dto.message.create_time.isoformat()}'"
        )

    messages.append(HumanMessage(content=update_message_dto.human_content))

    config = {
        "configurable": {
            "user_id": "aura-user-" + str(update_message_dto.user_id),
            "thread_id": "aura-thread-" + str(update_message_dto.message.agent_id),
            "agent_id": update_message_dto.message.agent_id,
            "knowledge_bases": update_message_dto.knowledge_bases,
            "from_checkpoint_id": update_message_dto.message.from_checkpoint_id,
            "enable_web_search": update_message_dto.enable_web_search,
            "checkpoint_id": update_message_dto.message.from_checkpoint_id,
            "is_sensitive": update_message_dto.is_sensitive
        }
    }

    astream = pg_connect.aura_agent.astream_events(
        input={"messages": messages},
        config=config,
        version="v2",
    )

    async for event in astream:
        if event["event"] == "on_chat_model_stream":
            content = event["data"]["chunk"].content

            if content != "":
                yield f"data: {json.dumps(content)}\n\n"

        if (
            event["event"] == "on_chain_stream"
            and isinstance(event["data"].get("chunk"), dict)
            and "__interrupt__" in event["data"]["chunk"]
        ):
            value = event["data"]["chunk"]["__interrupt__"][0].value
            value_json = json.dumps(value)

            yield f"data: [INTERRUPT]{value_json}\n\n"

        if event["event"] == "on_chain_end" and event["name"] == "sensitive_content_handler":
            content = event["data"]["output"]["messages"][0].content
            yield f"data: {json.dumps(content)}\n\n"




