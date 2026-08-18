// 文档相关工具

/**
 * 各文件类型对应的 MIME 类型。
 * 后端 /document/content 返回时不带 Content-Type（或为 octet-stream），
 * 浏览器/Blob URL 拿不到正确类型导致预览乱码，这里在前端兜底补全。
 */
const MIME_BY_TYPE: Record<string, string> = {
  pdf: 'application/pdf',
  // 注意 md 不用 text/markdown：Chrome 没有内置 markdown 渲染器，收到该类型会直接下载而不是预览
  txt: 'text/plain;charset=utf-8',
  md: 'text/plain;charset=utf-8',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
}

/**
 * 按文件类型重建 Blob，确保预览时浏览器能正确渲染。
 * - txt/md：以 UTF-8 纯文本在新标签页打开（不下载、不乱码）
 * - pdf：浏览器内置查看器打开
 * - docx：浏览器无法内联预览，触发下载，需本地查看
 */
export function toPreviewBlob(blob: Blob, fileType: string): Blob {
  const type = MIME_BY_TYPE[fileType]
  if (!type || blob.type === type) return blob
  return new Blob([blob], { type })
}
