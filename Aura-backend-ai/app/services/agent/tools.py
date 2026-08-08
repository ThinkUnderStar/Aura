import asyncio
from datetime import datetime
from typing import List

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool
from langgraph.config import get_store
from sqlalchemy import select

from app.core.tavily import tavily_client
from app.db.mysql.entities import MessageEntity
from app.db.mysql.session import AsyncSessionLocal
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
    在指定 Agent 的完整会话历史中搜索与关键词匹配的消息内容（后备检索）。

    **重要提示**：
    - 该工具基于关键词进行模糊匹配（`LIKE %...%`），因此 `query` 应包含用户可能说过的**确切词语或短语**，
      而非自然语言问题。例如：应传入 "部署" 或 "报错 500"，而不是 "用户之前问过什么关于部署的问题？"。
    - **该操作涉及数据库模糊查询（全表扫描），相对耗时**，且返回的大量历史消息会消耗较多 Token。
    - **建议仅在当前对话上下文无法回答用户问题时调用**，作为“后备检索”手段。
    - 如果用户的记忆已被压缩（`SummarizationNode` 处理过），可能无法检索到已压缩的完整原始内容，
      仅能检索到当前仍保存在 `messages` 表中的消息。

    **过滤条件**：
    - 仅查询角色为 `human` 或 `assistant` 的消息。
    - 按 `agent_id` 进行过滤（从 `config.configurable.agent_id` 中获取），确保只搜索该 Agent 相关的对话。
    - **注意**：当前未按会话（`thread_id`）过滤，因此搜索结果可能包含该 Agent 下所有会话的历史消息。
      如果未来需要限制在特定会话内，可调整查询条件。

    Args:
        query (str): 搜索关键词或短语，例如 "部署"、"报错"、"配置"。
        config (RunnableConfig): LangGraph 自动注入的运行时配置，用于获取当前 Agent 的 `agent_id`。

    Returns:
        str: 匹配的消息内容列表，按时间倒序排列，最多 20 条。
             如果没有匹配结果，返回 "未查询到任何相关记忆"。

    注意：
        - 仅返回 `content` 字段，不包含角色、时间等元数据。
        - 模糊匹配基于 MySQL 的 `LIKE`，区分中英文但大小写不敏感（取决于数据库排序规则）。
        - 返回结果可能不包含已压缩的摘要内容，因此建议在对话早期或未压缩前使用。
    """
    try:
        async with AsyncSessionLocal() as db:
            #所属agent
            agent_id = config.get("configurable",{}).get("agent_id",-1)
            if agent_id == -1:
                return "未指定agent_id"

            branch_path: str = config.get("configurable", {}).get("branch_path", "main")
            one_list = branch_path.split("/")
            branch = ""
            branch_list = []
            for one in one_list:
                if one == "main":
                    branch = one
                else:
                    branch = branch + "/" + one
                branch_list.append(branch)

            stmt = select(MessageEntity.content).where(
                MessageEntity.content.like(f"%{query}%"),
                MessageEntity.role.in_(['human', 'assistant']),
                MessageEntity.branch_path.in_(branch_list),
                MessageEntity.agent_id == agent_id
            ).order_by(MessageEntity.create_time.desc()).limit(20)

            result = await db.execute(stmt)

            rows = result.mappings().fetchall()
            if not rows:
                return "未查询到任何相关记忆"
            else:
                memories = ""
                for row in rows:
                    memories = memories + row["content"] +"\n"

                return memories

    except Exception as e:
        return "查询该会话的完整记忆工具调用异常"

@tool(name_or_callable="web_search")
async def web_search(query: str) -> str:
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

    Returns:
        str: 格式化后的搜索结果字符串，每条结果包含标题、摘要和来源 URL。
             如果没有找到结果，返回 "未找到相关搜索结果。"
             如果搜索过程中发生异常，返回 "搜索失败：{具体错误信息}。"
    """
    try:
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
