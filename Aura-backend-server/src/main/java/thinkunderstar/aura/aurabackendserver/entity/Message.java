package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("messages")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    /**
     * user/assistant/tool
     */
    private String role;

    private String content;

    /**
     * 工具调用记录（JSON）
     */
    private String toolCalls;

    /**
     * Agent推理过程（调试用）
     */
    private String thought;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Message() {}

    public Message(Long agentId, String role, String content) {
        this.agentId = agentId;
        this.role = role;
        this.content = content;
    }
}