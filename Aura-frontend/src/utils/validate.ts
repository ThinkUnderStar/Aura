// 前端格式校验（与后端 util/ValidateUtils.java 及各 Service 的文件规则 1:1 对齐）
// 校验函数返回「错误文案或 null」，供各表单实时内联提示复用。

// ---- 文本字段正则（来源：ValidateUtils.java）----

/** 用户名：4~16 位，字母开头，仅含字母、数字、下划线 */
const USERNAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]{3,15}$/
/** 密码：8~20 位，须同时包含字母与数字，允许安全符号 !@#$%^&*_- */
const PASSWORD_REGEX = /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z!@#$%^&*_\-]{8,20}$/
/** 手机号：1 开头 + [3-9] + 9 位数字 */
const PHONE_REGEX = /^1[3-9]\d{9}$/
/** 邮箱 */
const EMAIL_REGEX = /^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\.[a-zA-Z0-9]+)*\.[a-zA-Z0-9]{2,6}$/
/** 智能体名：1~20 位，中文/字母/数字/空格/下划线/连字符 */
const AGENT_NAME_REGEX = /^[一-龥a-zA-Z0-9_\-\s]{1,20}$/

// ---- 文件规则（来源：SysDocumentServiceImpl / SysUserServiceImpl / SysWorkspaceServiceImpl）----

/** 文档上传支持的扩展名 */
export const DOCUMENT_EXTENSIONS = ['docx', 'pdf', 'txt', 'md']
/** 文档上传最大 500MB */
export const DOCUMENT_MAX_SIZE = 500 * 1024 * 1024
/** 头像 / Logo 图片支持的扩展名 */
export const IMAGE_EXTENSIONS = ['jpg', 'png', 'jpeg', 'webp']
/** 头像 / Logo 图片最大 5MB */
export const IMAGE_MAX_SIZE = 5 * 1024 * 1024

function extOf(name: string): string {
  const i = name.lastIndexOf('.')
  return i < 0 ? '' : name.slice(i + 1).toLowerCase()
}

// ---- 文本字段校验 ----

export function validateUsername(v: string): string | null {
  if (!v) return '用户昵称不能为空'
  if (!USERNAME_REGEX.test(v)) return '4~16 位，字母开头，仅可含字母、数字、下划线'
  return null
}

export function validatePassword(v: string): string | null {
  if (!v) return '用户密码不能为空'
  if (!PASSWORD_REGEX.test(v)) return '8~20 位，须同时包含字母与数字，可用 !@#$%^&*_-'
  return null
}

export function validatePhone(v: string): string | null {
  if (!v) return '手机号不能为空'
  if (!PHONE_REGEX.test(v)) return '手机号格式不正确'
  return null
}

export function validateEmail(v: string): string | null {
  if (!v) return '邮箱地址不能为空'
  if (!EMAIL_REGEX.test(v)) return '邮箱地址格式不正确'
  return null
}

export function validateAgentName(v: string): string | null {
  if (!v.trim()) return '智能体名称不能为空'
  if (!AGENT_NAME_REGEX.test(v)) return '1~20 位，可含中文、字母、数字、空格、下划线、连字符'
  return null
}

/** 非空校验（通用，自定义文案）。名称/描述等字段后端仅要求非空 + 敏感词（敏感词无法前端校验） */
export function required(v: string, msg: string): string | null {
  return v ? null : msg
}

/** 登录账号：仅接受手机号或邮箱（与 AuthServiceImpl.validateAndRateLimit 一致） */
export function validateLoginAccount(v: string): string | null {
  if (!v) return '账号不能为空'
  if (!PHONE_REGEX.test(v) && !EMAIL_REGEX.test(v)) return '账号需为手机号或邮箱格式'
  return null
}

// ---- 文件校验 ----

export function validateDocumentFile(file: File): string | null {
  if (!file.size) return '文件不能为空'
  if (file.size > DOCUMENT_MAX_SIZE) return '上传的文件过大，超过了 500MB'
  const ext = extOf(file.name)
  if (!ext) return '文件名缺少扩展名'
  if (!DOCUMENT_EXTENSIONS.includes(ext)) return '暂时只支持上传 docx、pdf、txt、md 格式的文件'
  return null
}

export function validateImageFile(file: File): string | null {
  if (!file.size) return '图片不能为空'
  if (file.size > IMAGE_MAX_SIZE) return '上传的图片文件过大（超过 5MB）'
  const ext = extOf(file.name)
  if (!ext) return '文件名缺少扩展名'
  if (!IMAGE_EXTENSIONS.includes(ext)) return '图片格式只支持 jpg、png、jpeg、webp'
  return null
}
