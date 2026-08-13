package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ChatVODto {
    private Long userId;

    //用户发给AI的消息
    private String humanContent;

    /**
     *  1-开启联网搜索 0-关闭联网搜索
     */
    private int enableWebSearch;

    //所有绑定的知识库
    private List<KnowledgeBaseVODto> knowledgeBases;

    /**
     *  1-包含敏感词 0-不包括敏感词
     */
    private int isSensitive;
}
