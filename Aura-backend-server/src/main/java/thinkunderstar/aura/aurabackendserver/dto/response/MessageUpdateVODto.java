package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

@Data
public class MessageUpdateVODto extends ChatVODto{
    private String fromCheckpointId;
}
