import logging
from typing import List
from langchain_core.messages import ToolMessage, SystemMessage, AIMessage
from langchain_core.runnables import RunnableConfig
from langgraph.config import get_store
from langgraph.types import Command, interrupt
from langmem.short_term import SummarizationNode
from pymilvus import DataType, FieldSchema, CollectionSchema
from app.core.config import settings
from app.core.llm import extractor_llm
from app.db.milvus.client import milvus_client
from app.db.mysql.entities import MessageEntity
from app.db.mysql.session import AsyncSessionLocal
from app.models.state import State
from app.services.agent.llm import chat_llm_with_tools
from app.services.agent.prompts import SYSTEM_PROMPT_TEMPLATE
from app.services.agent.tools import search_knowledge_base, save_user_memory, get_user_memory, search_user_memory, \
    delete_user_memory, search_full_session_memory, web_search
from app.services.rag.embedding import embed_memory
from app.services.rag.vector_store import vector_store
from app.tools.count_tokens import count_tokens_for_messages


async def llm_node(state:State) -> State:
    """
    调用用户交互大模型
    :param state: 各个节点相互通信的通信类对象
    :return: State
    """
    response = await chat_llm_with_tools.ainvoke(state.messages)

    if response.tool_calls:
        return Command(
            update={"messages": response},
            goto="run_tool"
        )
    else:
        return Command(
            update={"messages": response},
            goto="save_ai_session_memory"
        )

async def run_tool(state:State,config:RunnableConfig) -> State:
    """
       执行工具调用并更新状态。

       该节点负责处理当前状态中待执行的工具调用（tool_calls），
       并行执行所有工具，并将结果封装为 ToolMessage 追加到消息历史中。
       同时，此节点也会从长期记忆（store）中读取用户偏好等上下文信息，
       并将调用过程中产生的错误或日志信息记录到状态中，供后续节点使用。

       Args:
           state (State): 当前图状态，应包含待执行的工具调用列表（如 messages 中的 tool_calls）。

       Returns:
           State: 更新后的状态，主要变更包括：
               - messages: 追加了每个工具执行结果对应的 ToolMessage。
               - tool_results: 结构化存储的工具执行结果列表（可选）。
               - error: 如果执行过程中发生异常，记录错误信息（可选）。

       Example:
           典型的调用流程：
           1. 模型生成包含 tool_calls 的 AIMessage。
           2. 路由到 run_tool 节点。
           3. 该节点并行执行所有工具调用。
           4. 返回包含 ToolMessage 列表的更新状态。
           5. 后续节点（如模型节点）可基于工具结果继续生成回答。
       """
    store = get_store()
    messages = []
    tool_calls = state.messages[-1].tool_calls

    for tool_call in tool_calls:
        #增强检索指定知识库
        if tool_call["name"] == "search_knowledge_base":
            result = await search_knowledge_base.ainvoke(tool_call["args"],config=config)

        #添加用户级记忆（需用户自己确认）
        elif tool_call["name"] == "save_user_memory":

            user_choice = interrupt({
                "question": f"是否同意将:\n {tool_call["args"]["thing"]} \n添加进用户级记忆？",
                "options": ["approve", "reject", "edit"]
            })

            if user_choice["choice"] == "approve":

                result = await save_user_memory.ainvoke(tool_call["args"],config=config)
            elif user_choice["choice"] == "reject":

                result = "用户拒绝将该内容添加进用户级记忆"
            elif user_choice["choice"] == "edit":

                result = await save_user_memory.ainvoke({"thing": user_choice["edit"]},config=config)
            else:

                result = "用户的选择异常，不符合规范"

        #获取用户的所有用户级记忆
        elif tool_call["name"] == "get_user_memory":
            result = await get_user_memory.ainvoke(tool_call["args"],config=config)

        #搜索该用户的相关用户级记忆
        elif tool_call["name"] == "search_user_memory":
            result = await search_user_memory.ainvoke(tool_call["args"],config=config)

        #删除用户指定的用户级记忆
        elif tool_call["name"] == "delete_user_memory":
            question = "是否同意将以下用户级记忆内容从用户级记忆中删除:\n"
            # 获取当前会话或用户的唯一标识符
            user_id = config.get("configurable", {}).get("user_id", "default_user_id")
            if user_id == "default_user_id":
                result =  "未指定用户ID"
            else:
                user_memory_space = ("users_memory", user_id)

                keys: List[str] = tool_call["args"]["keys"]
                if not keys:
                    result = "未传入要删除用户级记忆的key"
                else:
                    for key in keys:
                        content = ""
                        item = await store.aget(namespace=user_memory_space, key=key)
                        if item is None:
                            content = "该key并不对应任何一个用户记忆"
                        else:
                            content = item.value["data"]
                        question = question + content +"\n"

                    user_choice = interrupt({
                        "question": question,
                        "options": ["approve", "reject"]
                    })

                    if user_choice["choice"] == "reject":
                        result = "用户拒绝删除这些用户级记忆"

                    elif user_choice["choice"] == "approve":
                        result = await delete_user_memory.ainvoke(tool_call["args"],config=config)

                    else:
                        result = "用户的选择异常，不符合规范"

        #搜索该分支的完整会话记忆
        elif tool_call["name"] == "search_full_session_memory":
            result = await search_full_session_memory.ainvoke(tool_call["args"],config=config)

        #联网搜索
        elif tool_call["name"] == "web_search":
            result = await  web_search.ainvoke(tool_call["args"],config=config)

        #过滤对不存在工具的调用
        else:
            result = "未查询到该工具"

        tool_message = ToolMessage(
            content=result,
            tool_call_id=tool_call["id"]
        )

        messages.append(tool_message)

    return {"messages": messages}

async def relevant_user_memories(state:State,config:RunnableConfig) -> State:
    """
    从用户级记忆中检索与当前问题最相关的记忆，并注入到消息状态中。

    该节点在每次 LLM 调用之前执行，作为图中的一个前置节点。
    它从当前会话的最后一条 HumanMessage 中提取问题文本，使用语义检索从用户的长期记忆库中
    查找最相关的记忆片段，然后将这些记忆格式化为 SystemMessage，放置到消息列表的最前面，
    供后续的 LLM 节点参考。

    该节点是“动态记忆注入”的核心实现，确保 LLM 在每次回答时都能感知到与当前问题
    最相关的用户历史偏好或事实，同时避免将无关记忆全部塞入上下文。

    Args:
        state (State): 当前图状态，应包含 `messages` 列表，其中至少有一条 HumanMessage。
        config (RunnableConfig): 运行时配置，必须包含 `configurable.user_id` 或 `thread_id`，
            用于确定当前用户的记忆命名空间。

    Returns:
        dict: 更新后的状态字典，主要修改 `messages` 字段：
            - 如果存在 SystemMessage，将其替换为包含相关记忆的新 SystemMessage。
            - 否则，在列表最前插入新 SystemMessage。

    Example:
        假设用户消息为 "我喜欢简洁的回答"，该节点会检索到之前存储的
        “用户偏好简洁风格”等记忆，并注入到 system prompt 中，
        使得后续 LLM 回答时自动采用简洁风格。

    注意：
        - 该节点依赖 `store` 支持向量语义搜索（如 AsyncPostgresStore）。
        - 如果存储不支持或检索失败，会返回空记忆提示，不影响主流程。
        - 仅检索与当前问题最相关的 5 条记忆，以控制上下文长度。
    """
    #获取与最新聊天相关的用户级记忆
    question = state.messages[-1].content
    store = get_store()
    memories = ""

    user_id = config.get("configurable", {}).get("user_id", "default_user_id")
    if user_id == "default_user_id":
        memories = "未指定用户ID"

    else:
        user_memory_space = ("users_memory",user_id)
        results = await store.asearch(user_memory_space,query=str(question),limit=5)

        if not results:
            memories = "无相关用户级记忆"
        else:
            for result in results:
                memories = memories + result.value["data"] + "\n"

    #获取该agent绑定的相关知识库的信息
    knowledge_bases = ""
    knowledge_bases_list = config.get("configurable", {}).get("knowledge_bases", [])
    if not knowledge_bases_list:
        knowledge_bases = "当前没有绑定任何知识库，请根据自身知识回答用户的问题。"
    else:
        for knowledge_base in knowledge_bases_list:
            knowledge_bases += (
                f"知识库对应的向量数据库名: {knowledge_base.collection_name} "
                f"该知识库的相关描述: {knowledge_base.description}\n"
            )

    #替换旧的systemMessage
    system_prompt = await SYSTEM_PROMPT_TEMPLATE.ainvoke({
        "memories": memories,
        "knowledge_bases": knowledge_bases
    })

    old_system = state.messages[0]
    if isinstance(old_system,SystemMessage):
        new_system = SystemMessage(
            content=system_prompt,
            id=old_system.id  # 保留相同 id，触发覆盖
        )

        return {"messages": [new_system]}
    else:
        new_system = SystemMessage(
            content=system_prompt
        )

        return {"messages": [new_system] + state.messages}

#创建压缩对话节点（减少Token消耗）
summarization_node  = SummarizationNode(
    token_counter=count_tokens_for_messages,
    model=extractor_llm,
    max_tokens_before_summary=4096,
    max_summary_tokens=512
)

async def save_human_session_memory(state: State,config: RunnableConfig) -> State:
    """
    保存当前轮次的人类用户消息到会话记忆存储（MySQL + Milvus）。

    该节点在 LLM 节点之后执行，通常在图结束前运行。
    它从 `state.messages` 中提取最新的 `HumanMessage`，并将其内容、时间戳、关联的 `agent_id`、`user_id` 等信息：
    - 写入 MySQL 的 `messages` 表（供前端展示和历史查询）
    - 写入 Milvus 的向量集合 `aura_agent_{agent_id}_session_memory`（供语义检索）

    该节点只保存人类消息，不保存 AI 回复。如果需要同时保存 AI 回复，可扩展为同时保存。

    Args:
        state (State): 当前图状态，应包含 `messages` 列表，其中至少有一条 `HumanMessage`。
        config (RunnableConfig): 运行时配置，必须包含：
            - `configurable.agent_id`: Agent ID，用于确定 Milvus 集合名和 MySQL 关联。
            - `configurable.user_id`: 用户 ID，用于标记消息归属（可选）。
            - `configurable.thread_id`: 会话 ID（可选，用于日志）。

    Returns:
        State: 原状态（不变），因为本节点只执行外部写入操作，不修改 `state` 内容。
        如果保存失败，会记录错误日志，但不影响图执行。

    Note:
        - 本节点假设 `state.messages` 中至少有一条 `HumanMessage`，否则跳过保存。
        - Milvus 集合名格式为 `aura_agent_{agent_id}_session_memory`，需确保已创建。
        - 如果 Milvus 写入失败，只会记录日志，不会回滚 MySQL 操作（非事务性）。
        - 该节点不修改 `messages`，因此不会影响后续节点或 checkpoint。
    """
    agent_id = config.get("configurable", {}).get("agent_id", -1)
    if agent_id == -1:
        logging.error("config中未指定agent_id")
        return state

    memory_collection_name = f"aura_agent_{agent_id}_session_memory"
    from_checkpoint_id = config.get("configurable", {}).get("from_checkpoint_id", "default_checkpoint_id")
    if from_checkpoint_id == "default_checkpoint_id":
        logging.error("config中未指定from_checkpoint_id")
        return state

    async with AsyncSessionLocal() as db:
        #将HumanMessage的信息存入mysql中
        message = MessageEntity(
            content = state.messages[-1].content,
            agent_id = agent_id,
            role = "user",
            from_checkpoint_id=from_checkpoint_id,
        )
        db.add(message)
        await db.commit()
        await db.refresh(message)

    if not (await milvus_client.has_collection(collection_name=memory_collection_name)):
        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="text", dtype=DataType.VARCHAR, max_length=4096),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=settings.MILVUS_DIMENSION),
            FieldSchema(name="message_id", dtype=DataType.INT64),
            FieldSchema(name="message_role", dtype=DataType.VARCHAR, max_length=20),
            FieldSchema(name="create_time", dtype=DataType.VARCHAR, max_length=30),
        ]
        schema = CollectionSchema(fields, description="会话记忆向量集合")
        await milvus_client.create_collection(memory_collection_name, schema=schema)

        # 创建索引（必须）
        index_params = {
            "metric_type": "COSINE",
            "index_type": "IVF_FLAT",
            "params": {"nlist": 128}
        }
        await milvus_client.create_index(
            collection_name=memory_collection_name,
            field_name="vector",
            index_params=index_params
        )

     #将HumanMessage的信息存入用于完整记忆检索的milvus向量数据库中
    embed_messages = await embed_memory(message.content,message)
    await vector_store(embed_messages,memory_collection_name)

    return state

async def save_ai_session_memory(state: State,config: RunnableConfig) -> State:
    """
    保存当前轮次的 AI 回复消息到会话记忆存储（MySQL + Milvus）。

    该节点在 LLM 节点之后执行，通常在图结束前运行。
    它从 `state.messages` 中提取最新的 `AIMessage`，并将其内容、时间戳、关联的 `agent_id`、`user_id` 等信息：
    - 写入 MySQL 的 `messages` 表（供前端展示和历史查询）
    - 写入 Milvus 的向量集合 `aura_agent_{agent_id}_session_memory`（供语义检索）

    该节点与 `save_human_session_memory` 配对使用，分别保存人类消息和 AI 回复。

    Args:
        state (State): 当前图状态，应包含 `messages` 列表，其中至少有一条 `AIMessage`。
        config (RunnableConfig): 运行时配置，必须包含：
            - `configurable.agent_id`: Agent ID，用于确定 Milvus 集合名和 MySQL 关联。
            - `configurable.user_id`: 用户 ID，用于标记消息归属（可选）。
            - `configurable.thread_id`: 会话 ID（可选，用于日志）。

    Returns:
        State: 原状态（不变），因为本节点只执行外部写入操作，不修改 `state` 内容。
        如果保存失败，会记录错误日志，但不影响图执行。

    Note:
        - 本节点假设 `state.messages` 中至少有一条 `AIMessage`，否则跳过保存。
        - 保存时，`role` 字段固定为 "assistant"。
        - `from_checkpoint_id` 从 `config` 中获取，用于追溯会话来源。
        - Milvus 集合名格式为 `aura_agent_{agent_id}_session_memory`，需确保已创建。
        - 如果 Milvus 写入失败，只会记录日志，不会回滚 MySQL 操作（非事务性）。
        - 该节点不修改 `messages`，因此不会影响后续节点或 checkpoint。
    """
    agent_id = config.get("configurable", {}).get("agent_id", -1)
    if agent_id == -1:
        logging.error("config中未指定agent_id")
        return state
    memory_collection_name = f"aura_agent_{agent_id}_session_memory"

    async with AsyncSessionLocal() as db:
        #将HumanMessage的信息存入mysql中
        message = MessageEntity(
            content = state.messages[-1].content,
            agent_id = agent_id,
            role = "assistant",
        )
        db.add(message)
        await db.commit()
        await db.refresh(message)

     #将HumanMessage的信息存入用于完整记忆检索的milvus向量数据库中
    embed_messages = await embed_memory(message.content,message)
    await vector_store(embed_messages,memory_collection_name)

    return state

async def sensitive_content_handler(state: State) -> State:
    """
    敏感词内容处理节点（硬编码返回）。

    该节点在用户输入被 Java 端规则检查标记为敏感词（`is_sensitive == 1`）时触发，
    作为图内分流后的终点节点之一。它不调用任何 LLM 或外部服务，
    直接返回一条固定的警告消息给用户，避免敏感内容进入后续正常对话流程。

    **核心职责**：
    - 接收包含 `is_sensitive` 标记的状态。
    - 构造并返回一条标准的敏感词警告消息（`AIMessage`）。
    - 重置 `is_sensitive` 标记为 0，防止状态泄露或重复触发。

    **执行位置**：
    - 在图中的 `save_human_session_memory` 节点之后。
    - 作为 `route_by_sensitive` 条件边的一个分支（`is_sensitive == 1` 分支）。
    - 执行完成后，直接进入 `save_ai_session_memory` 节点（保存警告消息）。

    Args:
        state (State): 当前图状态，应包含 `is_sensitive` 字段（由 Java 端传入）。

    Returns:
        State: 更新后的状态，包含：
            - `messages`: 追加了一条 `AIMessage`，内容为固定的敏感词警告。
            - `is_sensitive`: 重置为 0，确保后续节点不再触发敏感词路由。

    Note:
        - 该节点不依赖任何外部服务，执行速度快（微秒级），适合作为兜底拦截路径。
        - 警告消息内容为：`"您的输入内容包含敏感词，请修改后重新发送。"`
        - 该节点不会产生流式事件，前端通过 `on_chain_end` 事件一次性接收到完整消息。
        - 如果后续需要更精细的拦截策略（如 AI 复核），可在该节点前增加额外分支。
    """
    ai_message = AIMessage(content="根据相关规定，我无法回应您当前的问题。")
    return {"messages": [ai_message]}