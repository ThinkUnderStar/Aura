from typing import List
from langchain_classic.retrievers import MultiQueryRetriever, ContextualCompressionRetriever
from langchain_classic.retrievers.document_compressors import CrossEncoderReranker, LLMChainExtractor, \
    DocumentCompressorPipeline
from langchain_community.document_compressors import LLMLinguaCompressor
from langchain_community.vectorstores.milvus import Milvus
from langchain_core.documents import Document

from app.core.config import settings
from app.core.llm import multi_query_llm, reranker_llm, extractor_llm, embedding_llm
from app.db.milvus.client import milvus_client

#全局只加载一次
# LLMLingua 压缩器
lingua_compressor = LLMLinguaCompressor(
    model_name=settings.LING_GUA_MODEL,
    device_map="cuda"
)

# Reranker 压缩器
reranker = CrossEncoderReranker(
    model=reranker_llm,
    top_n=5
)

# LLMChainExtractor 压缩器
extractor = LLMChainExtractor.from_llm(llm=extractor_llm)

# 构建压缩管道
pipline = DocumentCompressorPipeline(
    transformers=[
        reranker,  # 按相关性从大到小精排序
        lingua_compressor,  # token 缩减
        extractor  # 摘要
    ]
)

async def rag_ask(question: str, collection_name: str) -> List[Document]:
    """
    单个向量数据库rag检索增强流程实现
    :param question: 用户问的问题
    :param collection_name: milvus collection name
    :return: 从知识库查询的结果
    """
    #将该集合加载到内存中
    await milvus_client.load_collection(collection_name)

    vector_store = Milvus(
        collection_name=collection_name,
        embedding_function=embedding_llm,
        connection_args={
            "uri": settings.MILVUS_URI,
            "token": settings.MILVUS_TOKEN,
            "db_name": settings.MILVUS_DB_NAME
        },
        text_field="text",
        vector_field="vector"
    )

    # 问题解构查询
    multi_query_retriever = MultiQueryRetriever.from_llm(
        retriever=vector_store.as_retriever(
            search_kwargs={
                "k": 20
            }
        ),
        llm=multi_query_llm
    )

    #构建完整的检索器
    retriever = ContextualCompressionRetriever(
        base_retriever=multi_query_retriever,
        base_compressor=pipline
    )

    search_result =  await retriever.ainvoke(question)

    await milvus_client.release_collection(collection_name)

    return search_result


