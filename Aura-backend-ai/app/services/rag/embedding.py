import json
import re
from typing import List, Dict

from langchain_classic.embeddings import CacheBackedEmbeddings
from langchain_classic.storage import LocalFileStore
from langchain_core.documents import Document

from app.core.config import settings
#缓存设置
from app.core.llm import embedding_llm
from app.db.mysql.entities import DocumentEntity, MessageEntity

embedding_cache = CacheBackedEmbeddings.from_bytes_store(
        underlying_embeddings=embedding_llm,
        document_embedding_cache=LocalFileStore("./embedding_cache.db"),
        namespace=re.sub(r'[^a-zA-Z0-9_-]', '_', settings.EMBEDDING_MODEL)
    )

async def embedding_docs(docs: List[Document],document: DocumentEntity) -> List[dict]:
    """
    对文档进行embedding
    :param document: 文件对象
    :param docs: 被embedding的文档
    :return: embedding结果
    """

    docs_embed = await embedding_cache.aembed_documents([doc.page_content for doc in docs])

    #封装最后的结果
    embed_documents = []
    for i, e in enumerate(docs_embed):
        embed_documents.append(
            {
                "text": docs[i].page_content,
                "vector": e,
                "document_id": document.id,
                "document_name": document.file_name,
                "metadata": json.dumps(docs[i].metadata)
            }
        )
        
    return embed_documents

async def embed_memory(content: str,message: MessageEntity) -> List[dict]:
    """
    将单条会话记忆内容向量化（整体嵌入，不分块）。

    利用已配置的 `CacheBackedEmbeddings` 进行嵌入，相同内容会命中缓存，避免重复计算。
    当前实现将整个 `content` 作为一个整体进行嵌入，不对文本进行分块。

    Args:
        content (str): 待向量化的文本内容，通常为组合后的对话内容（如 "Human: ...\nAI: ..."）。
        message (MessageEntity): 对应的消息实体对象，用于提取 `id`、`role`、`create_time` 等元数据。

    Returns:
        List[dict]: 嵌入结果列表，每个元素为字典，包含以下字段：
            - "text" (str): 原始文本内容
            - "vector" (List[float]): 向量表示
            - "message_id" (int): 消息ID
            - "message_role" (str): 消息角色（如 "human"）
            - "create_time" (datetime): 消息创建时间

    Note:
        - 本函数不进行文本分块，适用于较短的对话记忆（通常 < 1000 字）。
        - 如需分块嵌入超长内容，可参考 `embedding_docs` 函数，使用 `RecursiveCharacterTextSplitter` 进行分割。
        - 返回的字典列表可直接用于 Milvus 插入操作。
    """
    documents = [Document(page_content=content)]

    message_embed = await embedding_cache.aembed_documents(
        [split_document.page_content for split_document in documents]
    )

    # 封装最后的结果
    embed_messages = []
    for i, e in enumerate(message_embed):
        embed_messages.append(
            {
                "text": documents[i].page_content,
                "vector": e,
                "message_id": message.id,
                "message_role": message.role,
                "create_time": message.create_time
            }
        )

    return embed_messages
