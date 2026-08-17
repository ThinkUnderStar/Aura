package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.entity.*;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.AgentKbBindingMapper;
import thinkunderstar.aura.aurabackendserver.mapper.KnowledgeBaseMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.*;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysKnowledgeBaseServiceImpl implements SysKnowledgeBaseService {
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final AgentKbBindingMapper agentKbBindingMapper;
    private final TransactionTemplate transactionTemplate;
    private final UserService userService;
    private final WorkspaceOperationLogService workspaceOperationLogService;
    private final WorkspaceMemberService workspaceMemberService;
    private final WebClient webClient;
    private final SensitiveWordManager sensitiveWordManager;

    public SysKnowledgeBaseServiceImpl(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeBaseMapper knowledgeBaseMapper,
            WorkspaceService workspaceService,
            WorkspaceMemberMapper workspaceMemberMapper,
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            AgentKbBindingMapper agentKbBindingMapper,
            TransactionTemplate transactionTemplate, UserService userService, WorkspaceOperationLogService workspaceOperationLogService, WorkspaceMemberService workspaceMemberService, WebClient webClient, SensitiveWordManager sensitiveWordManager) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.workspaceService = workspaceService;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.agentKbBindingMapper = agentKbBindingMapper;
        this.transactionTemplate = transactionTemplate;
        this.userService = userService;
        this.workspaceOperationLogService = workspaceOperationLogService;
        this.workspaceMemberService = workspaceMemberService;
        this.webClient = webClient;
        this.sensitiveWordManager = sensitiveWordManager;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> createKnowledgeBase(CreateKnowledgeBaseDto createKnowledgeBaseDto) {
        if (
                createKnowledgeBaseDto == null
                || createKnowledgeBaseDto.getName() == null
                || createKnowledgeBaseDto.getDescription() == null
                || createKnowledgeBaseDto.getDescription().isEmpty()
                || createKnowledgeBaseDto.getName().isEmpty()
        ) {
            throw new BusinessException("知识库名和描述不能为空");
        }

        if (createKnowledgeBaseDto.getIsTeam() == null){
            throw new BusinessException("知识库是否从属团队参数失效");
        }

        if (sensitiveWordManager.checkSensitiveWord(createKnowledgeBaseDto.getName())) {
            throw new BusinessException("知识库名中包含敏感词");
        }

        if (sensitiveWordManager.checkSensitiveWord(createKnowledgeBaseDto.getDescription())) {
            throw new BusinessException("知识库描述中包含敏感词");
        }

        long loginId = StpUtil.getLoginIdAsLong();

        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("创建数据库过于频繁，请稍后再试");
        }

        KnowledgeBase knowledgeBase = new KnowledgeBase(
                loginId,
                createKnowledgeBaseDto.getIsTeam(),
                createKnowledgeBaseDto.getName(),
                createKnowledgeBaseDto.getDescription()
        );
        knowledgeBaseService.save(knowledgeBase);

        //调用python接口创建milvus向量数据库
        Result result = webClient.post()
                .uri(uriBuilder ->
                        uriBuilder
                                .path("/api/v1/kb/create/{kb_name}")
                                .build(knowledgeBase.getCollectionName())
                ).retrieve()
                .bodyToMono(Result.class)
                .block();

        if (result == null || result.getCode() != 200) {
            throw new BusinessException("创建知识库失败");
        }

        return Result.success(knowledgeBase);
    }

    @Override
    public Result<Page<KnowledgeBase>> getMyKnowledgeBases(Integer page, Integer pageSize) {
        // 限制 page
        if (page == null || page < 1) {
            page = 1;
        }

        // 限制 size
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        Page<KnowledgeBase> knowledgeBasePage = new Page<>(page, pageSize);

        long loginId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<KnowledgeBase> queryWrapper
                = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getOwnerId,loginId)
                .eq(KnowledgeBase::getIsTeam, 0)
                .orderByDesc(KnowledgeBase::getUpdateTime);

        Page<KnowledgeBase> result = knowledgeBaseMapper.selectPage(knowledgeBasePage, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<KnowledgeBase> getKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new BusinessException("获取指定知识库信息接口参数接收异常");
        }

        //限流
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取知识库信息过于频繁，请稍后再试");
        }

        //鉴权
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(knowledgeBaseId);
        User user = userService.getById(loginId);

        if (user.getRole() != 2) {
            if (knowledgeBase == null) {
                throw new BusinessException("您无权查看该知识库的信息");
            }

            if (knowledgeBase.getIsTeam() != 1) {
                //私人知识库鉴权
                if ( knowledgeBase.getOwnerId() != loginId) {
                    throw new BusinessException("您无权查看该知识库的信息");
                }

            }else {
                //团队知识库鉴权
                Workspace workspace = workspaceService.getOne(
                        new LambdaQueryWrapper<Workspace>()
                                .eq(Workspace::getKbId, knowledgeBaseId)
                );

                if (workspace == null) {
                    throw new BusinessException("该团队不存在");
                }

                Long workspaceId = workspace.getId();
                List<Long> memberIds = workspaceMemberMapper.selectList(
                                new LambdaQueryWrapper<WorkspaceMember>()
                                        .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                                        .eq(WorkspaceMember::getStatus,1)
                                        .select(WorkspaceMember::getUserId)
                        ).stream()
                        .map(WorkspaceMember::getUserId)
                        .collect(Collectors.toList());

                if (!memberIds.contains(loginId)) {
                    throw new BusinessException("您无权查看该知识库的信息");
                }
            }

        } else {
            if (knowledgeBase == null){
                throw new BusinessException("该知识库不存在");
            }
        }

        if (knowledgeBase.getStatus() != 1){
            throw new BusinessException("该知识库已经停用");
        }

        return Result.success(knowledgeBase);
    }

    @Override
    public Result<KnowledgeBase> getTeamKnowledgeBases(Long workspaceId) {
        if (workspaceId == null || workspaceId < 1) {
            throw new BusinessException("获取指定团队知识库信息接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取指定团队知识库信息过于频繁，请稍后再试");
        }

        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BusinessException("未查询到该团队");
        }

        //管理员跳过该检查逻辑
        User user = userService.getById(loginId);
        //检查是否有权限
        if ( user.getRole() != 2) {
            WorkspaceMember member = workspaceMemberService.getOne(
                    new LambdaQueryWrapper<WorkspaceMember>()
                            .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                            .eq(WorkspaceMember::getUserId, loginId)
                            .eq(WorkspaceMember::getStatus, 1)
            );

            if (member == null) {
                throw new BusinessException("您没有权限查询该团队的知识库信息");
            }
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(workspace.getKbId());
        if (knowledgeBase == null) {
            throw new BusinessException("该知识库可能已被彻底删除");
        }

        if (knowledgeBase.getIsTeam() == 0) {
            throw new BusinessException("该知识库为私人知识库");
        }

        if (knowledgeBase.getStatus() != 1){
            throw new BusinessException("该知识库已被停用");
        }

        return Result.success(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> updateMyKnowledgeBase(UpdateKnowledgeBaseDto updateKnowledgeBaseDto) {
        if (updateKnowledgeBaseDto == null) {
            throw new BusinessException("知识库修改接口并未正常接收参数");
        }

        if (updateKnowledgeBaseDto.getType() == null){
            throw new BusinessException("知识库修改接口中的修改类型参数为空");
        }

        if (!redisTokenBucketLimiter.tryAcquireByUser(StpUtil.getLoginIdAsString(),10,2)){
            throw new BusinessException("修改过去频繁，请稍后再试");
        }

        //验证知识库是否存在,是否是私人知识库，是否从属于该用户
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(updateKnowledgeBaseDto.getKbId());

        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }

        if (knowledgeBase.getStatus() == 0) {
            throw new BusinessException("该知识库已被停用，无法修改");
        }

        if (knowledgeBase.getIsTeam() == 1) {
            throw new BusinessException("该接口无法修改团队知识库");
        }

        if (knowledgeBase.getOwnerId() != StpUtil.getLoginIdAsLong()) {
            throw new BusinessException("你无权修改该数据库");
        }

        //修改知识库信息的业务代码
        if (updateKnowledgeBaseDto.getType().equals("name")){
            return updateKnowledgeBaseName(updateKnowledgeBaseDto.getName(), knowledgeBase);
        } else if (updateKnowledgeBaseDto.getType().equals("description")) {
            return updateKnowledgeBaseDescription(updateKnowledgeBaseDto.getDescription(), knowledgeBase);
        }else {
            throw new BusinessException("知识库修改接口中的修改类型参数不符合规范");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> updateTeamKnowledgeBase(UpdateKnowledgeBaseDto updateKnowledgeBaseDto) {
        if (updateKnowledgeBaseDto == null) {
            throw new BusinessException("知识库修改接口并未正常接收参数");
        }

        if (updateKnowledgeBaseDto.getType() == null){
            throw new BusinessException("知识库修改接口中的修改类型参数为空");
        }

        if (!redisTokenBucketLimiter.tryAcquireByUser(StpUtil.getLoginIdAsString(),10,2)){
            throw new BusinessException("修改过去频繁，请稍后再试");
        }

        //验证知识库是否存在,是否是团队绑定的知识库，改用户是否有管理员权限
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(updateKnowledgeBaseDto.getKbId());

        if (knowledgeBase == null) {
            throw new BusinessException("知识库不存在");
        }

        if (knowledgeBase.getStatus() == 0) {
            throw new BusinessException("该知识库已被停用，无法修改");
        }

        if (knowledgeBase.getIsTeam() == 0) {
            throw new BusinessException("该接口无法修改私人知识库");
        }

        Workspace workspace = workspaceService.getOne(
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getKbId, knowledgeBase.getId())
        );

        if (workspace == null) {
            throw new BusinessException("该团队知识库绑定的团队不存在");
        }

        Long workspaceId = workspace.getId();
        List<Long> adminIdList = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                                .eq(WorkspaceMember::getStatus,1)
                                .in(WorkspaceMember::getRole, Arrays.asList(0,1))
                                .select(WorkspaceMember::getUserId)
                ).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        long loginId = StpUtil.getLoginIdAsLong();
        if (!adminIdList.contains(loginId)) {
            throw new BusinessException("您没有权限修改该知识库");
        }

        //修改知识库信息的业务代码
        //添加团队日志
        String username = userService.getById(loginId).getUsername();

        WorkspaceOperationLog workspaceOperationLog = new WorkspaceOperationLog();
        workspaceOperationLog.setWorkspaceId(workspaceId);
        workspaceOperationLog.setModule("knowledge_base");
        workspaceOperationLog.setOperation("update");
        workspaceOperationLog.setStatus(1);
        workspaceOperationLog.setUserId(loginId);
        workspaceOperationLog.setUsername(username);

        if (updateKnowledgeBaseDto.getType().equals("name")){
            String requestSummary = "用户: "
                    + username
                    + " 将团队知识库的名字从 "
                    + knowledgeBase.getName()
                    + " 改为: "
                    + updateKnowledgeBaseDto.getName();
            workspaceOperationLog.setRequestSummary(requestSummary);
            workspaceOperationLogService.save(workspaceOperationLog);
            return updateKnowledgeBaseName(updateKnowledgeBaseDto.getName(), knowledgeBase);
        } else if (updateKnowledgeBaseDto.getType().equals("description")) {
            String requestSummary = "用户: "
                    + username
                    + " 将团队知识库的描述从 "
                    + knowledgeBase.getDescription()
                    + " 改为: "
                    + updateKnowledgeBaseDto.getDescription();
            workspaceOperationLog.setRequestSummary(requestSummary);
            workspaceOperationLogService.save(workspaceOperationLog);
            return updateKnowledgeBaseDescription(updateKnowledgeBaseDto.getDescription(), knowledgeBase);
        }else {
            throw new BusinessException("知识库修改接口中的修改类型参数不符合规范");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> logicDeleteKnowledgeBase(Integer id) {
        if (id == null) {
            throw new BusinessException("知识库ID接收失败");
        }

        //用户限速
        if (!redisTokenBucketLimiter.tryAcquireByUser(StpUtil.getLoginIdAsString(),5,1)){
            throw new BusinessException("删除操作过于频繁");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(id);

        if (knowledgeBase == null) {
            throw new BusinessException("未查询到该知识库");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (knowledgeBase.getOwnerId() != loginId && userService.getById(loginId).getRole() != 2) {
            throw new BusinessException("您没有权限删除该知识库");
        }

        //知识库删除逻辑
        knowledgeBase.setStatus(0);
        deleteAgentKbBindings(id);
        knowledgeBaseService.updateById(knowledgeBase);

        return Result.success(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> forceDeleteKnowledgeBase(Integer id) {
        if (id == null) {
            throw new BusinessException("知识库ID接收失败");
        }

        //用户限速
        if (!redisTokenBucketLimiter.tryAcquireByUser(StpUtil.getLoginIdAsString(),5,1)){
            throw new BusinessException("删除操作过于频繁");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(id);

        if (knowledgeBase == null) {
            throw new BusinessException("未查询到该知识库");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (knowledgeBase.getOwnerId() != loginId && userService.getById(loginId).getRole() != 2) {
            throw new BusinessException("您没有权限强制删除该知识库");
        }

        //删除mysql中存储的数据
        knowledgeBaseService.removeById(id);

        //调用python端接口删除
        Result result = webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/kb/delete/{kb_name}")
                        .build(knowledgeBase.getCollectionName())
                ).retrieve()
                .bodyToMono(Result.class)
                .block();

        if (result == null || result.getCode() != 200) {
            throw new BusinessException("删除该知识库失败");
        }

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> restoreKnowledgeBase(Integer id) {
        if (id == null) {
            throw new BusinessException("知识库ID接收失败");
        }

        //用户限速
        if (!redisTokenBucketLimiter.tryAcquireByUser(StpUtil.getLoginIdAsString(),5,1)){
            throw new BusinessException("恢复操作过于频繁");
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(id);

        if (knowledgeBase == null) {
            throw new BusinessException("该知识库可能已被彻底删除");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (knowledgeBase.getOwnerId() != loginId && userService.getById(loginId).getRole() != 2) {
            throw new BusinessException("您没有权限恢复该知识库");
        }

        if (knowledgeBase.getStatus() == 1) {
            throw new BusinessException("该数据库并没有被删除");
        }

        //恢复知识库的业务代码
        knowledgeBase.setStatus(1);
        knowledgeBaseService.updateById(knowledgeBase);

        return Result.success(knowledgeBase);
    }

    @Override
    public Result<Page<KnowledgeBase>> searchMyKnowledgeBase(String keyword, Long page, Long pageSize) {
        if (keyword == null || page == null || pageSize == null) {
            throw new BusinessException("搜索相关知识库接口的参数接收异常");
        }

        // 限制 page
        if (page < 1) {
            page = 1L;
        }

        // 限制 size
        if (pageSize < 1) {
            pageSize = 20L;
        }
        if (pageSize > 100) {
            pageSize = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("搜索相关知识库过于频繁，请稍后再试");
        }

        Page<KnowledgeBase> knowledgeBasePage = new Page<>(page, pageSize);
        Page<KnowledgeBase> resultPage = knowledgeBaseMapper.selectPage(
                knowledgeBasePage,
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getOwnerId, loginId)
                        .eq(KnowledgeBase::getIsTeam,0)
                        .eq(KnowledgeBase::getStatus, 1)
                        .like(KnowledgeBase::getName, keyword)
                        .orderByDesc(KnowledgeBase::getUpdateTime)
        );

        return Result.success(resultPage);
    }

    private Result<KnowledgeBase> updateKnowledgeBaseName(String name , KnowledgeBase knowledgeBase) {
        if (name == null || name.isEmpty()){
            throw new BusinessException("知识库的名字不能为空");
        }

        if (sensitiveWordManager.checkSensitiveWord(name)) {
            throw new BusinessException("知识库名中包含敏感词");
        }

        if (name.equals(knowledgeBase.getName())){
            return Result.success(knowledgeBase);
        }

        knowledgeBase.setName(name);
        knowledgeBaseService.updateById(knowledgeBase);

        return Result.success(knowledgeBase);
    }

    private Result<KnowledgeBase> updateKnowledgeBaseDescription(String description, KnowledgeBase knowledgeBase) {
        if (description == null || description.isEmpty()){
            throw new BusinessException("知识库的描述不能为空");
        }

        if (sensitiveWordManager.checkSensitiveWord(description)) {
            throw new BusinessException("知识库描述中包含敏感词");
        }

        if (description.equals(knowledgeBase.getDescription())){
            return Result.success(knowledgeBase);
        }

        knowledgeBase.setDescription(description);
        knowledgeBaseService.updateById(knowledgeBase);

        return Result.success(knowledgeBase);
    }

    private void deleteAgentKbBindings(Integer kbId){
        List<Long> agentKbList = agentKbBindingMapper.selectList(
                        new LambdaQueryWrapper<AgentKbBinding>()
                                .eq(AgentKbBinding::getKbId, kbId)
                ).stream()
                .map(AgentKbBinding::getId)
                .collect(Collectors.toList());

        agentKbBindingMapper.deleteByIds(agentKbList);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void cleanExpiredKnowledgeBases() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        List<KnowledgeBase> expiredKnowledgeBases = knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>()
                                .eq(KnowledgeBase::getIsTeam, 0)
                                .eq(KnowledgeBase::getStatus, 0)
                                .le(KnowledgeBase::getUpdateTime, thirtyDaysAgo)
                );

        for (KnowledgeBase expiredKnowledgeBase : expiredKnowledgeBases) {

            transactionTemplate.execute(status -> {
                knowledgeBaseService.removeById(expiredKnowledgeBase.getId());
                return null;
            });

            try {
                Result result = webClient.delete()
                        .uri(
                                uriBuilder -> uriBuilder.path("/api/v1/kb/delete/{kb_name}")
                                        .build(expiredKnowledgeBase.getCollectionName())
                        )
                        .retrieve()
                        .bodyToMono(Result.class)
                        .block();

                if (result == null || result.getCode() != 200) {
                    throw new BusinessException("删除对应milvus向量数据库异常");
                }
            } catch (Exception e){
                log.error("清理失败: {}", expiredKnowledgeBase.getId(), e);  // 只打日志
            }
        }
    }
}
