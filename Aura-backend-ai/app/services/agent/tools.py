import asyncio
from typing import List

from langchain_core.tools import tool

from app.services.rag.rag_flow import rag_ask
from app.tools.to_str import list_document_to_str


@tool
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
