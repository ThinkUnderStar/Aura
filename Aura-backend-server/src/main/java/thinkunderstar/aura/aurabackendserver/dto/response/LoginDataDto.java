package thinkunderstar.aura.aurabackendserver.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginDataDto {
    public LoginDataDto(
            Long id,
            String username,
            String phone,
            String email,
            String avatar,
            Integer role,
            Integer status,
            LocalDateTime banStartTime,
            LocalDateTime banEndTime,
            String banReason,
            Long banBy
    ) {
        this.id = id;
        this.username = username;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.role = role;
        this.status = status;
        this.banStartTime = banStartTime;
        this.banEndTime = banEndTime;
        this.banReason = banReason;
        this.banBy = banBy;
        this.token = null;
    }

    private Long id;
    private String username;
    private String phone;
    private String email;
    private String avatar;
    private Integer role;
    private Integer status;
    private LocalDateTime banStartTime;
    private LocalDateTime banEndTime;
    private String banReason;
    private Long banBy;
    private String token;
}
