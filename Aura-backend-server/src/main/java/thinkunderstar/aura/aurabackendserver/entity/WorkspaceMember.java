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
     * 0-创建者 1-管理员 2-普通成员
     */
    private Integer role;

    /**
     * 0-不在团队中 1-在团队中
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    /**
     * 乐观锁版本号
     * <p>
     * 插入时自动填充为 1，每次更新时自动 +1。
     * 用于防止并发更新冲突。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    public WorkspaceMember() {}

    public WorkspaceMember(Long workspaceId, Long userId, Integer role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.status = 1;
    }

    public boolean isAdmin() {
        return role != null && role == 1;
    }
}