from langchain_community.embeddings import OllamaEmbeddings

from app.core.config import settings

#创建嵌入模型
embedding_llm = OllamaEmbeddings(model=settings.EMBEDDING_MODEL)