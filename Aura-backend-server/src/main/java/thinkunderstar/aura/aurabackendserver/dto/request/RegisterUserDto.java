package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class RegisterUserDto {
    private String username;
    private String password;
    private String repeatPassword;
    private String phone;
    private String code;
}
