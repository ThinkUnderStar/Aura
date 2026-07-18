package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceMemberVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.User;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceMemberService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceMemberService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysWorkspaceMemberServiceImpl implements SysWorkspaceMemberService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService workspaceMemberService;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserService userService;

    public SysWorkspaceMemberServiceImpl(
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            WorkspaceService workspaceService,
            WorkspaceMemberService workspaceMemberService,
            WorkspaceMemberMapper workspaceMemberMapper, UserService userService) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.workspaceService = workspaceService;
        this.workspaceMemberService = workspaceMemberService;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userService = userService;
    }

    @Override
    public Result<Page<WorkspaceMemberVODto>> getWorkspaceMembers(Long workspaceId, Integer page, Integer size) {
        if (page == null || size == null || workspaceId == null) {
            throw new BusinessException("获取团队所有成员接口的参数接收异常");
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

        Long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取团队成员操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() == 0) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getStatus, 1)
                        .eq(WorkspaceMember::getUserId, loginId)
        );

        if (member == null) {
            throw new BusinessException("您无权获取该团队的所有成员信息");
        }

        //分页查询并封装VO对象返回
        Page<WorkspaceMember> pageMember = new Page<>(page, size);

        Page<WorkspaceMember> workspaceMemberPage = workspaceMemberMapper.selectPage(
                pageMember,
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getStatus, 1)
        );

        List<WorkspaceMemberVODto> newRecords = workspaceMemberPage.getRecords()
                .stream()
                .map(workspaceMember -> {
                    WorkspaceMemberVODto workspaceMemberVODto = new WorkspaceMemberVODto();
                    BeanUtils.copyProperties(workspaceMember, workspaceMemberVODto);
                    switch (workspaceMember.getRole()) {
                        case 0:
                            workspaceMemberVODto.setRoleName("创建者");
                            break;

                        case 1:
                            workspaceMemberVODto.setRoleName("管理员");
                            break;

                        case 2:
                            workspaceMemberVODto.setRoleName("普通成员");
                            break;

                        default:
                            throw new BusinessException("团队成员身份信息异常");
                    }

                    switch (workspaceMember.getStatus()) {
                        case 0:
                            workspaceMemberVODto.setStatusName("已移除");
                            break;

                        case 1:
                            workspaceMemberVODto.setStatusName("正常");
                            break;

                        default:
                            throw new BusinessException("团队成员状态异常");
                    }

                    User user = userService.getById(workspaceMember.getUserId());
                    if (user == null) {
                        workspaceMemberVODto.setUsername("已注销");
                        workspaceMemberVODto.setAvatar(null);
                    }else {
                        workspaceMemberVODto.setUsername(user.getUsername());
                        workspaceMemberVODto.setAvatar(user.getAvatar());
                    }

                    return workspaceMemberVODto;
                }).collect(Collectors.toList());

        Page<WorkspaceMemberVODto> workspaceMemberVODtoPage = new Page<>();
        workspaceMemberVODtoPage.setRecords(newRecords);
        workspaceMemberVODtoPage.setTotal(pageMember.getTotal());
        workspaceMemberVODtoPage.setSize(size);
        workspaceMemberVODtoPage.setCurrent(page);

        return Result.success(workspaceMemberVODtoPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WorkspaceVODto> joinWorkspace(String inviteCode) {
        if (inviteCode == null) {
            throw new BusinessException("加入团队接口的参数接收异常");
        }

        inviteCode = inviteCode.trim();

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("加入团队过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getOne(
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getInviteCode, inviteCode)
        );

        if (workspace == null || workspace.getStatus() == 0) {
            throw new BusinessException("邀请码失效");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, loginId)
        );

        if (member == null) {
            WorkspaceMember workspaceMember = new WorkspaceMember(
                    workspace.getId(),
                    loginId,
                    2
            );

            workspaceMemberService.save(workspaceMember);
            member = workspaceMember;
        }else {
            if (member.getStatus() == 1) {
                throw new BusinessException("您已在该团队中");
            }else{
                member.setJoinedAt(LocalDateTime.now());
                member.setStatus(1);
                member.setRole(2);
                workspaceMemberService.updateById(member);
            }
        }

        WorkspaceVODto workspaceVODto = new WorkspaceVODto();
        BeanUtils.copyProperties(workspace, workspaceVODto);

        workspaceVODto.setRole(member.getRole());
        workspaceVODto.setMemberStatus(member.getStatus());

        return Result.success(workspaceVODto);
    }
}
