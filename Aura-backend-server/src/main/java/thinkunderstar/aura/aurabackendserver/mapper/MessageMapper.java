package thinkunderstar.aura.aurabackendserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;
import thinkunderstar.aura.aurabackendserver.entity.Message;

public interface MessageMapper extends BaseMapper<Message> {
    /**
     * 分页查询指定 Agent 及其所有子分支的消息（包含自身）
     */
    @Select("SELECT id, agent_id, branch_path, role, content, create_time, action, edited_content " +
            "FROM messages " +
            "WHERE agent_id = #{agentId} " +
            "AND (branch_path = #{branchPath} OR branch_path LIKE CONCAT(#{branchPath}, '/%')) " +
            "ORDER BY create_time DESC")
    Page<MessageVODto> selectMessageVODto(Page<MessageVODto> page,
                                                                   @Param("agentId") Long agentId,
                                                                   @Param("branchPath") String branchPath);
}
