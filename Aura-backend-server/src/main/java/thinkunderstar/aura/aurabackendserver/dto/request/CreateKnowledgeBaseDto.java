package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class CreateKnowledgeBaseDto {
    private String name;
    private String description;
    private Integer isTeam;
}
