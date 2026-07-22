package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class BindingKbInformationVODto {
    /**
     * 绑定的私人知识库ID
     */
    List<Long> kbIds;
    /**
     * 绑定的团队知识库对应的的团队ID
     */
    List<Long> workspaceIds;
}
