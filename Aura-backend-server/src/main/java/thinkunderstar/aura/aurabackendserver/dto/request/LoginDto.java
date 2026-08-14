package thinkunderstar.aura.aurabackendserver.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoginDto {
    /**
     * 1:密码登录，2:验证码登录
     */
    private int loginWay;
    /**
     * 手机号或者邮箱
     */
    private String username;
    private String password;
    /**
     * 验证码
     */
    private String code;

    @JsonProperty("isRemember")
    private boolean isRemember;
}
