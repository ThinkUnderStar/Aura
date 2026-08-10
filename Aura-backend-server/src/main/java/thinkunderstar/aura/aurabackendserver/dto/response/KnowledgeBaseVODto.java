package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

@Data
public class KnowledgeBaseVODto {
    private String collectionName;
    private String description;

    public KnowledgeBaseVODto(String collectionName, String description) {
        this.collectionName = collectionName;
        this.description = description;
    }

    public KnowledgeBaseVODto() {
    }
}
