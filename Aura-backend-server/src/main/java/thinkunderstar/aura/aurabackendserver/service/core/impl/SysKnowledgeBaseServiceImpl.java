package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.CreateKnowledgeBaseDto;
import thinkunderstar.aura.aurabackendserver.entity.KnowledgeBase;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.KnowledgeBaseMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysKnowledgeBaseService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.KnowledgeBaseService;

@Slf4j
@Service
public class SysKnowledgeBaseServiceImpl implements SysKnowledgeBaseService {
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public SysKnowledgeBaseServiceImpl(KnowledgeBaseService knowledgeBaseService, KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
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
}
