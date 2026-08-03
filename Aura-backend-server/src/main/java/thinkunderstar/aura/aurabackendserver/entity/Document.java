package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("documents")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String filePath;

    /**
     * 0-索引中 1-已索引 2-失败
     */
    private Integer status;

    private Long uploadBy;

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

    public Document() {}

    public Document(
            Long kbId,
            String fileName,
            Long fileSize,
            String fileType,
            String filePath,
            Long uploadBy
    ) {
        this.kbId = kbId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.filePath = filePath;
        this.uploadBy = uploadBy;
        this.status = 0;
    }
}