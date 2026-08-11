package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

@Data
public class ToolAllowVODto {
    private long userId;
    private long agentId;
    private String choice;
    private String edition;
}
