package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_kb_bindings")
public class AgentKbBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;
    private Long kbId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 乐观锁版本号
     * <p>
     * 插入时自动填充为 1，每次更新时自动 +1。
     * 用于防止并发更新冲突。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    public AgentKbBinding() {}

    public AgentKbBinding(Long agentId, Long kbId) {
        this.agentId = agentId;
        this.kbId = kbId;
    }
}