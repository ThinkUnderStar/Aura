import asyncio
from datetime import datetime
from typing import List

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool
from langgraph.config import get_store
import logging
from app.core.tavily import tavily_client
from app.db.milvus.client import milvus_client
from app.services.rag.rag_flow import rag_ask
from app.tools.to_str import list_document_to_str, format_tavily_response

@tool(name_or_callable="search_knowledge_base")
async def search_knowledge_base(question: str, kbs_name: List[str]) -> str:
    """
        在指定的知识库中并行搜索问题答案，返回格式化后的检索结果。

        该工具会同时查询多个知识库，收集所有相关的文档片段，
        并将结果整理为包含元数据和正文内容的可读字符串，供后续处理或生成回答。

        Args:
            question (str): 用户提出的问题，用于在知识库中进行语义检索。
            kbs_name (List[str]): 需要检索的知识库名称列表，

        Returns:
            str: 格式化后的检索结果字符串，包含每个文档的元数据（如来源、文件名等）
                 和正文内容。如果未指定知识库或所有查询均失败，返回相应的提示信息。
    """
    task_list = []
    result_list = []

    if not kbs_name:
        return "⚠️ 未指定任何知识库名称。"

    try:
        # 创建异步任务集合
        for kb_name in kbs_name:
            task_list.append(rag_ask(question, kb_name))

        # 异步并行执行异步任务
        results = await asyncio.gather(*task_list)

        # 并入同一个List[Document]
        for result in results:
            result_list.extend(result)

        # 拼接返回给llm的字符串
        result_str = list_document_to_str(result_list)

        return result_str

    except Exception as e:
        return "知识库查询异常"

@tool(name_or_callable="save_user_memory")
async def save_user_memory(
        thing:str,
        config:RunnableConfig
) -> str:
    """
        保存用户的长期记忆（偏好、事实、习惯等），**此记忆为“用户级”记忆**，
        一经存储，将对**该用户创建的所有 Agent** 生效，并跨会话持久化。

        **重要**：仅在用户**明确表达**希望保存为“用户级记忆”时调用此工具。
        例如用户说“记住我的名字叫张三，对所有 Agent 都有效”、“保存这个偏好到我的个人资料”等。
        对于仅在当前对话中有效、或用户未明确要求永久记住的信息，**请勿调用此工具**。

        该工具存储的数据可在后续任意会话中被搜索和利用。

        Args:
            thing (str): 用户希望永久记住的内容，如“我的名字是张三”、“我喜欢简洁的回答”等。
            config (RunnableConfig): LangGraph 自动注入的运行时配置，通常包含 user_id，
                用于区分不同用户。该参数由框架自动传入，调用时无需显式提供。

        Returns:
            str: 保存成功的确认信息，例如 "✅ 已成功记忆：{thing}"。
    """
    try:
        # 获取当前会话或用户的唯一标识符
        user_id = config.get("configurable",{}).get("user_id","default_user_id")
        if user_id == "default_user_id":
            return "未指定用户ID"

        user_memory_space = ("users_memory",user_id)

        store = get_store()
        # 将信息存储到用户的长期记忆空间中
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        await store.aput(user_memory_space,timestamp,{"data": thing})

        return "存储成功"
    except Exception as e:
        return "存储失败"

@tool(name_or_callable="get_user_memory")
async def get_user_memory(
        config:RunnableConfig
) -> str:
    """
    获取当前用户已保存的全部用户级记忆（列表形式）。

    该工具用于当用户明确询问“你记得我哪些信息？”、“我有哪些记忆？”、“你记住我什么了？”时，
    将用户的所有记忆以可读的列表形式返回给用户，帮助用户了解当前存储了哪些个人信息。

    该工具获取的是用户级记忆的**全部内容**，而非仅与当前问题相关的部分。
    如果记忆数量较多，返回的内容可能会较长，建议仅在用户明确需要查看全部记忆时使用。

    Args:
        config (RunnableConfig): LangGraph 自动注入的运行时配置，包含 `configurable.user_id`，
            用于确定当前用户的记忆命名空间。该参数由框架自动传入，调用时无需显式提供。

    Returns:
        str: 格式化后的记忆列表，每条记忆占一行，例如：
            - 你喜欢简洁的回答
            - 你的名字是张三
            如果没有记忆，返回“当前用户暂无任何记忆。”

    注意：
        - 该工具返回的字符串包含所有记忆，可能较长。
        - 该工具不会触发中断确认，直接返回结果。
        - 当前实现最多返回 1000 条记忆（limit=1000），如果用户记忆超过此数量，请调整 limit 参数。
    """
    try:
        store = get_store()
        memories = ""

        # 获取当前会话或用户的唯一标识符
        user_id = config.get("configurable", {}).get("user_id", "default_user_id")
        if user_id == "default_user_id":
            return "未指定用户ID"

        user_memory_space = ("users_memory", user_id)

        results = await store.asearch(user_memory_space,limit=1000)

        if not results:
            memories = "用户还未添加任何用户级记忆"
        else:
            for result in results:
                memories = memories+ "key: "+ result.key + " content: " + result.value["data"] + "\n"

        return memories

    except Exception as e:
        return "获取该用户的用户级记忆异常"

@tool(name_or_callable="search_user_memory")
async def search_user_memory(
        query:str,
        config:RunnableConfig
) -> str:
    """
    根据关键词或问题，搜索当前用户的相关用户级记忆。

    该工具用于当用户询问与之前存储的信息相关的问题时（例如“我之前说过我喜欢什么风格？”），
    从用户的全部记忆中检索与 `query` 语义或关键词最匹配的记忆片段。

    该工具执行的是**基于关键词的全文搜索**，返回与 `query` 最相关的记忆（按相关性排序）。
    注意：当前的存储后端（如 InMemoryStore 或 AsyncPostgresStore）默认使用关键词匹配，
    而非向量语义检索。如果需要语义检索，需配置向量索引。

    Args:
        query (str): 搜索关键词或问题，例如“用户偏好的回答风格”、“用户的名字”等。
        config (RunnableConfig): LangGraph 自动注入的运行时配置，包含 `configurable.user_id`，
            用于确定当前用户的记忆命名空间。该参数由框架自动传入，调用时无需显式提供。

    Returns:
        str: 格式化后的匹配记忆列表，每条记忆占一行，例如：
            - 你喜欢简洁的回答
            - 你的名字是张三
            如果没有匹配结果，返回“未查询到关于该问题的任何用户级记忆”。

    注意：
        - 该工具最多返回 10 条最相关的记忆（由 limit=10 控制），如需更多可调整。
        - 该工具不会触发中断确认，直接返回结果。
        - 搜索基于关键词匹配，而非语义理解，请确保 `query` 包含足够的关键词。
    """
    try:
        store = get_store()
        memories = ""

        # 获取当前会话或用户的唯一标识符
        user_id = config.get("configurable", {}).get("user_id", "default_user_id")
        if user_id == "default_user_id":
            return "未指定用户ID"

        user_memory_space = ("users_memory", user_id)

        results = await store.asearch(user_memory_space, query=query ,limit=10)

        if not results:
            memories = "未查询到关于该问题的任何用户级记忆"
        else:
            for result in results:
                memories = memories+ "key: "+ result.key + " content: " + result.value["data"] + "\n"

        return memories

    except Exception as e:
        return "获取用户用户及记忆异常"

@tool(name_or_callable="delete_user_memory")
async def delete_user_memory(
        keys:list[str],
        config:RunnableConfig
) -> str:
    """
    根据指定的 key 列表，批量删除用户级记忆。

    该工具用于用户明确知道要删除哪些记忆时（例如用户说“删除我刚才提到的偏好”），
    直接通过 key 精确定位并删除对应的记忆条目。
    通常与 `get_user_memory` 或 `search_user_memory` 配合使用，
    先获取记忆及其 key，再由用户选择删除。

    Args:
        keys (List[str]): 要删除的记忆 key 列表（时间戳字符串）。
            例如：["20250101_120000", "20250102_143000"]。
            用户可通过 `get_user_memory` 或 `search_user_memory` 获取所有记忆及其 key。
        config (RunnableConfig): LangGraph 自动注入的运行时配置，包含 `configurable.user_id`，
            用于确定当前用户的记忆命名空间。该参数由框架自动传入，调用时无需显式提供。

    Returns:
        str: 删除结果的汇总信息，包括成功删除的数量、失败的 key 及原因（若有）。

    注意：
        - 该工具直接执行删除操作，不会触发中断确认。如需确认，请在调用前由上层节点处理。
        - 如果某个 key 不存在，会记录失败信息但不会中断整个删除流程。
        - 删除操作不可恢复，请谨慎使用。
    """
    try:
        store = get_store()

        # 获取当前会话或用户的唯一标识符
        user_id = config.get("configurable", {}).get("user_id", "default_user_id")
        if user_id == "default_user_id":
            return "未指定用户ID"

        user_memory_space = ("users_memory", user_id)

        if not keys:
            return "未指定删除哪些记忆"

        for key in keys:
            await store.adelete(user_memory_space,key)

        return "删除成功"

    except Exception as e:
        return "删除用户级记忆失败"

@tool(name_or_callable="search_full_session_memory")
async def search_full_session_memory(
        query: str,
        config: RunnableConfig
) -> str:
    """
        在 Agent 的向量记忆库中语义搜索历史对话内容（后备检索）。

        该工具将 Agent 的完整会话历史存储在 Milvus 向量库中，通过语义相似度检索
        查找与用户问题最相关的历史对话片段。相比 MySQL 的 `LIKE` 模糊匹配，
        向量检索能理解语义相近的表达，召回率更高。

        **核心机制**：
        - 每个 Agent 拥有独立的 Milvus Collection：`aura_agent_{agent_id}_session_memory`
        - 检索基于向量相似度（Cosine/Inner Product），而非关键词匹配
        - 返回与 `query` 语义最相关的历史消息片段

        **使用场景**：
        - 用户询问“我之前有没有问过类似的问题？”
        - 当前对话上下文无法回答，需要从历史对话中寻找线索
        - 需要检索已压缩或被截断的历史记忆

        **过滤条件**：
        - 按 `agent_id` 隔离，只搜索该 Agent 自己的历史记忆
        - 如果该 Agent 尚未创建向量记忆库，返回提示信息

        Args:
            query (str): 搜索关键词或自然语言问题，例如 "部署"、"报错 500"、
                "我之前问过什么关于配置的问题？"
            config (RunnableConfig): LangGraph 自动注入的运行时配置，
                需要包含 `agent_id`。

        Returns:
            str: 格式化的检索结果，按相关性排序，包含元数据和正文内容。
                 如果该 Agent 没有向量记忆库，返回 "该agent并没有向量会话记忆库"。
                 如果查询失败，返回空结果。

        Note:
            - 该工具依赖 Milvus 向量数据库，需确保 `milvus_client` 已正确初始化。
            - Collection 命名规范为 `aura_agent_{agent_id}_session_memory`。
            - 与 `search_user_memory` 的区别：
                - `search_user_memory`：搜索用户级记忆（Store），跨 Agent 共享
                - `search_full_session_memory`：搜索当前 Agent 的会话历史（Milvus）
            - 建议作为后备检索手段，优先使用当前对话上下文。
            - 如果 `agent_id` 未传入，返回错误提示。
        """
    try:
        agent_id = config.get("configurable", {}).get("agent_id", -1)
        if agent_id == -1:
            return "未传入agent_id"

        memory_collection_name = f"aura_agent_{agent_id}_session_memory"
        if not milvus_client.has_collection(memory_collection_name):
            logging.error(f"agent_{agent_id} 不存在向量记忆库")
            return "该agent并没有向量会话记忆库"

        results = await rag_ask(question=query,collection_name=memory_collection_name)
        return list_document_to_str(results)

    except Exception as e:
        return "搜索该agent的全部会话记忆工具调用异常"

@tool(name_or_callable="web_search")
async def web_search(
        query: str,
        config: RunnableConfig
) -> str:
    """
    通过 Tavily 进行实时网络搜索，获取最新、准确的信息。

    该工具专为 AI Agent 设计，返回结果经过提炼，包含标题、摘要和来源链接。
    适用于需要获取实时新闻、最新事件、动态信息、外部知识或验证事实的场景。
    当用户询问时效性强、超出模型知识截止日期或需要外部验证的问题时，
    应优先考虑调用此工具。

    Args:
        query (str): 搜索关键词或问题。
            应使用**简洁、核心的关键词**，而非冗长的自然语言描述。
            例如：应传入 "LangGraph 2026 最新特性"，而不是 "你能告诉我 LangGraph 在 2026 年有哪些新功能吗？"。
        config (RunnableConfig): LangGraph 自动注入的运行时配置，
                需要包含 `enable_web_search`。

    Returns:
        str: 格式化后的搜索结果字符串，每条结果包含标题、摘要和来源 URL。
             如果没有找到结果，返回 "未找到相关搜索结果。"
             如果搜索过程中发生异常，返回 "搜索失败：{具体错误信息}。"
    """
    try:
        enable_web_search = config.get("configurable", {}).get("enable_web_search", 0)
        if enable_web_search == 0:
            return "用户未开启联网搜索功能"

        response = await tavily_client.search(
            query=query,
            search_depth="advanced",
            max_results=5,
            include_answer=True,
            topic="general"
        )
        response_str = format_tavily_response(response)

        return response_str

    except Exception as e:
        return "联网搜索工具异常"
