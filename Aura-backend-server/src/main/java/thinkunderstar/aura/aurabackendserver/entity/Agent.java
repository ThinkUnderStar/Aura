package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agents")
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /**
     * 1-活跃 0-已归档
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Agent() {}

    public Agent(Long id, Long userId, String name) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.status = 1;
    }
}