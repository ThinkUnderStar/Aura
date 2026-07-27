from langchain_community.embeddings import OllamaEmbeddings

from app.core.config import settings

embedding_llm = OllamaEmbeddings(model=settings.EMBEDDING_MODEL)