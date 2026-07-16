package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class UpdateKnowledgeBaseDto {
    private Long kbId;
    private String name;
    private String description;

    //只有“name”与“description”这两种类型
    private String type;
}
