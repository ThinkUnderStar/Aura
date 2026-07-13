package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class RegisterDto {
    /**
     * 用户昵称
     */
    private String username;
    private String password;
    private String repeatPassword;
    private String phone;
    private String code;
}
