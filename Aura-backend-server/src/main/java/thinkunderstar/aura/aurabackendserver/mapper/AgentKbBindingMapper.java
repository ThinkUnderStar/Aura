package thinkunderstar.aura.aurabackendserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import thinkunderstar.aura.aurabackendserver.entity.AgentKbBinding;

import java.util.List;

public interface AgentKbBindingMapper extends BaseMapper<AgentKbBinding> {
    /**
     * 根据 Agent ID 获取所有绑定的知识库 ID 列表
     *
     * @param agentId Agent ID
     * @return 知识库 ID 列表
     */
    @Select("SELECT kb_id FROM agent_kb_bindings WHERE agent_id = #{agentId}")
    List<Long> selectKbIdsByAgentId(@Param("agentId") Long agentId);
}
