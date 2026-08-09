package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ChatDto;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;
import thinkunderstar.aura.aurabackendserver.entity.Agent;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.MessageMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysChatService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.AgentService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

@Service
public class SysChatServiceImpl implements SysChatService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final AgentService agentService;
    private final MessageMapper messageMapper;

    public SysChatServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, AgentService agentService, MessageMapper messageMapper) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.agentService = agentService;
        this.messageMapper = messageMapper;
    }

    @Override
    public Result<Page<MessageVODto>> getChatMessages(int page, int size, Long agentId) {
        if (agentId == null){
            throw new BusinessException("获取agent对话接口的参数接收异常");
        }

        if (page < 1){
            page = 1;
        }

        if (size < 1){
            size = 20;
        }

        if (size > 100){
            size = 100;
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 1)){
            throw new BusinessException("获取agent历史对话过于频繁，请稍后再试");
        }

        Agent agent = agentService.getById(agentId);
        if (agent == null || agent.getUserId() != loginId){
            throw new BusinessException("您无权获取该agent的历史对话记录");
        }

        Page<MessageVODto> pageVO = new Page<>(page, size);
        Page<MessageVODto> messageVODtoPage = messageMapper.selectMessageVODto(pageVO, agentId);

        return Result.success(messageVODtoPage);
    }

    @Override
    public SseEmitter chatWithAgent(Long agentId, ChatDto chatDto) {

    }
}
