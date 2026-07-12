package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_logs")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 团队ID（个人操作为NULL）
     */
    private Long workspaceId;

    private Long userId;

    /**
     * auth/user/workspace/member/kb/doc/agent/chat
     */
    private String module;

    /**
     * create/update/delete/upload/login/logout
     */
    private String operation;

    private Long resourceId;
    private String resourceName;
    private String ip;
    private String userAgent;
    private String requestSummary;

    /**
     * 1-成功 0-失败
     */
    private Integer status;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public OperationLog() {}

    public OperationLog(Long workspaceId, Long userId, String module, String operation,
                        String requestSummary) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.module = module;
        this.operation = operation;
        this.requestSummary = requestSummary;
        this.status = 1;
        this.durationMs = 0;
    }

    public OperationLog(Long workspaceId, Long userId, String module, String operation,
                        String requestSummary, Integer status, Integer durationMs) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.module = module;
        this.operation = operation;
        this.requestSummary = requestSummary;
        this.status = status;
        this.durationMs = durationMs;
    }
}