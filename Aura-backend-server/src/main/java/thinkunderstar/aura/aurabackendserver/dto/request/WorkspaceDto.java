package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class WorkspaceDto {
    private String name;
    //可以为空
    private String description;

    private String kbName;
    private String kbDescription;
}
