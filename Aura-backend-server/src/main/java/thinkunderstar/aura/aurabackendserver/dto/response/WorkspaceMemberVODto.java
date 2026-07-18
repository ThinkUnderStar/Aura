package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkspaceMemberVODto {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    /**
     * 0-创建者 1-管理员 2-普通成员
     */
    private Integer role;
    /**
     * “创建者”，“管理员”，“普通成员”
     */
    private String roleName;
    private Integer status;
    /**
     * “正常”，“已移除”
     */
    private String statusName;
    private LocalDateTime joinedAt;
}