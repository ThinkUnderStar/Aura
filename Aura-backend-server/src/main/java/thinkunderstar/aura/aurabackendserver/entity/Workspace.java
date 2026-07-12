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

    /**
     * 1-个人空间 2-团队
     */
    private Integer type;

    private Long ownerId;

    /**
     * 1-正常 0-已解散
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Workspace() {}

    public Workspace(String name, String description, Integer type, Long ownerId) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.ownerId = ownerId;
        this.status = 1;
    }

    public boolean isPersonal() {
        return type != null && type == 1;
    }

    public boolean isTeam() {
        return type != null && type == 2;
    }
}