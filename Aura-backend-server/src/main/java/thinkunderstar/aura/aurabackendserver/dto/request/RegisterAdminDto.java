package thinkunderstar.aura.aurabackendserver.dto.request;

import lombok.Data;

@Data
public class RegisterAdminDto {
    private String username;
    private String password;
    private String repeatPassword;
    private String email;
    private String emailCode;
    private String phone;
    private String phoneCode;
}
