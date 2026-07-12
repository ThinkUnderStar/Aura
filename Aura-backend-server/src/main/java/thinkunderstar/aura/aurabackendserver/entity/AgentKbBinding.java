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

    public AgentKbBinding() {}

    public AgentKbBinding(Long agentId, Long kbId) {
        this.agentId = agentId;
        this.kbId = kbId;
    }
}