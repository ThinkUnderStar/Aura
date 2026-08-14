import tiktoken

def count_tokens_for_messages(messages) -> int:
    """
    使用 tiktoken 计算消息列表的 Token 数。
    兼容 LangChain 消息格式。
    """
    try:
        # 使用 cl100k_base 编码（适用于 GPT-4、GPT-3.5-turbo 等）
        enc = tiktoken.get_encoding("cl100k_base")

        # 提取所有消息的 content 并拼接
        text = " ".join([m.content for m in messages if hasattr(m, "content") and m.content])
        return len(enc.encode(text))
    except Exception:
        # 降级方案：按字符数估算（约 1 token ≈ 4 字符）
        total_chars = sum([len(m.content) for m in messages if hasattr(m, "content") and m.content])
        return total_chars // 4
