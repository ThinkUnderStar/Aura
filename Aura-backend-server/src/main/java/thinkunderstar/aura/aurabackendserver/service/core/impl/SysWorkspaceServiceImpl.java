package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceService;

@Service
public class SysWorkspaceServiceImpl implements SysWorkspaceService {
    private final WorkspaceMapper workspaceMapper;

    public SysWorkspaceServiceImpl(WorkspaceMapper workspaceMapper) {
        this.workspaceMapper = workspaceMapper;
    }

    @Override
    public Result<IPage<WorkspaceVODto>> getMyWorkspaces(int page, int size) {
        long loginId = StpUtil.getLoginIdAsLong();

        Page<WorkspaceVODto> pageVO = new Page<>(page, size);
        IPage<WorkspaceVODto> workspaceVODtoIPage = workspaceMapper.selectUserWorkspaces(pageVO, loginId);

        return Result.success(workspaceVODtoIPage);
    }
}
