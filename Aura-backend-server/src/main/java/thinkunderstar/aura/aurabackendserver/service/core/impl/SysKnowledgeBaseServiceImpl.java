package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.KnowledgeBaseMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.KnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceService;

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

    public SysKnowledgeBaseServiceImpl(KnowledgeBaseService knowledgeBaseService, KnowledgeBaseMapper knowledgeBaseMapper, WorkspaceService workspaceService, WorkspaceMemberMapper workspaceMemberMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.workspaceService = workspaceService;
        this.workspaceMemberMapper = workspaceMemberMapper;
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

        KnowledgeBase knowledgeBase = new KnowledgeBase(
                StpUtil.getLoginIdAsLong(),
                createKnowledgeBaseDto.getIsTeam(),
                createKnowledgeBaseDto.getName(),
                createKnowledgeBaseDto.getDescription()
        );
        knowledgeBaseService.save(knowledgeBase);

        //调用python接口创建milvus向量数据库
        log.warn("python接口创建milvus数据库业务未实现");

        return Result.success(knowledgeBase);
    }

    @Override
    public Result<Page<KnowledgeBase>> getMyKnowledgeBases(Integer page, Integer pageSize) {
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
    @Transactional(rollbackFor = Exception.class)
    public Result<KnowledgeBase> updateMyKnowledgeBase(UpdateKnowledgeBaseDto updateKnowledgeBaseDto) {
        if (updateKnowledgeBaseDto == null) {
            throw new BusinessException("知识库修改接口并未正常接收参数");
        }

        if (updateKnowledgeBaseDto.getType() == null){
            throw new BusinessException("知识库修改接口中的修改类型参数为空");
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
    public Result<KnowledgeBase> updateTeamKnowledgeBase(UpdateKnowledgeBaseDto updateKnowledgeBaseDto) {
        if (updateKnowledgeBaseDto == null) {
            throw new BusinessException("知识库修改接口并未正常接收参数");
        }

        if (updateKnowledgeBaseDto.getType() == null){
            throw new BusinessException("知识库修改接口中的修改类型参数为空");
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
                                .in(WorkspaceMember::getRole, Arrays.asList(0,1))
                                .select(WorkspaceMember::getUserId)
                ).stream()
                .map(WorkspaceMember::getUserId)
                .collect(Collectors.toList());

        if (!adminIdList.contains(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("您没有权限修改该知识库");
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

    private Result<KnowledgeBase> updateKnowledgeBaseName(String name , KnowledgeBase knowledgeBase) {
        if (name == null || name.isEmpty()){
            throw new BusinessException("知识库的名字不能为空");
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

        if (description.equals(knowledgeBase.getDescription())){
            return Result.success(knowledgeBase);
        }

        knowledgeBase.setDescription(description);
        knowledgeBaseService.updateById(knowledgeBase);

        return Result.success(knowledgeBase);
    }
}
