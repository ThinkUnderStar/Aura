package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateWorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.request.WorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceOperationLog;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceOperationLogMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.*;
import thinkunderstar.aura.aurabackendserver.util.CodeUtils;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysWorkspaceServiceImpl implements SysWorkspaceService {
    private final WorkspaceMapper workspaceMapper;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final WorkspaceService workspaceService;
    private final SysKnowledgeBaseService sysKnowledgeBaseService;
    private final WorkspaceMemberService workspaceMemberService;
    private static final long MAX_SIZE = 5*1024*1024;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserService userService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final TransactionTemplate transactionTemplate;
    private final WorkspaceOperationLogMapper workspaceOperationLogMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public SysWorkspaceServiceImpl(
            WorkspaceMapper workspaceMapper,
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            WorkspaceService workspaceService,
            SysKnowledgeBaseService sysKnowledgeBaseService,
            WorkspaceMemberService workspaceMemberService,
            WorkspaceMemberMapper workspaceMemberMapper, UserService userService, WorkspaceOperationLogService workspaceOperationLogService, TransactionTemplate transactionTemplate, WorkspaceOperationLogMapper workspaceOperationLogMapper, KnowledgeBaseService knowledgeBaseService) {
        this.workspaceMapper = workspaceMapper;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.workspaceService = workspaceService;
        this.sysKnowledgeBaseService = sysKnowledgeBaseService;
        this.workspaceMemberService = workspaceMemberService;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userService = userService;
        this.workspaceOperationLogService = workspaceOperationLogService;
        this.transactionTemplate = transactionTemplate;
        this.workspaceOperationLogMapper = workspaceOperationLogMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public Result<IPage<WorkspaceVODto>> getMyWorkspaces(int page, int size) {
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

        Page<WorkspaceVODto> pageVO = new Page<>(page, size);
        IPage<WorkspaceVODto> workspaceVODtoIPage = workspaceMapper.selectUserWorkspaces(pageVO, loginId);

        return Result.success(workspaceVODtoIPage);
    }

    @Override
    @Transactional(rollbackFor = BusinessException.class)
    public Result<WorkspaceVODto> createWorkspace(WorkspaceDto workspaceDto) {
        if (workspaceDto == null) {
            throw new BusinessException("创建团队的接口参数接收异常");
        }

        if (workspaceDto.getName() == null || workspaceDto.getName().isEmpty()) {
            throw new BusinessException("团队名不能为空");
        }

        if (workspaceDto.getKbName() == null || workspaceDto.getKbName().isEmpty()) {
            throw new BusinessException("团队知识库名不能为空");
        }

        if (workspaceDto.getKbDescription() == null || workspaceDto.getKbDescription().isEmpty()) {
            throw new BusinessException("团队知识库描述不能为空");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("创建团队操作过于频繁，请稍后再试");
        }

        Workspace workspace = new Workspace(
                workspaceDto.getName(),
                workspaceDto.getDescription(),
                loginId
        );

        String inviteCode = CodeUtils.generateInviteCode();
        workspace.setInviteCode(inviteCode);

        workspaceService.save(workspace);

        //添加团队日志
        String username = userService.getById(loginId).getUsername();

        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspace.getId());
        workspaceOperationLog.setUsername(username);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setModule("workspace");
        workspaceOperationLog.setOperation("create");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLog.setRequestSummary(
                "用户: "
                        + username
                        + " 创建了团队: "
                        + workspaceDto.getName()
                        + " 并初始化了团队知识库"
        );
        workspaceOperationLogService.save(workspaceOperationLog);

        WorkspaceVODto workspaceVODto = new WorkspaceVODto(
                loginId,
                0,
                workspace.getName(),
                workspace.getDescription(),
                workspace.getLogo(),
                workspace.getInviteCode(),
                1,
                workspace.getCreateTime()
        );

        //添加创建者member
        WorkspaceMember workspaceMember = new WorkspaceMember(
                workspace.getId(),
                loginId,
                0
        );
        workspaceMemberService.save(workspaceMember);

        //创建团队知识库业务逻辑
        CreateKnowledgeBaseDto createKnowledgeBaseDto = new CreateKnowledgeBaseDto();
        createKnowledgeBaseDto.setName(workspaceDto.getKbName());
        createKnowledgeBaseDto.setDescription(workspaceDto.getKbDescription());
        createKnowledgeBaseDto.setIsTeam(1);
        Result<KnowledgeBase> result = sysKnowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDto);

        if (result.getCode() != 200){
            throw new BusinessException(result.getMsg());
        }

        //绑定团队知识库(考虑到事务回滚无法回滚Milvus数据库的创建操作，将Milvus数据库的创建放在了最后)
        workspace.setKbId(result.getData().getId());
        workspaceService.updateById(workspace);
        workspaceOperationLog.setRequestSummary(
                workspaceOperationLog.getRequestSummary()
                +"知识库ID为: "
                + workspace.getKbId()
        );

        return Result.success(workspaceVODto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> logo(Long workspaceId, MultipartFile file) {
        if (workspaceId == null || file == null) {
            throw new BusinessException("团队logo设置接口参数接收异常");
        }

        if (file.isEmpty()){
            throw new BusinessException("团队头像文件不能为空");
        }

        if (file.getSize() > MAX_SIZE ) {
            throw new BusinessException("上传的团队头像文件过大");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException("团队头像文件名不能为空");
        }

        if (!fileName.contains(".")) {
            throw new BusinessException("团队头像文件缺少扩展名");
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        if(!List.of("jpg","png","jpeg","webp").contains(ext)){
            throw new BusinessException("团队头像文件格式只支持:\".jpg\",\".png\",\".jpeg\",\".webp\"");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        //令牌桶算法，按用户限流
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("修改logo过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("该团队不存在");
        }

        List<Long> adminIdList = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                                .eq(WorkspaceMember::getStatus,1)
                                .in(WorkspaceMember::getRole, Arrays.asList(0, 1))
                ).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        if (!adminIdList.contains(loginId)) {
            throw new BusinessException("你没有权限修改该团队的logo");
        }

        if (!(workspace.getLogo() == null ||workspace.getLogo().isEmpty())) {
            String oldLogo = "./docs"+workspace.getLogo();
            try {
                Files.deleteIfExists(Path.of(oldLogo));
            } catch (IOException e) {
                log.warn("团队:"+ workspaceId +"的旧logo文件删除失败");
            }
        }

        String logo = "/workspace_logos/"+workspaceId+"-"+System.currentTimeMillis()+"-aura"+ext;
        try {
            file.transferTo(Path.of("./docs"+logo).toFile());
        } catch (IOException e) {
            log.error("团队:"+workspaceId+"的logo文件上传失败");
            throw new BusinessException("团队logo文件上传失败");
        }

        workspace.setLogo(logo);
        workspaceService.updateById(workspace);

        //添加一条团队日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspaceId);
        workspaceOperationLog.setUsername(username);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setModule("logo");
        workspaceOperationLog.setOperation("update");
        workspaceOperationLog.setRequestSummary("用户: "+username+" 更新了团队头像");
        workspaceOperationLog.setStatus(1);

        workspaceOperationLogService.save(workspaceOperationLog);

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<WorkspaceVODto> updateWorkspace(UpdateWorkspaceDto updateWorkspaceDto) {
        //对接收参数的检查
        if (
                updateWorkspaceDto == null
                || updateWorkspaceDto.getWorkspaceId() == null
                || updateWorkspaceDto.getType() == null
        ) {
            throw new BusinessException("修改团队信息接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        //令牌桶算法，基于用户的限流
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("修改团队信息操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(updateWorkspaceDto.getWorkspaceId());

        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("该团队不存在");
        }

        List<Long> adminIdList = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                                .eq(WorkspaceMember::getStatus,1)
                                .in(WorkspaceMember::getRole, Arrays.asList(0, 1))
                ).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        if (!adminIdList.contains(loginId)) {
            throw new BusinessException("您没有权限修改该团队的基本信息");
        }

        //添加团队日志
        String username = userService.getById(loginId).getUsername();

        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspace.getId());
        workspaceOperationLog.setModule("workspace");
        workspaceOperationLog.setOperation("update");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setUsername(username);

        if (updateWorkspaceDto.getType().equals("name")){
            String requestSummary = "用户: "
                    + username
                    + " 将团队的名字从 "
                    + workspace.getName()
                    + " 改为: "
                    + updateWorkspaceDto.getName();
            workspaceOperationLog.setRequestSummary(requestSummary);
            workspaceOperationLogService.save(workspaceOperationLog);
            return updateWorkspaceName(workspace,updateWorkspaceDto.getName());
        } else if (updateWorkspaceDto.getType().equals("description")) {
            String requestSummary = workspace.getDescription() == null
                    ?
                    "用户: "
                    + username
                    + " 将团队的描述改为: "
                    + updateWorkspaceDto.getDescription()
                    :
                    "用户: "
                    + username
                    + " 将团队的描述从 "
                    + workspace.getDescription()
                    + " 改为: "
                    + updateWorkspaceDto.getDescription();
            workspaceOperationLog.setRequestSummary(requestSummary);
            workspaceOperationLogService.save(workspaceOperationLog);
            return updateWorkspaceDescription(workspace,updateWorkspaceDto.getDescription());
        }else {
            throw new BusinessException("修改团队信息的接口中type参数不符合规范");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<List<WorkspaceOperationLog>> deleteWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException("解散团队接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("解散团队操作过去频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);

        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        if (workspace.getOwnerId() != loginId) {
            throw new BusinessException("您无权解散该团队");
        }

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspace.getId());
        workspaceOperationLog.setUsername(username);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setModule("workspace");
        workspaceOperationLog.setOperation("delete");
        workspaceOperationLog.setRequestSummary("用户: "+username+" 解散了团队");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLogService.save(workspaceOperationLog);

        //获取全部日志
        List<WorkspaceOperationLog> workspaceOperationLogs = workspaceOperationLogMapper.selectList(
                new LambdaQueryWrapper<WorkspaceOperationLog>()
                        .eq(WorkspaceOperationLog::getWorkspaceId, workspace.getId())
                        .orderByDesc(WorkspaceOperationLog::getCreateTime)
        );

        //解散团队业务代码
        workspace.setStatus(0);
        //删除绑定的知识库
        Long kbId = workspace.getKbId();
        if (kbId != null) {
            sysKnowledgeBaseService.forceDeleteKnowledgeBase(Math.toIntExact(kbId));
            workspace.setKbId(null);
        }
        workspaceService.updateById(workspace);
        //删除创建者的member信息
        WorkspaceMember one = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMember::getRole,0)
        );
        workspaceMemberService.removeById(one.getId());

        return Result.success(workspaceOperationLogs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cleanWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException("清除团队信息接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("用户清除操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);

        if (workspace == null) {
            throw new BusinessException("未查询到该团队");
        }

        WorkspaceMember member = workspaceMemberService.getOne(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMember::getUserId, loginId)
        );

        if (member == null){
            throw new BusinessException("你未加入该团队");
        }

        if (workspace.getStatus() == 0 || member.getStatus() != 1){
            workspaceMemberService.removeById(member.getId());
        }else {
            throw new BusinessException("请调用退队接口");
        }

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> resetInviteCode(Long workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException("更新邀请码接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("更新邀请码操作过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        List<Long> adminIds = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getWorkspaceId, workspace.getId())
                                .eq(WorkspaceMember::getStatus, 1)
                                .in(WorkspaceMember::getRole, Arrays.asList(0, 1))
                ).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        if (!adminIds.contains(loginId)) {
            throw new BusinessException("您没有权限更新邀请码");
        }

        //生成新的邀请码
        String newInviteCode = CodeUtils.generateInviteCode();
        workspace.setInviteCode(newInviteCode);
        workspaceService.updateById(workspace);

        return Result.success(newInviteCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<List<WorkspaceOperationLog>> banWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            throw new BusinessException("封禁团队接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("封禁团队过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspace.getId());
        workspaceOperationLog.setUsername(username);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setModule("workspace");
        workspaceOperationLog.setOperation("delete");
        workspaceOperationLog.setRequestSummary("管理员: "+username+" 封禁了该团队");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLogService.save(workspaceOperationLog);

        //获取全部日志
        List<WorkspaceOperationLog> workspaceOperationLogs = workspaceOperationLogMapper.selectList(
                new LambdaQueryWrapper<WorkspaceOperationLog>()
                        .eq(WorkspaceOperationLog::getWorkspaceId, workspace.getId())
                        .orderByDesc(WorkspaceOperationLog::getCreateTime)
        );

        //封禁团队业务代码
        workspace.setStatus(2);
        //删除绑定的知识库
        Long kbId = workspace.getKbId();
        if (kbId != null) {
            sysKnowledgeBaseService.logicDeleteKnowledgeBase(Math.toIntExact(kbId));
            workspace.setKbId(null);
        }
        workspaceService.updateById(workspace);

        return Result.success(workspaceOperationLogs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unbanWorkspace(Long workspaceId, Long kbId) {
        if (workspaceId == null || kbId == null) {
            throw new BusinessException("解封团队接口参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("解封团队过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() == 0) {
            throw new BusinessException("未查询到该团队");
        }

        if (workspace.getStatus() == 1){
            throw new BusinessException("该团队未被封禁");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(kbId);
        if (knowledgeBase == null) {
            throw new BusinessException("该知识库已被彻底清除");
        }

        sysKnowledgeBaseService.restoreKnowledgeBase(Math.toIntExact(kbId));
        workspace.setKbId(kbId);
        workspace.setStatus(1);
        workspaceService.updateById(workspace);

        //添加一条日志
        String username = userService.getById(loginId).getUsername();
        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspace.getId());
        workspaceOperationLog.setUsername(username);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setModule("workspace");
        workspaceOperationLog.setOperation("update");
        workspaceOperationLog.setRequestSummary("管理员: "+username+" 解封了该团队");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLogService.save(workspaceOperationLog);

        return Result.success();
    }

    @Override
    public Result<IPage<Workspace>> getAllWorkspaces(Long page, Long size) {
        if (page == null || size == null) {
            throw new BusinessException("获取所有团队信息接口的参数接收异常");
        }

        // 限制 page
        if (page < 1) {
            page = 1L;
        }

        // 限制 size
        if (size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取所有团队信息接口调用过于频繁，请稍后再试");
        }

        Page<Workspace> workspacePage = new Page<>(page, size);
        Page<Workspace> resultPage = workspaceMapper.selectPage(
                workspacePage,
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getStatus, 1)
                        .orderByDesc(Workspace::getCreateTime)
        );

        return Result.success(resultPage);
    }

    @Override
    public Result<IPage<WorkspaceVODto>> searchMyWorkspaces(String keyWord, Long page, Long size) {
        if (page == null || size == null || keyWord == null) {
            throw new BusinessException("搜索相关团队接口的参数接收异常");
        }

        if (page < 1) {
            page = 1L;
        }

        if (size < 1) {
            size = 20L;
        }

        if (size > 100) {
            size = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("搜索相关团队过于频繁，请稍后再试");
        }

        Page<WorkspaceVODto> resultPage = new Page<>(page, size);
        IPage<WorkspaceVODto> workspaceVODtoIPage = workspaceMapper.searchUserWorkspacesByKeyword(resultPage, loginId, keyWord);

        return Result.success(workspaceVODtoIPage);
    }

    @Override
    public Result<Page<Workspace>> searchAllMyWorkspaces(String keyWord, Long page, Long size) {
        if (page == null || size == null || keyWord == null) {
            throw new BusinessException("搜索所有相关团队接口的参数接收异常");
        }

        if (page < 1) {
            page = 1L;
        }

        if (size < 1) {
            size = 20L;
        }

        if (size > 100) {
            size = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("搜索所有相关团队过于频繁，请稍后再试");
        }

        Page<Workspace>  workspacePage = new Page<>(page, size);
        Page<Workspace> resultPage = workspaceMapper.selectPage(
                workspacePage,
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getStatus, 1)
                        .like(Workspace::getName, keyWord)
                        .orderByDesc(Workspace::getCreateTime)
        );

        return Result.success(resultPage);
    }

    private Result<WorkspaceVODto> updateWorkspaceDescription(Workspace workspace, String description) {
        Integer role = StpUtil.getLoginIdAsLong() == workspace.getOwnerId() ? 0 : 1;
        WorkspaceVODto workspaceVODto = new WorkspaceVODto();

        if (
                (description == null && workspace.getDescription() == null)
                        || (
                                workspace.getDescription() != null
                                        && description != null
                                        && description.equals(workspace.getDescription())
                )
        ){
            workspaceVODto.setName(workspace.getName());
            workspaceVODto.setRole(role);
            workspaceVODto.setId(workspace.getId());
            workspaceVODto.setDescription(description);
            workspaceVODto.setLogo(workspace.getLogo());
            workspaceVODto.setInviteCode(workspace.getInviteCode());
            workspaceVODto.setStatus(1);
            workspaceVODto.setCreateTime(workspace.getCreateTime());

            return Result.success(workspaceVODto);
        }

        workspace.setDescription(description);
        workspaceService.updateById(workspace);

        workspaceVODto.setName(workspace.getName());
        workspaceVODto.setRole(role);
        workspaceVODto.setId(workspace.getId());
        workspaceVODto.setDescription(description);
        workspaceVODto.setLogo(workspace.getLogo());
        workspaceVODto.setInviteCode(workspace.getInviteCode());
        workspaceVODto.setStatus(1);
        workspaceVODto.setCreateTime(workspace.getCreateTime());

        return Result.success(workspaceVODto);
    }

    private Result<WorkspaceVODto> updateWorkspaceName(Workspace workspace, String name) {
        if (name == null || name.isEmpty()) {
            throw new BusinessException("团队名不能为空");
        }

        Integer role = StpUtil.getLoginIdAsLong() == workspace.getOwnerId() ? 0 : 1;
        WorkspaceVODto workspaceVODto = new WorkspaceVODto();

        if (name.equals(workspace.getName())) {
            workspaceVODto.setName(name);
            workspaceVODto.setRole(role);
            workspaceVODto.setId(workspace.getId());
            workspaceVODto.setDescription(workspace.getDescription());
            workspaceVODto.setLogo(workspace.getLogo());
            workspaceVODto.setInviteCode(workspace.getInviteCode());
            workspaceVODto.setStatus(1);
            workspaceVODto.setCreateTime(workspace.getCreateTime());

            return Result.success(workspaceVODto);
        }

        workspace.setName(name);
        workspaceService.updateById(workspace);

        workspaceVODto.setName(name);
        workspaceVODto.setRole(role);
        workspaceVODto.setId(workspace.getId());
        workspaceVODto.setDescription(workspace.getDescription());
        workspaceVODto.setLogo(workspace.getLogo());
        workspaceVODto.setInviteCode(workspace.getInviteCode());
        workspaceVODto.setStatus(1);
        workspaceVODto.setCreateTime(workspace.getCreateTime());

        return Result.success(workspaceVODto);
    }

    /**
     * 定时清除没有任何成员遗留的团队信息
     */
    @Scheduled(cron = "0 * * * * ?")
    public void cleanDeletedWorkspaceInformation(){
        workspaceMapper.selectList(
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getStatus, 0)
                        .or()
                        .eq(Workspace::getStatus, 2)
        ).stream()
                .map(Workspace::getId)
                .forEach(workspaceId -> {
                    Long count = workspaceMemberMapper.selectCount(
                            new LambdaQueryWrapper<WorkspaceMember>()
                                    .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                    );

                    if (count == 0) {
                        Workspace workspace = workspaceService.getById(workspaceId);
                        //删除团队的logo图片
                        if (workspace.getLogo() != null && !workspace.getLogo().isEmpty()) {
                            String oldLogo = "./docs"+workspace.getLogo();
                            try {
                                Files.deleteIfExists(Path.of(oldLogo));
                            } catch (IOException e) {
                                log.warn("团队:"+ workspaceId +"的旧logo文件删除失败");
                            }
                        }

                        try {
                            transactionTemplate.execute(status -> {
                                workspaceService.removeById(workspaceId);
                                return null;
                            });
                        } catch (Exception e) {
                            log.error("已被解散的团队: " + workspaceId + " 的驻留信息删除失败");
                        }
                    }
                });
    }
}
