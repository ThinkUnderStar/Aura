import json
from typing import List

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