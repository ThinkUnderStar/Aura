from langchain_community.cross_encoders import HuggingFaceCrossEncoder
from langchain_ollama import ChatOllama, OllamaEmbeddings

from app.core.config import settings

#创建嵌入模型
embedding_llm = OllamaEmbeddings(model=settings.EMBEDDING_MODEL)

#初始化一个本地 LLM (用于生成查询变体)
multi_query_llm = ChatOllama(
    model=settings.MULTI_QUERY_MODEL,
    temperature=0.4
)

#创建摘要模型
extractor_llm = ChatOllama(
    model=settings.EXTRACTOR_MODEL,
    temperature=0.1
)

#创建精排序模型
reranker_llm = HuggingFaceCrossEncoder(
    model_name=settings.RERANKER_MODEL,
    model_kwargs={"device": "cuda"}
)