package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;
import thinkunderstar.aura.aurabackendserver.entity.Message;

@Data
public class MessageUpdateVODto extends ChatVODto{
    private Message message;
}
