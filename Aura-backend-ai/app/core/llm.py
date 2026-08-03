from langchain_community.cross_encoders import HuggingFaceCrossEncoder
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_openai import ChatOpenAI

from app.core.config import settings

#=======================RAG 相关模型===========================
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

#=========================用户交互 LLM===============================
chat_llm = ChatOpenAI(
    model=settings.CHAT_MODEL_NAME,
    temperature=settings.CHAT_MODEL_TEMPERATURE,
    base_url=settings.CHAT_MODEL_BASE_URL,
    api_key=settings.CHAT_MODEL_API_KEY,
    model_kwargs={
        "thinking": {"type": "disabled"}  #考虑到兼容问题，关闭深度思考
    }
)