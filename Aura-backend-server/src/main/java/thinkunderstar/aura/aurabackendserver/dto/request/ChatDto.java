package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class ChatDto {
    //用户发给AI的消息
    private String humanContent;
    /**
     *  1-开启联网搜索 0-关闭联网搜索
     */
    private int enableWebSearch;
}
