package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息展示 VO（不含内部敏感字段）
 */
@Data
public class MessageVODto {
    private Long id;
    private Long agentId;
    private String role;
    private String content;
    private LocalDateTime createTime;

    // ===== 工具确认专用字段 =====
    private String action;
    private String editedContent;
}