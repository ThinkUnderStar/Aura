import re
from typing import List

from langchain_classic.embeddings import CacheBackedEmbeddings
from langchain_classic.storage import LocalFileStore
from langchain_core.documents import Document

from app.core.config import settings
#缓存设置
from app.core.llm import embedding_llm

embedding_cache = CacheBackedEmbeddings.from_bytes_store(
        underlying_embeddings=embedding_llm,
        document_embedding_cache=LocalFileStore("./embedding_cache.db"),
        namespace=re.sub(r'[^a-zA-Z0-9_-]', '_', settings.EMBEDDING_MODEL)
    )

async def embedding_docs(docs: List[Document]):
    """
    对文档进行embedding
    :param docs: 被embedding的文档
    :return: embedding结果
    """