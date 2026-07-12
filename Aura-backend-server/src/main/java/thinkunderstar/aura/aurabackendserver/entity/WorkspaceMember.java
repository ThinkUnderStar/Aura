package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspace_members")
public class WorkspaceMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;
    private Long userId;

    /**
     * 1-管理员 2-普通成员
     */
    private Integer role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    public WorkspaceMember() {}

    public WorkspaceMember(Long workspaceId, Long userId, Integer role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
    }

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}