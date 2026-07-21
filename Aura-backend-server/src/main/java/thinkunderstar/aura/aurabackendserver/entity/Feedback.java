package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("feedbacks")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 反馈用户ID
     */
    private Long userId;

    /**
     * 反馈类型: bug-功能异常, suggestion-功能建议, experience-使用体验, other-其他
     */
    private String type;

    private String title;
    private String content;

    /**
     * 联系方式(选填)
     */
    private String contact;

    /**
     * 处理状态: 0-待处理, 1-处理中, 2-已完成, 3-已关闭
     */
    private Integer status;

    /**
     * 处理人ID(管理员)
     */
    private Long handlerId;

    /**
     * 管理员回复内容
     */
    private String reply;

    /**
     * 回复时间
     */
    private LocalDateTime replyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Feedback() {}

    public Feedback(Long userId, String type, String title, String content, String contact) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.contact = contact;
        this.status = 0;
    }
}
