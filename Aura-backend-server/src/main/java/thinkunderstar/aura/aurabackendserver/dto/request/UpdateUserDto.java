package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class UpdateUserDto {
    private String username;
    private String password;
    private String repeatPassword;
    private String phone;
    private String email;
    private String code;

    //只包括“username”，“password”，“phone”，“email”
    private String type;
}
