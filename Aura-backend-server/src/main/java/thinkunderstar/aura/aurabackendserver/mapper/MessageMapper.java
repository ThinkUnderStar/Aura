package thinkunderstar.aura.aurabackendserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;
import thinkunderstar.aura.aurabackendserver.entity.Message;

public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 分页查询指定 Agent 的所有消息（按时间倒序，用于前端展示）
     *
     * @param page    分页对象
     * @param agentId Agent ID
     * @return 分页消息列表
     */
    @Select("SELECT id, agent_id, role, content, create_time, action, edited_content " +
            "FROM messages " +
            "WHERE agent_id = #{agentId} " +
            "ORDER BY create_time DESC")
    Page<MessageVODto> selectMessageVODto(Page<MessageVODto> page,
                                          @Param("agentId") Long agentId);
}