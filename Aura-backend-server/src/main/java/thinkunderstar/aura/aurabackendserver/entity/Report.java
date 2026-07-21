package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reports")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 举报人用户ID
     */
    private Long reporterId;

    /**
     * 被举报的用户ID
     */
    private Long reportedUserId;

    /**
     * 被举报的团队ID
     */
    private Long reportedWorkspaceId;

    /**
     * 举报目标类型: user-用户, workspace-团队, document-文档
     */
    private String targetType;

    /**
     * 举报目标ID
     */
    private Long targetId;

    /**
     * 举报原因: spam-垃圾信息, harassment-骚扰, inappropriate-不当内容, violation-违规行为, other-其他
     */
    private String reason;

    /**
     * 举报详细描述
     */
    private String description;

    /**
     * 处理状态: 0-待处理, 1-已处理, 2-已驳回
     */
    private Integer status;

    /**
     * 处理人ID(管理员)
     */
    private Long handlerId;

    /**
     * 处理结果说明
     */
    private String handleResult;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Report() {}

    public Report(Long reporterId, String targetType, Long targetId,
                  String reason, String description) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.description = description;
        this.status = 0;
    }
}
