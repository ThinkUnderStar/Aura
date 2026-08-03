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
     * 分支路径，用于区分不同的对话分支
     * <p>
     * 格式说明：
     * <ul>
     *     <li>主分支：main</li>
     *     <li>从主分支 step=3 处创建的分支：main/3</li>
     *     <li>从 main/3 分支 step=2 处创建的分支：main/3/2</li>
     * </ul>
     * 默认值为 main
     */
    private String branchPath;

    /**
     * 消息角色
     * <p>
     * 取值：user（用户）、assistant（助手）
     */
    private String role;

    /**
     * 对话内容（原始文本）
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
}