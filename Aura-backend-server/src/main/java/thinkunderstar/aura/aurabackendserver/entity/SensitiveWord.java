package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sensitive_words")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 敏感词
     */
    private String word;

    /**
     * 处理方式: 1-替换, 2-直接拦截（默认）
     */
    private Integer type = 2;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public SensitiveWord() {}
}
