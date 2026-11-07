"""
扫描件 / 图片型 PDF 的 OCR 兜底能力。

设计原则：

1. **懒加载**：只有真正遇到「PDF 提取不到文本」时才 import 并初始化 OCR 引擎，
   正常文本型 PDF 走不到这里，不产生任何内存与启动耗时开销。
2. **不占显存**：走 onnxruntime CPU 推理，不碰 torch 的 CUDA 上下文，
   避免和嵌入模型争夺那 8GB 显存。
3. **不阻塞事件循环**：OCR 是 CPU 密集的同步调用，统一丢到 `asyncio.to_thread`，
   否则会卡住整个 FastAPI 事件循环，其他请求全部排队。
4. **缺失依赖要可读**：本模块依赖 `pymupdf`（渲染页面）与 `rapidocr + onnxruntime`
   （文字识别）。任一缺失时抛 `OCRUnavailableError`，由调用方转成用户能看懂的提示，
   而不是抛一串 ModuleNotFoundError。

安装方式：
    pip install pymupdf rapidocr onnxruntime
"""
import asyncio
import logging
import threading
from typing import List, Optional

from langchain_core.documents import Document

logger = logging.getLogger(__name__)

# OCR 参数（Aura 的 config.py 里没有这些配置项，故以模块常量维护）
OCR_DPI = 150          # 页面渲染分辨率：150dpi 对 OCR 精度足够，比 200dpi 快约 20%
OCR_MAX_PAGES = 200    # 单文档最多识别的页数，防止超大扫描件长时间占用
OCR_THREADS = 4        # onnxruntime 线程数，限制 CPU 占用，避免吃满所有核心

# 引擎单例（RapidOCR 初始化很快，但模型常驻内存，只建一次）
_engine = None
_engine_init_failed = False
_engine_lock = threading.Lock()


class OCRUnavailableError(RuntimeError):
    """OCR 依赖缺失或引擎初始化失败。调用方应转成可读的业务提示。"""


def _get_engine():
    """
    获取 OCR 引擎单例（首次调用时加载模型）。

    加载失败时返回 None 并置标志位，后续调用直接短路，
    避免每篇文档都重复尝试 import 一个装不上的包。
    """
    global _engine, _engine_init_failed

    if _engine is not None:
        return _engine
    if _engine_init_failed:
        return None

    with _engine_lock:
        if _engine is not None:
            return _engine
        try:
            from rapidocr import RapidOCR

            _engine = RapidOCR(
                params={
                    # 降低 OCR 自身日志噪音，避免刷屏 uvicorn 输出
                    "Global.log_level": "warning",
                    # 限制 onnxruntime 线程数：OCR 在后台异步执行，不必抢满所有 CPU 核心
                    "EngineConfig.onnxruntime.intra_op_num_threads": OCR_THREADS,
                    "EngineConfig.onnxruntime.inter_op_num_threads": 1,
                }
            )
            logger.info("OCR 引擎加载完成（RapidOCR，线程数=%d）", OCR_THREADS)
        except ImportError as e:
            _engine_init_failed = True
            logger.warning("OCR 引擎不可用（缺少依赖）：%s", e)
            return None
        except Exception as e:  # noqa: BLE001 三方库异常类型不稳定，统一兜住
            _engine_init_failed = True
            logger.warning("OCR 引擎初始化失败：%s: %s", type(e).__name__, e)
            return None

    return _engine


def _extract_text(result) -> str:
    """从 RapidOCR 的返回结果里取出纯文本（按行拼接）。"""
    if result is None:
        return ""
    texts = getattr(result, "txts", None)
    if not texts:
        return ""
    return "\n".join(t.strip() for t in texts if t and t.strip())


def _ocr_pdf_sync(file_path: str, dpi: int, max_pages: int) -> List[Document]:
    """同步执行 OCR（由 asyncio.to_thread 调用，不直接跑在事件循环上）。"""
    try:
        # PyMuPDF 1.24 起模块名为 pymupdf，fitz 是已弃用的兼容别名，故优先新名
        try:
            import pymupdf as fitz
        except ImportError:  # pragma: no cover - 仅旧版 PyMuPDF 会走到
            import fitz
    except ImportError as e:
        raise OCRUnavailableError(
            "缺少 pymupdf，无法把 PDF 页面渲染成图片。安装：pip install pymupdf"
        ) from e

    engine = _get_engine()
    if engine is None:
        raise OCRUnavailableError(
            "OCR 引擎不可用。安装：pip install rapidocr onnxruntime（CPU 推理，不占显存）"
        )

    pages: List[Document] = []
    doc = fitz.open(file_path)
    try:
        total = doc.page_count
        limit = min(total, max_pages)
        if total > max_pages:
            logger.warning(
                "文档共 %d 页，超过上限 %d，仅识别前 %d 页", total, max_pages, limit
            )

        for i in range(limit):
            # 按需渲染单页，避免一次性把所有页转成图片占满内存
            pix = doc[i].get_pixmap(dpi=dpi)
            text = _extract_text(engine(pix.tobytes("png")))
            if text:
                # page 用 0 基索引，与 document_processor 里 +1 的约定配合
                pages.append(
                    Document(page_content=text, metadata={"source": file_path, "page": i})
                )
            if (i + 1) % 10 == 0 or i + 1 == limit:
                logger.info("OCR 进度 %d/%d 页", i + 1, limit)
    finally:
        doc.close()

    return pages


async def ocr_pdf(file_path: str) -> List[Document]:
    """
    对 PDF 逐页 OCR，返回每页一个 Document（仅含识别出文本的页）。

    :raises OCRUnavailableError: 依赖缺失或引擎初始化失败
    """
    return await asyncio.to_thread(_ocr_pdf_sync, file_path, OCR_DPI, OCR_MAX_PAGES)
