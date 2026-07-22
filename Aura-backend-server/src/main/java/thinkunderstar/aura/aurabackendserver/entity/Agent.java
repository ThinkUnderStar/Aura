package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agents")
public class Agent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /**
     * 1-活跃 0-已归档 (暂未用到，先滞留)
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Agent() {}

    public Agent(Long userId, String name) {
        this.userId = userId;
        this.name = name;
        this.status = 1;
    }
}