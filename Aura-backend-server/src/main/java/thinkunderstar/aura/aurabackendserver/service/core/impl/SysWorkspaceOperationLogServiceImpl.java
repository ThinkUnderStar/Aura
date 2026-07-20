package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceOperationLog;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceOperationLogMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceOperationLogService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceMemberService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

@Service
public class SysWorkspaceOperationLogServiceImpl implements SysWorkspaceOperationLogService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService workspaceMemberService;
    private final WorkspaceOperationLogMapper workspaceOperationLogMapper;
    private final UserService userService;

    public SysWorkspaceOperationLogServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, WorkspaceService workspaceService, WorkspaceMemberService workspaceMemberService, WorkspaceOperationLogMapper workspaceOperationLogMapper, UserService userService) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.workspaceService = workspaceService;
        this.workspaceMemberService = workspaceMemberService;
        this.workspaceOperationLogMapper = workspaceOperationLogMapper;
        this.userService = userService;
    }

    @Override
    public Result<Page<WorkspaceOperationLog>> getWorkspaceOperationLogs(Long workspaceId, Integer page, Integer size) {
        if (page == null || size == null || workspaceId == null) {
            throw new BusinessException("获取日志接口的参数接收异常");
        }

        // 限制 page
        if (page < 1) {
            page = 1;
        }

        // 限制 size
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10 , 2)) {
            throw new BusinessException("获取团队日志操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getStatus, 1)
                        .eq(WorkspaceMember::getUserId, loginId)
        );

        if ((member == null || member.getRole() == 2) && userService.getById(loginId).getRole() != 2) {
            throw new BusinessException("您无权查找该团队的日志");
        }

        Page<WorkspaceOperationLog> workspaceOperationLogPage = new Page<>(page, size);
        Page<WorkspaceOperationLog> resultPage = workspaceOperationLogMapper.selectPage(
                workspaceOperationLogPage,
                new LambdaQueryWrapper<WorkspaceOperationLog>()
                        .eq(WorkspaceOperationLog::getWorkspaceId, workspace.getId())
                        .orderByDesc(WorkspaceOperationLog::getCreateTime)
        );

        return  Result.success(resultPage);
    }
}
