package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class SetRoleDto {
    private Long workspaceId;
    private Long memberId;
    private Integer setRole;
}
