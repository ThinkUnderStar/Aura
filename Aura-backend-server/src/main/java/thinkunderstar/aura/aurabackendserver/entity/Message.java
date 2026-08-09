package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息表实体
 * 对应表: messages
 * <p>
 * 用于存储 Agent 与用户之间的对话消息，支持多分支对话。
 * 每个消息记录通过 branch_path 区分不同分支，通过 create_time 排序。
 */
@Data
@TableName("messages")
public class Message {

    /**
     * 消息ID，自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属 Agent ID，关联 agents 表
     */
    private Long agentId;

    /**
     * 消息角色
     * <p>
     * 取值：user（用户）、assistant（助手）、tool_confirm（工具确认消息）
     */
    private String role;

    /**
     * 对话内容（原始文本）
     * <p>
     * 对于 tool_confirm 类型，存储完整的操作描述
     */
    private String content;

    /**
     * 乐观锁版本号
     * <p>
     * 插入时自动填充为 1，每次更新时自动 +1。
     * 用于防止并发更新冲突。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    /**
     * 创建时间
     * <p>
     * 插入时自动填充当前时间，精确到微秒（对应 MySQL DATETIME(6)）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    //============== Human 消息独有 创建新的对话分支用 =============
    /**
     * 分支源快照 ID（LangGraph checkpoint_id）
     * <p>
     * 仅当 role = 'human' 时有效，表示该 Human 消息是从哪个图状态快照创建的分支。
     * 主分支为 NULL，子分支的 Human 消息指向分支起点的 checkpoint_id。
     * 其他角色（assistant、tool_confirm）的该字段始终为 NULL。
     */
    private String fromCheckpointId;

    // ===== 工具确认专用字段（仅 tool_confirm 时有值） =====
    /**
     * 用户确认动作
     * <p>
     * 取值：approve（同意）、reject（拒绝）、edit（编辑后同意，这个选择有些工具可能没有）
     */
    private String action;

    /**
     * 用户编辑后的新内容（仅 action = 'edit' 时有值）
     */
    private String editedContent;
}