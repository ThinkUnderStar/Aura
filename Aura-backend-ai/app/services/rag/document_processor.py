from typing import List

from langchain_community.document_loaders import (
    PyPDFLoader,
    Docx2txtLoader,
    TextLoader,
    UnstructuredMarkdownLoader,
)
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.core.config import settings


async def document_processor(file_path: str) -> List[Document]:
    """
    文档处理器，根据文件类型加载文档并进行切割
    :param file_path: 文件路径
    :return: 切割后的文档列表
    """
    #根据文件扩展名加载不同类型的文档
    ext = file_path.split(".")[-1]

    if ext == "pdf":
        loader = PyPDFLoader(file_path)
    elif ext == "docx":
        loader = Docx2txtLoader(file_path)
    elif ext == "txt":
        loader = TextLoader(file_path)
    elif ext == "md":
        loader = UnstructuredMarkdownLoader(file_path)
    else:
        raise ValueError(f"Unsupported file format: {ext}")

    pages = await loader.aload()

    #使用RecursiveCharacterTextSplitter将文档分块
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=settings.SPLIT_CHUNK_SIZE, #默认512
        chunk_overlap=settings.SPLIT_CHUNK_OVERLAP, #默认64
        separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""]
    )

    documents = text_splitter.split_documents(pages)

    return documents

