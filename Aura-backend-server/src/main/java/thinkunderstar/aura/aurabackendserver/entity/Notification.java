package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    private String title;
    private String content;

    /**
     * 通知类型: report_result-举报结果, feedback_reply-反馈回复
     */
    private String type;

    /**
     * 关联业务ID(report_id 或 feedback_id)
     */
    private Long relatedId;

    /**
     * 0-未读, 1-已读
     */
    private Integer isRead;

    /**
     * 0-已删除, 1-正常
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     * <p>
     * 插入时自动填充为 1，每次更新时自动 +1。
     * 用于防止并发更新冲突。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    public Notification() {}

    public Notification(Long userId, String title, String content, String type, Long relatedId) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.type = type;
        this.relatedId = relatedId;
        this.isRead = 0;
        this.status = 1;
    }
}
