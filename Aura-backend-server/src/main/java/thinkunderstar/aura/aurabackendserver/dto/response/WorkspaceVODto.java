package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceVODto {
    private Long id;
    /**
     * 0-创建者 1-管理员 2-普通成员
     */
    private Integer role;
    private String name;
    private String description;
    private String logo;
    private String inviteCode;

    /**
     * 1-正常 0-已解散
     */
    private Integer status;

    private LocalDateTime createTime;

    public WorkspaceVODto(
            Long id,
            Integer role,
            String name,
            String description,
            String logo,
            String inviteCode,
            Integer status,
            LocalDateTime createTime
    ) {
        this.id = id;
        this.role = role;
        this.name = name;
        this.description = description;
        this.logo = logo;
        this.inviteCode = inviteCode;
        this.status = status;
        this.createTime = createTime;
    }
}
