package thinkunderstar.aura.aurabackendserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
    /**
     * 根据用户ID获取该用户参加的所有正常团队所绑定的知识库ID（去重）
     * <p>
     * 查询条件：
     * <ul>
     *     <li>用户是团队的正常成员（workspace_members.status = 1）</li>
     *     <li>团队处于正常状态（workspaces.status = 1）</li>
     *     <li>团队绑定了知识库（workspaces.kb_id IS NOT NULL）</li>
     *     <li>知识库处于正常状态（knowledge_bases.status = 1）</li>
     * </ul>
     * <p>
     * 返回结果自动去重，适用于需要获取用户所有可用的团队知识库场景（如 Agent 绑定时的可选列表）。
     *
     * @param userId 用户ID
     * @return 知识库ID列表（去重，可能为空列表）
     */
    @Select("SELECT DISTINCT kb.id " +
            "FROM workspace_members wm " +
            "JOIN workspaces w ON w.id = wm.workspace_id AND w.status = 1 AND w.kb_id IS NOT NULL " +
            "JOIN knowledge_bases kb ON kb.id = w.kb_id AND kb.status = 1 " +
            "WHERE wm.user_id = #{userId} AND wm.status = 1")
    List<Long> selectTeamKbIdsByUserId(@Param("userId") Long userId);
}
