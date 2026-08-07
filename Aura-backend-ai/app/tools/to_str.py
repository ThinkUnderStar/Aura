import json
from typing import List, Dict, Any

from langchain_core.documents import Document


def list_document_to_str(docs: List[Document]):
    """
    将 Document 列表转为字符串，自动展开所有元数据（包括嵌套）。
    """
    if not docs:
        return ""

    parts = []
    for idx, doc in enumerate(docs, 1):
        # 将整个 metadata 字典转为美观的 JSON 字符串（缩进2空格）
        meta_str = json.dumps(doc.metadata, ensure_ascii=False, indent=2)
        # 构建内容
        content = f"【检索结果 #{idx}】（注意：编号仅为检索结果的序号，不代表文档名称或标题）\n" \
                  f"元数据:\n{meta_str}\n\n" \
                  f"正文:\n{doc.page_content}"        
        parts.append(content)

    return "\n\n---\n\n".join(parts)

def format_tavily_response(response: Dict[str, Any]) -> str:
    """
    将 Tavily 搜索 API 返回的结果对象转换为格式化的字符串。

    该函数提取 Tavily 响应中的直接答案、搜索结果列表和图片信息，
    并将其组织为结构化的文本，便于 LLM 或用户阅读。

    Args:
        response (Dict[str, Any]): Tavily 的 `search()` 方法返回的原始响应字典。
            期望包含以下键：
            - "answer" (str, 可选): 直接生成的答案。
            - "results" (List[Dict]): 搜索结果列表，每项含 "title", "content", "url" 等。
            - "images" (List[Dict], 可选): 图片信息列表。

    Returns:
        str: 格式化后的字符串，包含答案、搜索结果和图片（如有）。
            如果没有结果，返回 "未找到相关搜索结果。"
    """
    output_parts = []

    # 1. 处理直接答案（如果有）
    answer = response.get("answer")
    if answer:
        output_parts.append(f"📌 直接答案：{answer}\n")

    # 2. 处理搜索结果
    results = response.get("results", [])
    if results:
        output_parts.append("📋 搜索结果：")
        for i, item in enumerate(results, 1):
            title = item.get("title", "无标题")
            content = item.get("content", "无摘要")
            url = item.get("url", "")
            published_date = item.get("published_date", "")
            # 如果有发布日期，添加到描述中
            date_str = f"（发布于 {published_date}）" if published_date else ""
            output_parts.append(
                f"{i}. {title} {date_str}\n"
                f"   {content}\n"
                f"   链接：{url}\n"
            )
    else:
        # 如果没有结果，提示未找到
        if not answer:  # 如果连答案也没有，才返回未找到
            return "未找到相关搜索结果。"

    # 3. 处理图片信息（如果有）
    images = response.get("images", [])
    if images:
        output_parts.append("🖼️ 相关图片：")
        for i, img in enumerate(images[:3], 1):  # 最多展示3张
            img_url = img.get("url", "")
            img_description = img.get("description", "")
            output_parts.append(f"{i}. {img_description}（{img_url}）")

    return "\n".join(output_parts)