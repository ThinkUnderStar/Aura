"""
文档解析与切块。

支持格式与前端 `Aura-frontend/src/utils/validate.ts` 的 `DOCUMENT_EXTENSIONS`
保持一致：**docx / pdf / txt / md**。

相比原实现（Docx2txtLoader + PyPDFLoader + 默认分隔符）的关键改动：

1. **docx 结构化解析**：docx2txt 会把标题层级、表格全部拍平成纯文本，段落与表格的
   真实先后顺序也丢失。改为直接读 `word/document.xml`，顺序遍历 body 子元素，
   维护「标题栈」得到 heading 路径；表格按行序列化成「列名: 值」，保证每个数据行
   自带列名（否则检索「付款比例是多少」时块里只有裸数字）。零新增依赖。
2. **pdf 跨页连续切分**：不再逐页切（每页都会被页边界强制截断）。先把所有页按序
   拼成全文再整体切块，用 `start_index` 反查页码，跨页段落能完整落在同一块里，
   同时保留页码用于溯源。
3. **剔除重复页眉页脚**：页眉在每页都出现，会让「每块都像」，明显拉低块的区分度。
   判据是同一短行出现在超过 30% 的页面（且至少 3 页），避免误删正文里偶尔重复的行。
4. **中文优先分隔符**：句末标点排在逗号与空格之前，并保留分隔符本身，避免句子粘连。
   原实现里逗号排在空格前，而中文几乎没有空格，会一路退化到逗号切，块被切得很碎。
5. **上下文前缀**：每块正文前注入 `【文档名 > 章节】`，让块自带主语（中文长文档里，
   脱离章节的块向量语义很弱）；metadata 补 `chunk_index / page / heading_path`
   供前端溯源。
6. **字段长度受 Milvus schema 约束**：`knowledge_base_service.py` 里
   text 字段 `max_length=4096`、metadata `max_length=2048`，两者都按 **UTF-8 字节**
   计算（不是字符数）。超限会被 Milvus 直接拒绝写入，因此这里统一做兜底截断。
7. **扫描件 OCR 兜底**：PDF 提取不到文本层时自动降级，实现在 `ocr.py`，
   依赖缺失时给出明确提示而不是抛一堆 ModuleNotFoundError。
"""
import asyncio
import bisect
import io
import json
import logging
import os
import re
import zipfile
from collections import Counter
from typing import List, Optional, Tuple
from xml.etree import ElementTree as ET

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.core.config import settings

logger = logging.getLogger(__name__)

# 提取到的有效文本少于该字符数，视为「无文本层」（扫描件/图片型文档）
MIN_TEXT_LENGTH = 5

# 中文优先的递归分隔符：段落 → 换行 → 句末标点 → 分句标点 → 空格 → 硬切。
# 句末标点必须排在逗号与空格之前：中文句子长、少空格，靠前的位置留给「。！？」才切得自然。
SEPARATORS = ["\n\n", "\n", "。", "！", "？", "；", "…", "!", "?", ";", "，", ",", " ", ""]

# 判定为页眉/页脚的短行长度上限
HEADER_LINE_MAX_LEN = 40

# 小于该字符数的正文块视为碎片（表格块除外），仅在整篇都切不出有效块时回退使用
MIN_CHUNK_LEN = 30

# Milvus schema 上限（字节）：text 字段 4096，metadata JSON 2048，各留一点余量
TEXT_MAX_BYTES = 4000
META_MAX_BYTES = 2000

# 前缀与元数据的长度上限：metadata 是按 ensure_ascii 序列化的，一个汉字会膨胀成 6 字节
DOC_NAME_MAX_CHARS = 60
HEADING_MAX_CHARS = 50

# OOXML 命名空间
_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
_W_P = _W + "p"
_W_TBL = _W + "tbl"
_W_TR = _W + "tr"
_W_TC = _W + "tc"
_W_T = _W + "t"
_W_TAB = _W + "tab"
_W_BR = _W + "br"
_W_CR = _W + "cr"
_W_PPR = _W + "pPr"
_W_PSTYLE = _W + "pStyle"
_W_VAL = _W + "val"

# 标题样式：兼容英文 "Heading 1" / 中文 "标题 1" / 纯数字样式 id
_HEADING_STYLE_RE = re.compile(r"^(?:heading|标题)\s*([1-9])$", re.IGNORECASE)
_HEADING_NUM_RE = re.compile(r"^([1-9])$")
_MD_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")

# 章节标题判断用的句中/句末标点：出现这些说明是正文句子而非标题
_TITLE_PUNCT_RE = re.compile(r"[。！？；：，、,.;!?…—－]")
_PURE_NUMBER_RE = re.compile(r"^[\d\s.\-/]+$")


# --------------------------------------------------------------------------- #
# 通用工具
# --------------------------------------------------------------------------- #
def _display_name(file_path: str) -> str:
    """取用于展示的文档名（去扩展名），过长时截断，避免前缀挤占正文字符数。"""
    name = os.path.splitext(os.path.basename(file_path))[0]
    name = re.sub(r"\s+", " ", name).strip()
    return name[:DOC_NAME_MAX_CHARS] if len(name) > DOC_NAME_MAX_CHARS else name


def _looks_like_section_title(text: str) -> bool:
    """
    判断一个短块是不是章节标题（而非正文）。

    为什么需要这个启发式：大量中文文档（尤其简历、公文模板）并不使用 Word 的
    标题样式，而是把「基本信息」「教育背景」这类章节名单独放成一行甚至一列表格。
    这类块只有 4~6 个字，单独入库后是一个几乎没有语义的碎片，检索命中它反而挤掉
    真正有内容的块；把它们识别成章节名注入到后续块的前缀里，才能让正文块自带主语。

    判定条件刻意保守：短、单行、无标点、非纯数字。
    """
    t = text.strip()
    if not (2 <= len(t) <= 20) or "\n" in t:
        return False
    if _TITLE_PUNCT_RE.search(t) or _PURE_NUMBER_RE.match(t):
        return False
    return True


def _build_splitter() -> RecursiveCharacterTextSplitter:
    return RecursiveCharacterTextSplitter(
        chunk_size=settings.SPLIT_CHUNK_SIZE,
        chunk_overlap=settings.SPLIT_CHUNK_OVERLAP,
        separators=SEPARATORS,
        keep_separator=True,   # 保留句末标点，避免句子粘连
        strip_whitespace=True,
        add_start_index=True,  # 记录块在原文中的起点，用于 PDF 反查页码
    )


def _compose_prefix(doc_name: str, heading: str) -> str:
    """块正文前缀：让每个块自带「哪份文档、哪一章」。"""
    if heading:
        return f"【{doc_name} > {heading[:HEADING_MAX_CHARS]}】"
    return f"【{doc_name}】"


def _fit_text(text: str, limit: int = TEXT_MAX_BYTES) -> str:
    """
    按 UTF-8 字节数截断，保证不超 Milvus 的 VARCHAR 上限。

    注意 VARCHAR 的 max_length 在 Milvus 里按字节计：4000 字节约 1300 汉字，
    单个块正常不会到这个量级，这里是防止异常文档（超长段落）导致整篇入库失败。
    """
    raw = text.encode("utf-8")
    if len(raw) <= limit:
        return text
    # errors="ignore" 避免把多字节字符截成半个
    clipped = raw[: limit - 3].decode("utf-8", errors="ignore")
    return clipped + "…"


def _fit_metadata(metadata: dict) -> dict:
    """metadata 最终由 embedding.py 以 json.dumps(ensure_ascii=True) 序列化，按字节控制。"""
    for _ in range(8):
        if len(json.dumps(metadata, ensure_ascii=True).encode("utf-8")) <= META_MAX_BYTES:
            return metadata
        # 逐级缩短 heading_path，它是最容易膨胀的字段
        heading = str(metadata.get("heading_path") or "")
        if heading:
            metadata["heading_path"] = heading[: max(len(heading) - 10, 0)]
            continue
        name = str(metadata.get("document_name") or "")
        if name:
            metadata["document_name"] = name[: max(len(name) - 10, 0)]
            continue
        break
    return metadata


def _make_chunk(
    body: str,
    doc_name: str,
    heading: str,
    page: Optional[int],
    chunk_type: str,
) -> Document:
    metadata = {
        "document_name": doc_name,
        "heading_path": heading,
        "chunk_type": chunk_type,
        "chunk_index": 0,  # 最后统一回填
    }
    if page is not None:
        # 统一成 1 基页码，与用户看到的页码一致
        metadata["page"] = page + 1

    content = f"{_compose_prefix(doc_name, heading)}\n{body.strip()}"
    return Document(
        page_content=_fit_text(content),
        metadata=_fit_metadata(metadata),
    )


def _emit_chunks(
    pieces: List[Document],
    doc_name: str,
    heading: str,
    chunk_type: str,
    out: List[Document],
    short_out: List[Document],
    page_of=None,
) -> None:
    """
    把切好的片段收进结果列表。

    碎片（正文不足 MIN_CHUNK_LEN）单独放进 short_out 暂存：正常情况直接丢弃，
    只有整篇文档都切不出有效块时才回退使用，避免「短公告」类文档直接报错。
    """
    for piece in pieces:
        body = (piece.page_content or "").strip()
        # overlap 回退可能让块从句子中间开始，块首留下一个孤立标点（“。如果…”），
        # 去掉它，避免污染向量与展示效果
        body = body.lstrip("。！？；，、,;.!?… \t\r\n").strip()
        if not body:
            continue
        page = page_of(piece) if page_of else None
        doc = _make_chunk(body, doc_name, heading, page, chunk_type)
        if chunk_type != "table" and len(body) < MIN_CHUNK_LEN:
            short_out.append(doc)
            continue
        out.append(doc)


# --------------------------------------------------------------------------- #
# PDF
# --------------------------------------------------------------------------- #
def _extract_pdf_pages_pymupdf(file_path: str) -> List[Tuple[int, str]]:
    # PyMuPDF 1.24 起官方模块名是 pymupdf，fitz 仅为兼容别名且已标记弃用；
    # 旧版本没有 pymupdf 这个名字，故双向回退，避免任一版本下导入失败
    try:
        import pymupdf as fitz
    except ImportError:  # pragma: no cover - 仅旧版 PyMuPDF 会走到
        import fitz

    with fitz.open(file_path) as doc:
        return [(i, page.get_text("text") or "") for i, page in enumerate(doc)]


def _extract_pdf_pages_pypdf(file_path: str) -> List[Tuple[int, str]]:
    from pypdf import PdfReader

    reader = PdfReader(file_path)
    return [(i, page.extract_text() or "") for i, page in enumerate(reader.pages)]


def _extract_pdf_pages(file_path: str) -> List[Tuple[int, str]]:
    """
    提取 PDF 每页文本，返回 [(页号0基, 文本)]。

    优先 PyMuPDF（对拼版 / 多次增量保存 / 多 EOF 这类结构异常的 PDF 容错更强、更快），
    未安装时回退 pypdf（pypdf 读坏 xref 会直接抛 PdfReadError）。
    """
    errors: List[str] = []

    try:
        pages = _extract_pdf_pages_pymupdf(file_path)
        if any(t.strip() for _, t in pages):
            return pages
        errors.append("PyMuPDF 未解析出文本层")
    except ImportError:
        pass
    except Exception as e:  # noqa: BLE001 三方库异常类型不稳定，统一兜住
        errors.append(f"PyMuPDF: {type(e).__name__}: {e}")

    try:
        pages = _extract_pdf_pages_pypdf(file_path)
        if any(t.strip() for _, t in pages):
            return pages
        errors.append("pypdf 未解析出文本层")
    except Exception as e:  # noqa: BLE001
        errors.append(f"pypdf: {type(e).__name__}: {e}")

    # 走到这里不直接抛错：交给上层判断是否走 OCR（扫描件本来就没有文本层）
    if errors and all("未解析出文本层" not in e for e in errors):
        raise ValueError("PDF 解析失败，文件可能已损坏或结构异常（" + "；".join(errors) + "）")
    return []


async def _load_pdf_pages(file_path: str) -> List[Tuple[int, str]]:
    """
    加载 PDF 页文本，在「无文本层」时自动降级到 OCR。

    扫描件/图片型 PDF 的页面里只有位图，能正常打开但每页文本为空；
    直接往下切块只会得到空向量并污染知识库，所以用 OCR 把文字识别出来。
    OCR 是懒加载的：只有提取不到文本时才会初始化引擎，正常 PDF 零开销。
    """
    pages = await asyncio.to_thread(_extract_pdf_pages, file_path)
    if len("".join(t for _, t in pages).strip()) >= MIN_TEXT_LENGTH:
        return pages

    logger.info("PDF 未提取到文本层，尝试 OCR 识别：%s", file_path)
    from app.services.rag.ocr import OCRUnavailableError, ocr_pdf

    try:
        ocr_docs = await ocr_pdf(file_path)
    except OCRUnavailableError as e:
        raise ValueError(f"该 PDF 没有文本层（可能是扫描件），且 OCR 组件不可用：{e}") from e

    if len("".join(d.page_content or "" for d in ocr_docs).strip()) < MIN_TEXT_LENGTH:
        raise ValueError(
            "该 PDF 未提取到任何文本，OCR 也未能识别出文字。"
            "请确认文件是否为空白扫描件或图片过于模糊，建议上传含文本层的 PDF。"
        )

    logger.info("OCR 识别成功，共 %d 页有文本内容", len(ocr_docs))
    return [(int(d.metadata.get("page", i)), d.page_content or "") for i, d in enumerate(ocr_docs)]


def _strip_repeated_lines(pages: List[Tuple[int, str]]) -> List[Tuple[int, str]]:
    """
    剔除重复页眉/页脚（含页码）。

    判据：同一短行出现在超过 30% 的页面中，且至少 3 页。
    这样正文里偶尔重复的「合计」「备注」不会被误删。
    """
    if len(pages) < 4:
        return pages

    counter: Counter = Counter()
    for _, text in pages:
        seen = set()
        for line in text.splitlines():
            s = line.strip()
            if 0 < len(s) <= HEADER_LINE_MAX_LEN and s not in seen:
                seen.add(s)
                counter[s] += 1

    threshold = max(3, int(len(pages) * 0.3))
    noise = {s for s, c in counter.items() if c >= threshold}
    if not noise:
        return pages

    logger.info("检测到 %d 条重复页眉/页脚，已从正文剔除", len(noise))
    cleaned: List[Tuple[int, str]] = []
    for page_no, text in pages:
        kept = [line for line in text.splitlines() if line.strip() not in noise]
        cleaned.append((page_no, "\n".join(kept)))
    return cleaned


def _build_full_text(pages: List[Tuple[int, str]]) -> Tuple[str, List[int], List[Tuple[int, int, int]]]:
    """
    把所有页拼成一篇全文，同时记录每页在全文中的偏移区间，用于反查页码。

    :return: (全文, 每页起始偏移列表, [(起始偏移, 结束偏移, 页号)])
    """
    parts: List[str] = []
    pos = 0
    spans: List[Tuple[int, int, int]] = []
    for page_no, text in pages:
        t = text.strip()
        if not t:
            continue
        parts.append(t)
        spans.append((pos, pos + len(t), page_no))
        pos += len(t) + 2  # 页间用 "\n\n" 连接
    return "\n\n".join(parts), [s[0] for s in spans], spans


def _page_of(offset: int, starts: List[int], spans: List[Tuple[int, int, int]]) -> int:
    """由全文偏移反查页码（0 基）。"""
    if not spans:
        return 0
    idx = bisect.bisect_right(starts, offset) - 1
    return spans[max(idx, 0)][2]


# --------------------------------------------------------------------------- #
# DOCX（直接解析 OOXML，零额外依赖）
# --------------------------------------------------------------------------- #
def _paragraph_text(p) -> str:
    """取段落文本：拼接所有 w:t，并还原制表符与换行。"""
    parts: List[str] = []
    for node in p.iter():
        tag = node.tag
        if tag == _W_T:
            parts.append(node.text or "")
        elif tag == _W_TAB:
            parts.append("\t")
        elif tag in (_W_BR, _W_CR):
            parts.append("\n")
    return "".join(parts).strip()


def _heading_level(p) -> int:
    """识别段落标题层级：兼容 "Heading 1" / "标题 1" / 纯数字样式 id / Title。"""
    ppr = p.find(_W_PPR)
    if ppr is None:
        return 0
    style = ppr.find(_W_PSTYLE)
    if style is None:
        return 0
    name = (style.get(_W_VAL) or "").strip()
    if not name:
        return 0
    if name.lower() in ("title", "标题"):
        return 1
    m = _HEADING_STYLE_RE.match(name) or _HEADING_NUM_RE.match(name)
    return int(m.group(1)) if m else 0


def _cell_text(tc) -> str:
    """单元格文本：合并单元格里会有多个段落，用空格连接。"""
    texts = [_paragraph_text(p) for p in tc.findall(_W_P)]
    return " ".join(t for t in texts if t).strip()


def _table_rows(tbl) -> List[List[str]]:
    rows: List[List[str]] = []
    for tr in tbl.findall(_W_TR):
        cells = [_cell_text(tc) for tc in tr.findall(_W_TC)]
        if any(cells):
            rows.append(cells)
    return rows


def _table_to_text(rows: List[List[str]]) -> str:
    """
    表格序列化：首行作表头，每个数据行转成「列名: 值；列名: 值」。

    直接把表格拍平成一堆数字后，检索「付款比例是多少」时块里只有数字没有列名，
    LLM 无法判断哪个数对应哪个字段；逐行带上列名，表格才真正可检索。
    """
    if not rows:
        return ""
    header = rows[0]
    # 表头行的展示过滤掉空单元格：单列表格或带空格子的布局表很常见，
    # 不过滤会拼出「基本信息 | 」这种尾巴，进而污染 heading_path
    lines = [" | ".join(h for h in header if h.strip())]
    for row in rows[1:]:
        parts = []
        for i, value in enumerate(row):
            key = header[i] if i < len(header) and header[i] else f"列{i + 1}"
            parts.append(f"{key}: {value}" if value else f"{key}: (空)")
        lines.append("；".join(parts))
    return "\n".join(lines)


def _load_docx_blocks(file_path: str) -> List[Document]:
    """
    顺序遍历 docx 的 body，产出「文本块 / 表格块」，每块带上标题路径。

    必须按 body 子元素的真实顺序遍历，而不是 paragraphs + tables 分开取：
    后者会把所有段落和所有表格各自成组，丢失它们在文档中的先后关系。
    """
    with zipfile.ZipFile(file_path) as package:
        try:
            raw = package.read("word/document.xml")
        except KeyError as e:
            raise ValueError("docx 结构异常：缺少 word/document.xml") from e

    root = ET.fromstring(raw)
    body = root.find(_W + "body")
    if body is None:
        raise ValueError("docx 结构异常：未找到 body")

    blocks: List[Document] = []
    stack: List[Tuple[int, str]] = []
    buffer: List[str] = []
    buffer_heading = ""

    def current_heading() -> str:
        return " > ".join(text for _, text in stack)

    def flush() -> None:
        nonlocal buffer, buffer_heading
        text = "\n".join(buffer).strip()
        if text:
            blocks.append(
                Document(
                    page_content=text,
                    metadata={"heading_path": buffer_heading, "block_type": "text"},
                )
            )
        buffer = []

    for child in body:
        tag = child.tag
        if tag == _W_P:
            text = _paragraph_text(child)
            level = _heading_level(child)
            if level:
                flush()
                while stack and stack[-1][0] >= level:
                    stack.pop()
                stack.append((level, text or f"未命名标题{level}"))
                buffer_heading = current_heading()
                continue
            if not text:
                flush()
                continue
            if not buffer:
                buffer_heading = current_heading()
            buffer.append(text)
        elif tag == _W_TBL:
            flush()
            text = _table_to_text(_table_rows(child))
            if text:
                blocks.append(
                    Document(
                        page_content=text,
                        metadata={"heading_path": current_heading(), "block_type": "table"},
                    )
                )

    flush()
    return blocks


# --------------------------------------------------------------------------- #
# Markdown
# --------------------------------------------------------------------------- #
def _load_markdown_blocks(file_path: str) -> List[Document]:
    """按 # 标题层级切段，产出与 docx 同构的块，便于复用同一套切块逻辑。"""
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.read().splitlines()

    blocks: List[Document] = []
    stack: List[Tuple[int, str]] = []
    buffer: List[str] = []
    buffer_heading = ""

    def flush() -> None:
        nonlocal buffer
        text = "\n".join(buffer).strip()
        if text:
            blocks.append(
                Document(
                    page_content=text,
                    metadata={"heading_path": buffer_heading, "block_type": "text"},
                )
            )
        buffer = []

    for line in lines:
        m = _MD_HEADING_RE.match(line)
        if m:
            flush()
            level = len(m.group(1))
            while stack and stack[-1][0] >= level:
                stack.pop()
            stack.append((level, m.group(2)))
            buffer_heading = " > ".join(t for _, t in stack)
            continue
        if not line.strip():
            flush()
            continue
        if not buffer:
            buffer_heading = " > ".join(t for _, t in stack)
        buffer.append(line)
    flush()
    return blocks


# --------------------------------------------------------------------------- #
# TXT
# --------------------------------------------------------------------------- #
_TXT_ENCODINGS = ("utf-8-sig", "utf-8", "gb18030", "gbk", "big5", "utf-16", "latin-1")


def _read_text_file(file_path: str) -> str:
    """
    读取纯文本，依次尝试常见编码。

    Windows 下中文 txt 常见 GBK/GB2312，直接按 utf-8 读会抛 UnicodeDecodeError；
    gb18030 是 GBK 的超集，优先于 gbk 尝试能多覆盖一部分生僻字。
    """
    last_error: Optional[Exception] = None
    for encoding in _TXT_ENCODINGS:
        try:
            with open(file_path, "r", encoding=encoding) as f:
                return f.read()
        except (UnicodeDecodeError, LookupError) as e:
            last_error = e
            continue
    raise ValueError(f"文本文件编码无法识别：{type(last_error).__name__}: {last_error}")


# --------------------------------------------------------------------------- #
# 主入口
# --------------------------------------------------------------------------- #
async def document_processor(file_path: str) -> List[Document]:
    """
    文档处理器：按文件类型解析，做结构化切块并注入上下文前缀。

    支持 docx / pdf / txt / md（与前端上传白名单一致）。

    :param file_path: 文件路径
    :return: 切好的文档块列表（每块带 heading_path / page / chunk_index 等元数据）
    """
    ext = os.path.splitext(file_path)[1].lower().lstrip(".")
    doc_name = _display_name(file_path)
    splitter = _build_splitter()

    chunks: List[Document] = []
    short_chunks: List[Document] = []

    if ext == "pdf":
        pages = await _load_pdf_pages(file_path)
        pages = _strip_repeated_lines(pages)
        full_text, starts, spans = _build_full_text(pages)

        if len(full_text) < MIN_TEXT_LENGTH:
            raise ValueError("该文档未提取到有效文本内容，请检查文件是否为空或仅含图片")

        # 先合并成全文再切：跨页续写的段落不会被页边界切断
        pieces = splitter.split_documents([Document(page_content=full_text, metadata={})])
        _emit_chunks(
            pieces,
            doc_name,
            "",
            "text",
            chunks,
            short_chunks,
            page_of=lambda d: _page_of(d.metadata.get("start_index", 0), starts, spans),
        )

    elif ext == "docx":
        blocks = await asyncio.to_thread(_load_docx_blocks, file_path)
        section = ""      # 当前章节名：文档没有标题样式时，靠「独立短块」推断
        title_streak = 0  # 连续短块计数，用于兜住诗歌/清单这类整篇都是短行的文档
        for block in blocks:
            block_type = block.metadata.get("block_type", "text")
            body = (block.page_content or "").strip()
            style_heading = block.metadata.get("heading_path", "") or ""

            if not style_heading and title_streak < 3 and _looks_like_section_title(body):
                # 只有几个字的独立块（常见于简历/公文：章节名单独占一行或一列表格），
                # 不当正文入库，改为记录章节名，注入到后续块的前缀里。
                # 连续多个短块则视为正文短行（诗歌、清单），不再当标题，避免整篇被吞掉。
                section = body
                title_streak += 1
                short_chunks.append(_make_chunk(body, doc_name, "", None, "text"))
                continue

            title_streak = 0
            if style_heading:
                section = style_heading.split(" > ")[-1]

            heading = style_heading or section
            if block_type == "table":
                # 表格不交给递归切分器：按行切开会让「列名: 值」被从中间截断。
                # 超长表格按行分批，每批仍在 chunk_size 内。
                for piece in _split_table_block(body, splitter):
                    _emit_chunks(
                        [Document(page_content=piece, metadata={})],
                        doc_name,
                        heading,
                        "table",
                        chunks,
                        short_chunks,
                    )
                continue
            pieces = splitter.split_documents([block])
            _emit_chunks(pieces, doc_name, heading, block_type, chunks, short_chunks)

    elif ext == "txt":
        text = await asyncio.to_thread(_read_text_file, file_path)
        # 纯文本不存在「无文本层」问题，只有真空白才算失败。
        # 这里不能用 MIN_TEXT_LENGTH 卡：两三个字的公告、便签是合法文档，
        # 应该交给碎片回退逻辑保留，而不是直接报错。
        if not text.strip():
            raise ValueError("该文档未提取到有效文本内容，请检查文件是否为空")
        pieces = splitter.split_documents([Document(page_content=text, metadata={})])
        _emit_chunks(pieces, doc_name, "", "text", chunks, short_chunks)

    elif ext == "md":
        blocks = await asyncio.to_thread(_load_markdown_blocks, file_path)
        for block in blocks:
            pieces = splitter.split_documents([block])
            _emit_chunks(
                pieces,
                doc_name,
                block.metadata.get("heading_path", ""),
                "text",
                chunks,
                short_chunks,
            )

    else:
        raise ValueError(f"不支持的文件格式: {ext}（支持 docx / pdf / txt / md）")

    # 整篇都是短内容（公告、便签等）时，不要把块全过滤光
    if not chunks and short_chunks:
        chunks = short_chunks

    if not chunks:
        raise ValueError("该文档未提取到有效文本内容，请检查文件是否为空或仅含图片")

    # 回填全局块序号（同一文档内递增，供溯源与前端展示使用）
    for index, chunk in enumerate(chunks):
        chunk.metadata["chunk_index"] = index

    avg_len = sum(len(c.page_content) for c in chunks) // max(len(chunks), 1)
    logger.info("切块完成 %s：%d 块，平均 %d 字符", doc_name, len(chunks), avg_len)
    return chunks


def _split_table_block(text: str, splitter: RecursiveCharacterTextSplitter) -> List[str]:
    """
    表格块分批：首行表头在每个分片里重复一次，保证任何一片都能独立读懂。

    表格如果整体塞进一个块，长表格会超过 Milvus 的 text 字节上限；
    如果直接交给递归切分器，它会在任意标点处切断，把「列名: 值」拆散。
    """
    limit = settings.SPLIT_CHUNK_SIZE
    if len(text) <= limit * 1.5:
        return [text]

    lines = text.splitlines()
    header, rows = lines[0], lines[1:]
    if not rows:
        return [text]

    pieces: List[str] = []
    buffer: List[str] = []
    size = len(header)
    for row in rows:
        if buffer and size + len(row) + 1 > limit:
            pieces.append("\n".join([header] + buffer))
            buffer, size = [], len(header)
        buffer.append(row)
        size += len(row) + 1
    if buffer:
        pieces.append("\n".join([header] + buffer))
    return pieces
