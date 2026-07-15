package thinkunderstar.aura.aurabackendserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;

public interface WorkspaceMapper extends BaseMapper<Workspace> {
    /**
     * 分页查询用户加入的团队列表（按创建时间倒序）
     */
    @Select("SELECT w.*, wm.role " +
            "FROM workspaces w " +
            "JOIN workspace_members wm ON w.id = wm.workspace_id " +
            "WHERE wm.user_id = #{userId} " +
            "AND w.status = 1 " +
            "ORDER BY w.create_time DESC")
    IPage<WorkspaceVODto> selectUserWorkspaces(Page<WorkspaceVODto> page, @Param("userId") Long userId);
}
