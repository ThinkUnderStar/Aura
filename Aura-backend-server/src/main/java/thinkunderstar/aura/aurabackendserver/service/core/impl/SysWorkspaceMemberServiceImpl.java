package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.SetRoleDto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceMemberVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.*;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceMemberService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.*;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SysWorkspaceMemberServiceImpl implements SysWorkspaceMemberService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberService workspaceMemberService;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserService userService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final KnowledgeBaseService knowledgeBaseService;

    public SysWorkspaceMemberServiceImpl(
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            WorkspaceService workspaceService,
            WorkspaceMemberService workspaceMemberService,
            WorkspaceMemberMapper workspaceMemberMapper, UserService userService, WorkspaceOperationLogService workspaceOperationLogService, KnowledgeBaseService knowledgeBaseService) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.workspaceService = workspaceService;
        this.workspaceMemberService = workspaceMemberService;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userService = userService;
        this.workspaceOperationLogService = workspaceOperationLogService;
        this.knowledgeBaseService = knowledgeBaseService;
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
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getStatus, 1)
                        .eq(WorkspaceMember::getUserId, loginId)
        );

        if (member == null && userService.getById(loginId).getRole() != 2) {
            throw new BusinessException("您无权获取该团队的所有成员信息");
        }

        //分页查询并封装VO对象返回
        Page<WorkspaceMember> pageMember = new Page<>(page, size);

        Page<WorkspaceMember> workspaceMemberPage = workspaceMemberMapper.selectPage(
                pageMember,
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getStatus, 1)
                        .orderByAsc(WorkspaceMember::getRole)
                        .orderByAsc(WorkspaceMember::getJoinedAt)
        );

        List<WorkspaceMemberVODto> newRecords = workspaceMemberPage.getRecords()
                .stream()
                .map(workspaceMember -> {
                    WorkspaceMemberVODto workspaceMemberVODto = new WorkspaceMemberVODto();
                    BeanUtils.copyProperties(workspaceMember, workspaceMemberVODto);
                    wrapMemberVO(workspaceMember, workspaceMemberVODto);

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

        if (workspace == null || workspace.getStatus() != 1) {
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

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
        operationLog.setWorkspaceId(workspace.getId());
        operationLog.setUserId(loginId);
        operationLog.setUsername(username);
        operationLog.setModule("member");
        operationLog.setOperation("create");
        operationLog.setRequestSummary(
                "用户: "
                + username
                +" 加入了该团队"
        );
        operationLog.setStatus(1);
        workspaceOperationLogService.save(operationLog);

        WorkspaceVODto workspaceVODto = new WorkspaceVODto();
        BeanUtils.copyProperties(workspace, workspaceVODto);

        workspaceVODto.setRole(member.getRole());
        workspaceVODto.setMemberStatus(member.getStatus());

        return Result.success(workspaceVODto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> quitWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException("退出团队接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("退出团队操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        // 放行正常(1)与封禁(2)的团队，已解散(0)的团队走清除记录接口
        if (workspace == null || workspace.getStatus() == 0) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, loginId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (member == null){
            throw new BusinessException("您不在该团队中");
        }

        if (member.getRole() == 0){
            throw new BusinessException("请转让创建者身份后，再退出该团队");
        }

        workspaceMemberService.removeById(member.getId());

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
        operationLog.setWorkspaceId(workspace.getId());
        operationLog.setUserId(loginId);
        operationLog.setUsername(username);
        operationLog.setModule("member");
        operationLog.setOperation("delete");
        operationLog.setRequestSummary(
                "用户: "
                        + username
                        + " 退出了该团队"
        );
        operationLog.setStatus(1);
        workspaceOperationLogService.save(operationLog);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> removeMember(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            throw new BusinessException("移除团队成员接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("移除团队成员过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, loginId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (member == null || member.getRole() == 2){
            throw new BusinessException("你无权移除该团队的成员");
        }

        WorkspaceMember targetMember = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, userId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (targetMember == null){
            throw new BusinessException("团队中不存在该成员");
        }

        if (Objects.equals(member.getUserId(), targetMember.getUserId())){
            throw new BusinessException("不能移除自己");
        }

        if (member.getRole() == 1 && targetMember.getRole() != 2){
            throw new BusinessException("您无权限移除该成员");
        }

        workspaceMemberService.removeById(targetMember.getId());

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
        operationLog.setWorkspaceId(workspaceId);
        operationLog.setUserId(loginId);
        operationLog.setUsername(username);
        operationLog.setModule("member");
        operationLog.setOperation("delete");
        operationLog.setRequestSummary(
                "管理员: "
                        + username
                        + " 将: "
                        + userService.getById(targetMember.getUserId()).getUsername()
                        + " 移除了团队"
        );
        operationLog.setStatus(1);
        workspaceOperationLogService.save(operationLog);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WorkspaceMemberVODto> setRole(SetRoleDto setRoleDto) {
        if (
                setRoleDto == null
                        || setRoleDto.getWorkspaceId() == null
                        || setRoleDto.getSetRole() == null
                        || setRoleDto.getMemberId() == null
        ) {
            throw new BusinessException("设置团队成员身份接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("设置身份过于频繁请稍后再试");
        }

        Workspace workspace = workspaceService.getById(setRoleDto.getWorkspaceId());

        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, loginId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (member == null || member.getRole() != 0){
            throw new BusinessException("您没有权限执行该操作");
        }

        WorkspaceMember targetMember = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getUserId, setRoleDto.getMemberId())
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (targetMember == null){
            throw new BusinessException("团队中并没有该成员");
        }

        if (targetMember.getRole() == 0){
            throw new BusinessException("不能将群主降级为管理员");
        }

        String username = userService.getById(loginId).getUsername();
        User targetUser = userService.getById(targetMember.getUserId());
        String targetUsername = targetUser.getUsername();
        //添加一条日志
        WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
        operationLog.setWorkspaceId(workspace.getId());
        operationLog.setUserId(loginId);
        operationLog.setUsername(username);
        operationLog.setModule("member");
        operationLog.setOperation("update");

        if (setRoleDto.getSetRole() == 1){ //设置管理员

            if (Objects.equals(targetMember.getRole(), setRoleDto.getSetRole())){
                throw new BusinessException("该团队成员已是管理员，请无重复设置");
            }

            operationLog.setRequestSummary(
                    "团队创建者: "
                    + username
                    +" 将 "
                    + targetUsername
                    +" 设为管理员"
            );
            operationLog.setStatus(1);
            workspaceOperationLogService.save(operationLog);

            targetMember.setRole(setRoleDto.getSetRole());
            workspaceMemberService.updateById(targetMember);

        } else if (setRoleDto.getSetRole() == 2) { //取消管理员

            if (Objects.equals(targetMember.getRole(), setRoleDto.getSetRole())){
                throw new BusinessException("该团队成员本就不是管理员，请无重复设置");
            }

            operationLog.setRequestSummary(
                    "团队创建者: "
                            + username
                            +" 将 "
                            + targetUsername
                            +" 设置为普通用户"
            );
            operationLog.setStatus(1);
            workspaceOperationLogService.save(operationLog);

            targetMember.setRole(setRoleDto.getSetRole());
            workspaceMemberService.updateById(targetMember);

        } else { //处理异常参数
            throw new BusinessException("管控管理员身份接口的身份参数异常");
        }

        WorkspaceMemberVODto workspaceMemberVODto = new WorkspaceMemberVODto();
        BeanUtils.copyProperties(targetMember , workspaceMemberVODto);

        workspaceMemberVODto.setUsername(targetUsername);
        workspaceMemberVODto.setAvatar(targetUser.getAvatar());

        wrapMemberVO(targetMember, workspaceMemberVODto);

        return Result.success(workspaceMemberVODto);
    }

    private void wrapMemberVO(WorkspaceMember member, WorkspaceMemberVODto workspaceMemberVODto) {
        switch (member.getRole()) {
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

        switch (member.getStatus()) {
            case 0:
                workspaceMemberVODto.setStatusName("已移除");
                break;

            case 1:
                workspaceMemberVODto.setStatusName("正常");
                break;

            default:
                throw new BusinessException("团队成员状态异常");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WorkspaceMemberVODto> transferOwnership(Long workspaceId, Long targetUserId) {
        if (workspaceId == null || targetUserId == null) {
            throw new BusinessException("转让创建者身份接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("转移创建者身份过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, loginId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (member == null || member.getRole() != 0){
            throw new BusinessException("您无权执行该操作");
        }

        WorkspaceMember targetMember = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, targetUserId)
                        .eq(WorkspaceMember::getStatus, 1)
        );

        if (targetMember == null){
            throw new BusinessException("该用户并不在团队之中");
        }

        if (Objects.equals(loginId, targetUserId)){
            throw new BusinessException("转让团队创建者身份的目标不能是你自己");
        }

        //转让团队创建者身份业务
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(workspace.getKbId());
        if (knowledgeBase != null){
            knowledgeBase.setOwnerId(targetUserId);
            knowledgeBaseService.updateById(knowledgeBase);
        }
        workspace.setOwnerId(targetUserId);
        workspaceService.updateById(workspace);
        member.setRole(2);
        targetMember.setRole(0);
        workspaceMemberService.updateById(targetMember);
        workspaceMemberService.updateById(member);

        String username = userService.getById(loginId).getUsername();
        User targetUser = userService.getById(targetUserId);
        String targetUsername = targetUser.getUsername();
        //添加一条日志
        WorkspaceOperationLog operationLog = new WorkspaceOperationLog();
        operationLog.setWorkspaceId(workspace.getId());
        operationLog.setUserId(loginId);
        operationLog.setUsername(username);
        operationLog.setModule("member");
        operationLog.setOperation("update");
        operationLog.setStatus(1);
        operationLog.setRequestSummary(
                "用户: "
                + username
                + " 将团队创建者身份转让给用户:  "
                + targetUsername
        );
        workspaceOperationLogService.save(operationLog);

        //封装VO对象并返回给前端
        WorkspaceMemberVODto workspaceMemberVODto = new WorkspaceMemberVODto();
        BeanUtils.copyProperties(targetMember, workspaceMemberVODto);
        workspaceMemberVODto.setUsername(targetUsername);
        workspaceMemberVODto.setAvatar(targetUser.getAvatar());
        wrapMemberVO(targetMember, workspaceMemberVODto);

        return Result.success(workspaceMemberVODto);
    }
}
