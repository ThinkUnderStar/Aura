package thinkunderstar.aura.aurabackendserver.service.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;

public interface SysKnowledgeBaseService {
    /**
     * 创建知识库
     * <p>
     * 用户可创建个人知识库或团队知识库。创建后在 Milvus 中创建对应的向量集合，
     * 并在数据库中记录知识库元数据。
     * 个人知识库仅自己可见，团队知识库对团队成员共享。
     *
     * @param createKnowledgeBaseDto 创建知识库请求参数，包含名称、描述、类型（个人/团队）
     * @return Result 创建结果，成功时返回知识库信息（含 ID、名称、集合名等）,前端将这些信息刷新到泪飙最前面
     */
    Result<KnowledgeBase> createKnowledgeBase(CreateKnowledgeBaseDto createKnowledgeBaseDto);

    /**
     * 获取当前用户的所有知识库（分页）
     * <p>
     * 返回当前登录用户创建的所有知识库，包括个人知识库和团队知识库，
     * 按创建时间倒序排列。需要用户已登录。
     *
     * @param page 当前页码，从1开始
     * @param pageSize 每页记录数
     * @return Result 包含分页知识库数据的响应，每项包含知识库ID、名称、描述、类型、文档数量、状态、创建时间等
     */
    Result<Page<KnowledgeBase>> getMyKnowledgeBases(Integer page, Integer pageSize);
}
