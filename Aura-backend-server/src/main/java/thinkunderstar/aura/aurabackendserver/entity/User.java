package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String phone;
    private String email;
    private String avatar;

    /**
     * 1-普通用户 2-管理员
     */
    private Integer role;

    /**
     * 1-正常 0-禁用
     */
    private Integer status;

    // ==================== 封禁相关字段 ====================

    /**
     * 封禁开始时间
     */
    private LocalDateTime banStartTime;

    /**
     * 封禁结束时间（null表示永久封禁）
     */
    private LocalDateTime banEndTime;

    /**
     * 封禁原因
     */
    private String banReason;

    /**
     * 执行封禁操作的管理员ID
     */
    private Long banBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号
     * <p>
     * 插入时自动填充为 1，每次更新时自动 +1。
     * 用于防止并发更新冲突。
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    /**
     * 0为正常，1为被删除
     */
    private Integer deleted;

    public User() {}

    public User(String username, String password, String phone, String email) {
        this.username = username;
        this.password = password;
        this.phone = phone;
        this.email = email;
        this.status = 1;
    }
}