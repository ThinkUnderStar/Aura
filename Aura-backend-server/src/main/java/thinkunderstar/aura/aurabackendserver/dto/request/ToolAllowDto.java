package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class ToolAllowDto {
    private String choice;

    /**
     * 只有当choice的值为edit时，才启用的参数
     */
    private String edition;

    /**
     *  1-开启 0-关闭
     */
    private int enableWebSearch;
}
