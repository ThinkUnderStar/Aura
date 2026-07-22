package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.KbIds;
import thinkunderstar.aura.aurabackendserver.dto.response.BindingKbInformationVODto;
import thinkunderstar.aura.aurabackendserver.entity.Agent;
import thinkunderstar.aura.aurabackendserver.entity.AgentKbBinding;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.AgentKbBindingMapper;
import thinkunderstar.aura.aurabackendserver.mapper.AgentMapper;
import thinkunderstar.aura.aurabackendserver.mapper.KnowledgeBaseMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysAgentService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.AgentService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.impl.AgentKbBindingServiceImpl;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;
import thinkunderstar.aura.aurabackendserver.util.ValidateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysAgentServiceImpl implements SysAgentService {

    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final AgentService agentService;
    private final AgentMapper agentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentKbBindingMapper agentKbBindingMapper;
    private final AgentKbBindingServiceImpl agentKbBindingService;
    private final WorkspaceMapper workspaceMapper;

    public SysAgentServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, AgentService agentService, AgentMapper agentMapper, KnowledgeBaseMapper knowledgeBaseMapper, AgentKbBindingMapper agentKbBindingMapper, AgentKbBindingServiceImpl agentKbBindingService, WorkspaceMapper workspaceMapper) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.agentService = agentService;
        this.agentMapper = agentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.agentKbBindingMapper = agentKbBindingMapper;
        this.agentKbBindingService = agentKbBindingService;
        this.workspaceMapper = workspaceMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Agent> createAgent(String name) {
        if (name == null ||  name.trim().isEmpty()) {
            throw new BusinessException("agent的名字不能为空");
        }

        if (!ValidateUtils.agentNameValidate(name)){
            throw new BusinessException("agent的名字不合规范");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),3,1)){
            throw new BusinessException("创建agent过于频繁，请稍后再试");
        }

        Agent agent = new Agent(loginId,name.trim());
        agentService.save(agent);

        log.warn("调用python端接口，创建会话级记忆库");

        return Result.success(agent);
    }

    @Override
    public Result<Page<Agent>> getAgent(Long page, Long size) {
        if (page == null || size == null) {
            throw new BusinessException("获得agents接口的参数接收异常");
        }

        if (page < 1){
            page = 1L;
        }

        if (size < 1){
            size = 20L;
        }

        if (size > 100){
            size = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取agents过于频繁，请稍后再试");
        }

        Page<Agent> agentPage = new Page<>(page,size);
        Page<Agent> resultPage = agentMapper.selectPage(
                agentPage,
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, loginId)
                        .eq(Agent::getStatus, 1)
                        .orderByDesc(Agent::getUpdateTime)
        );

        return Result.success(resultPage);
    }

    @Override
    public Result<Page<Agent>> searchAgent(String keyWord, Long page, Long size) {
        if (keyWord == null || page == null || size == null) {
            throw new BusinessException("搜索agent接口的参数接收异常");
        }

        if (page < 1){
            page = 1L;
        }

        if (size < 1){
            size = 20L;
        }

        if (size > 100){
            size = 100L;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("搜索agent过于频繁，请稍后再试");
        }

        Page<Agent> agentPage = new Page<>(page, size);
        Page<Agent> resultPage = agentMapper.selectPage(
                agentPage,
                new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUserId, loginId)
                        .eq(Agent::getStatus, 1)
                        .like(Agent::getName, keyWord)
                        .orderByDesc(Agent::getUpdateTime)
        );

        return Result.success(resultPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Agent> updateAgent(Long id, String name) {
        if (id == null || name == null || name.trim().isEmpty()) {
            throw new BusinessException("更新agent接口的参数接收异常");
        }

        if (!ValidateUtils.agentNameValidate(name)){
            throw new BusinessException("agent的名字不合规范");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("更新agent过于频繁，请稍后再试");
        }

        Agent agent = agentService.getById(id);
        if (agent == null) {
            throw new BusinessException("未查询到该agent");
        }

        if (agent.getUserId() != loginId) {
            throw new BusinessException("您没有权限修改该agent");
        }

        if (agent.getStatus() != 1) {
            throw new BusinessException("该agent已被归档，无法修改");
        }

        agent.setName(name.trim());
        agentService.updateById(agent);

        return Result.success(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> bindKnowledgeBases(Long agentId, KbIds kbIds) {
        if (agentId == null || kbIds == null || kbIds.getKbIds() == null) {
            throw new BusinessException("agent绑定知识库接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("绑定知识库过于频繁，请稍后再试");
        }

        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException("未查询到该agent");
        }

        if (agent.getUserId() != loginId) {
            throw new BusinessException("您没有权限给该agent绑定知识库");
        }

        if (agent.getStatus() != 1) {
            throw new BusinessException("该agent已被归档，无法修改");
        }

        //删除该agent曾今绑定的知识库
        agentKbBindingMapper.delete(
                new LambdaQueryWrapper<AgentKbBinding>()
                        .eq(AgentKbBinding::getAgentId, agentId)
        );

        List<Long> list = knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>()
                                .eq(KnowledgeBase::getOwnerId, loginId)
                                .eq(KnowledgeBase::getIsTeam, 0)
                                .eq(KnowledgeBase::getStatus, 1)
                ).stream()
                .map(KnowledgeBase::getId)
                .collect(Collectors.toList());

        list.addAll(knowledgeBaseMapper.selectTeamKbIdsByUserId(loginId));

        List<AgentKbBinding> agentKbBindings = new ArrayList<>();
        kbIds.getKbIds().stream().distinct().forEach(kbId -> {
            if (list.contains(kbId)) {
                AgentKbBinding agentKbBinding = new AgentKbBinding(agentId, kbId);
                agentKbBindings.add(agentKbBinding);
            } else {
                log.warn("用户: "+loginId+" 打算给他的agent绑定一个不属于他的知识库: "+kbId );
            }
        });

        agentKbBindingService.saveBatch(agentKbBindings);
        return Result.success();
    }

    @Override
    public Result<BindingKbInformationVODto> getBindingKbInformation(Long agentId) {
        if (agentId == null) {
            throw new BusinessException("获取agent绑定知识库信息接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),10,2)){
            throw new BusinessException("获取agent绑定知识库信息过于频繁，请稍后再试");
        }

        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException("未查询到该agent");
        }

        if (agent.getUserId() != loginId) {
            throw new BusinessException("您没有权限查看该agent的绑定信息");
        }

        if (agent.getStatus() != 1) {
            throw new BusinessException("该agent已被归档");
        }

        // 查询该agent所有的绑定记录
        List<AgentKbBinding> bindings = agentKbBindingMapper.selectList(
                new LambdaQueryWrapper<AgentKbBinding>()
                        .eq(AgentKbBinding::getAgentId, agentId)
        );

        List<Long> kbIds = new ArrayList<>();
        List<Long> workspaceIds = new ArrayList<>();

        if (!bindings.isEmpty()) {
            List<Long> boundKbIds = bindings.stream()
                    .map(AgentKbBinding::getKbId)
                    .collect(Collectors.toList());

            // 查询对应的知识库（仅正常状态的）
            List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeBase>()
                            .in(KnowledgeBase::getId, boundKbIds)
                            .eq(KnowledgeBase::getStatus, 1)
            );

            for (KnowledgeBase kb : knowledgeBases) {
                if (kb.getIsTeam() == 0) {
                    // 个人知识库
                    kbIds.add(kb.getId());
                } else {
                    // 团队知识库，查找对应的正常团队
                    List<Workspace> workspaces = workspaceMapper.selectList(
                            new LambdaQueryWrapper<Workspace>()
                                    .eq(Workspace::getKbId, kb.getId())
                                    .eq(Workspace::getStatus, 1)
                    );
                    for (Workspace ws : workspaces) {
                        workspaceIds.add(ws.getId());
                    }
                }
            }
        }

        BindingKbInformationVODto result = new BindingKbInformationVODto();
        result.setKbIds(kbIds);
        result.setWorkspaceIds(workspaceIds);

        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAgent(Long id) {
        if (id == null) {
            throw new BusinessException("删除agent接口的参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("删除agent过于频繁，请稍后再试");
        }

        Agent agent = agentService.getById(id);
        if (agent == null) {
            throw new BusinessException("未查询到该agent");
        }

        if (agent.getUserId() != loginId) {
            throw new BusinessException("您没有权限删除该agent");
        }

        if (agent.getStatus() != 1) {
            throw new BusinessException("该agent已被归档，无法删除");
        }

        // 清理该agent的所有知识库绑定记录
        agentKbBindingMapper.delete(
                new LambdaQueryWrapper<AgentKbBinding>()
                        .eq(AgentKbBinding::getAgentId, id)
        );

        // 物理删除agent
        agentService.removeById(id);

        // TODO: 调用python接口删除PostgreSQL中的agent记忆
        log.warn("python端删除agent记忆的接口未完成，agentId: {}", id);

        return Result.success();
    }
}
