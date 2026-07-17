package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class UpdateWorkspaceDto {
    private Long workspaceId;
    private String name;
    private String description;
    //只能填“name”或“description”
    private String type;
}
