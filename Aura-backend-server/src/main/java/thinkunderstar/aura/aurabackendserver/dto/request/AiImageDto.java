package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class AiImageDto {
    String imageName;
    /**
     * 1-为保存 , 0-为不保存
     */
    int isSaved;
}
