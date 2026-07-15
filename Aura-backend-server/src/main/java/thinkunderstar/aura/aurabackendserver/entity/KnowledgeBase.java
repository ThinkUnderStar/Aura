package thinkunderstar.aura.aurabackendserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_bases")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建者ID（用户ID）
     */
    private Long ownerId;

    /**
     * 1-团队知识库 0-个人知识库
     */
    private Integer isTeam;

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

    public KnowledgeBase(Long ownerId, Integer isTeam, String name, String description) {
        this.ownerId = ownerId;
        this.isTeam = isTeam;
        this.name = name;
        this.description = description;
        this.docCount = 0;
        this.status = 1;
    }

    /**
     * 获取 Milvus Collection 名称
     */
    public String getCollectionName() {
        return "aura_kb_" + this.id + (this.isTeam == 1 ? "_team" : "_personal");
    }
}