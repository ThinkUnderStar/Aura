package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workspaces")
public class Workspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private String logo;
    private String inviteCode;

    private Long ownerId;

    /**
     * 关联的知识库ID
     */
    private Long kbId;

    /**
     * 1-正常 0-已解散 2-被封禁
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Workspace() {}

    public Workspace(String name, String description, Long ownerId) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.status = 1;
    }
}