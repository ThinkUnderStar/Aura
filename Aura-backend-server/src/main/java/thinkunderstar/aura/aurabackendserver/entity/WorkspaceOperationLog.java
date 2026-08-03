package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspace_operation_logs")
public class WorkspaceOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 团队ID
     */
    private Long workspaceId;

    private String username;

    private Long userId;

    /**
     * 操作模块：
     * workspace      - 团队基本信息（名称/描述）
     * logo           - 团队头像
     * knowledge_base - 知识库
     * document       - 文档文件
     * member         - 团队成员的情况
     */
    private String module;

    /**
     * create/update/delete/
     */
    private String operation;

    /**
     * 操作摘要
     */
    private String requestSummary;

    /**
     * 1-成功 0-失败
     */
    private Integer status;

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
}