package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("documents")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;
    private Long kbId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String filePath;
    private Integer chunkCount;

    /**
     * 0-索引中 1-已索引 2-失败
     */
    private Integer status;

    private Long uploadBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Document() {}

    public Document(Long workspaceId, Long kbId, String fileName, Long fileSize,
                    String fileType, String filePath, Long uploadBy) {
        this.workspaceId = workspaceId;
        this.kbId = kbId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.filePath = filePath;
        this.uploadBy = uploadBy;
        this.chunkCount = 0;
        this.status = 0;
    }
}