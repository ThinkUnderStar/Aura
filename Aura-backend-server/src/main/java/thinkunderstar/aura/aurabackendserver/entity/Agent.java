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
    private String description;
    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;

    /**
     * 1-活跃 0-已归档
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
        this.temperature = new BigDecimal("0.70");
        this.maxTokens = 4096;
        this.status = 1;
    }
}