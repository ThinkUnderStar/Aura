package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.dto.request.WorkspaceDto;
import thinkunderstar.aura.aurabackendserver.dto.response.WorkspaceVODto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.core.SysWorkspaceService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceMemberService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceService;
import thinkunderstar.aura.aurabackendserver.util.CodeUtils;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

@Service
public class SysWorkspaceServiceImpl implements SysWorkspaceService {
    private final WorkspaceMapper workspaceMapper;
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final WorkspaceService workspaceService;
    private final SysKnowledgeBaseService sysKnowledgeBaseService;
    private final WorkspaceMemberService workspaceMemberService;

    public SysWorkspaceServiceImpl(
            WorkspaceMapper workspaceMapper,
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            WorkspaceService workspaceService,
            SysKnowledgeBaseService sysKnowledgeBaseService,
            WorkspaceMemberService workspaceMemberService
    ) {
        this.workspaceMapper = workspaceMapper;
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.workspaceService = workspaceService;
        this.sysKnowledgeBaseService = sysKnowledgeBaseService;
        this.workspaceMemberService = workspaceMemberService;
    }

    @Override
    public Result<IPage<WorkspaceVODto>> getMyWorkspaces(int page, int size) {
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

        //创建团队知识库业务逻辑
        CreateKnowledgeBaseDto createKnowledgeBaseDto = new CreateKnowledgeBaseDto();
        createKnowledgeBaseDto.setName(workspaceDto.getKbName());
        createKnowledgeBaseDto.setDescription(workspaceDto.getKbDescription());
        createKnowledgeBaseDto.setIsTeam(1);
        Result<KnowledgeBase> result = sysKnowledgeBaseService.createKnowledgeBase(createKnowledgeBaseDto);

        //绑定知识库
        workspace.setKbId(result.getData().getId());

        workspaceService.save(workspace);
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

        return Result.success(workspaceVODto);
    }

    @Override
    public Result<Void> logo(Long workspaceId, MultipartFile file) {

    }
}
