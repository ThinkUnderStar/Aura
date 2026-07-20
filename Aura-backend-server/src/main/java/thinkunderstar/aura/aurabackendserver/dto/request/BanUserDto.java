package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class BanUserDto {
    private Long targetUserId;
    /**
     * 1-封禁该用户 2-延长封禁时长
     */
    private Integer type;
    //1时不能为空，2时可以为空，不为空则视为覆盖第一次的reason
    private String banReason;
    //1-封禁的天数 2-延长的天数
    private Long banTime;
}
