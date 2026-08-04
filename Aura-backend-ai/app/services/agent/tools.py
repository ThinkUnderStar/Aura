import asyncio
from datetime import datetime
from typing import List

from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool
from langgraph.config import get_store

from app.services.rag.rag_flow import rag_ask
from app.tools.to_str import list_document_to_str


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
    保存用户的长期记忆（偏好、事实、习惯等）。
    该工具用于将用户提供的信息持久化存储到当前会话或用户的长期记忆空间中。
    存储的数据可在后续对话中被 `search_user_memory` 或 `manage_user_memory` 等工具检索和使用。
    Args:
        thing (str): 用户希望记住的内容，如“我喜欢简洁的回答”、“我的名字是张三”等。
        config (RunnableConfig): LangGraph 自动注入的运行时配置，通常包含 thread_id 或 user_id，
            用于区分不同用户或会话。该参数由框架自动传入，调用时无需显式提供。

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
