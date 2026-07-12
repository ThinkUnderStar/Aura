package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_bases")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;
    private String name;
    private String description;
    private Integer docCount;

    /**
     * 1-正常 0-停用
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public KnowledgeBase() {}

    public KnowledgeBase(Long workspaceId, String name, String description) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.description = description;
        this.docCount = 0;
        this.status = 1;
    }

    /**
     * 获取 Milvus Collection 名称（自动生成）
     */
    public String getCollectionName() {
        return "aura_kb_" + this.workspaceId;
    }
}