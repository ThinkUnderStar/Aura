package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.ChatDto;
import thinkunderstar.aura.aurabackendserver.dto.request.ToolAllowDto;
import thinkunderstar.aura.aurabackendserver.dto.response.ChatVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.KnowledgeBaseVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.MessageVODto;
import thinkunderstar.aura.aurabackendserver.dto.response.ToolAllowVODto;
import thinkunderstar.aura.aurabackendserver.entity.Agent;
import thinkunderstar.aura.aurabackendserver.entity.Message;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.AgentKbBindingMapper;
import thinkunderstar.aura.aurabackendserver.mapper.KnowledgeBaseMapper;
import thinkunderstar.aura.aurabackendserver.mapper.MessageMapper;
import thinkunderstar.aura.aurabackendserver.service.core.SysChatService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.AgentService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.MessageService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysChatServiceImpl implements SysChatService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final AgentService agentService;
    private final MessageMapper messageMapper;
    private final MessageService messageService;
    private final WebClient webClient;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentKbBindingMapper agentKbBindingMapper;

    public SysChatServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, AgentService agentService, MessageMapper messageMapper, MessageService messageService, WebClient webClient, KnowledgeBaseMapper knowledgeBaseMapper, AgentKbBindingMapper agentKbBindingMapper) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.agentService = agentService;
        this.messageMapper = messageMapper;
        this.messageService = messageService;
        this.webClient = webClient;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.agentKbBindingMapper = agentKbBindingMapper;
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
        if (agentId == null ||  chatDto == null){
            throw new BusinessException("AI对话接口的参数接收异常");
        }

        if (chatDto.getHumanContent().trim().isEmpty()){
            throw new BusinessException("发送给AI的消息不能为空");
        }

        //用户级限流
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 1)){
            throw new BusinessException("与AI对话过去频繁，请稍后再试");
        }

        //鉴权
        Agent agent = agentService.getById(agentId);
        if (agent == null || agent.getUserId() != loginId){
            throw new BusinessException("您无权与该ai对话");
        }

        //判断会话状态
        Message message = messageService.getOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getAgentId, agentId)
                        .orderByDesc(Message::getCreateTime)
                        .last("limit 1")
        );

        if (message != null){
            if (message.getRole().equals("tool_confirm")){
                throw new BusinessException("工具调用未确认，请确认后在发送信息");
            } else if (message.getRole().equals("user")) {
                throw new BusinessException("请在ai消息发完后，在发送新的对话");
            }
        }

        //构建请求体
        ChatVODto chatVODto = new ChatVODto();
        chatVODto.setUserId(loginId);
        chatVODto.setHumanContent(chatDto.getHumanContent());
        chatVODto.setEnableWebSearch(chatDto.getEnableWebSearch());

        //获取绑定的知识库信息
        List<Long> kbIds = agentKbBindingMapper.selectKbIdsByAgentId(agentId);
        List<KnowledgeBaseVODto> knowledgeBases = knowledgeBaseMapper.selectByIds(kbIds)
                .stream()
                .map(knowledgeBase -> new KnowledgeBaseVODto(
                        knowledgeBase.getCollectionName(),
                        knowledgeBase.getDescription()
                ))
                .collect(Collectors.toList());

        chatVODto.setKnowledgeBases(knowledgeBases);

        //向python端发送请求，并分装sse协议返回给前端
        SseEmitter sseEmitter = new SseEmitter(120_000L);
        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/chat/send/{agentId}")
                        .build(agentId)
                )
                .bodyValue(chatVODto)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(response -> !response.trim().isEmpty())
                .subscribe(
                        response -> {
                            try {
                                if (response.startsWith("data:")){
                                    sseEmitter.send(response);
                                } else if (response.startsWith("event: interrupt")) {

                                    String interrupt_value_json = response.split("data:")[1].trim();
                                    Message tool_message = new Message();
                                    tool_message.setAgentId(agentId);
                                    tool_message.setRole("tool_confirm");
                                    tool_message.setContent(interrupt_value_json);
                                    messageService.save(tool_message);

                                    sseEmitter.send(response);
                                }
                            }catch (Exception e){
                                sseEmitter.completeWithError(e);
                            }
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete
                );

        return sseEmitter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter toolAllow(Long agentId, ToolAllowDto toolAllowDto) {
        if (agentId == null || toolAllowDto == null){
            throw new BusinessException("工具调用确认接口的参数接收异常");
        }

        if (
                toolAllowDto.getChoice() ==  null
                        || !List.of("approve","reject","edit").contains(toolAllowDto.getChoice())
        ) {
            throw new BusinessException("工具调用接口，用户传入的choice值异常");
        }

        if (toolAllowDto.getChoice().equals("edit")
                && (toolAllowDto.getEdition() == null ||  toolAllowDto.getEdition().trim().isEmpty())){
            throw new BusinessException("edition内容不能为空");
        }

        //限流
        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId), 20, 1)){
            throw new BusinessException("工具调用过于频繁，请稍后再试");
        }

        //鉴权
        Agent agent = agentService.getById(agentId);
        if (agent == null || agent.getUserId() != loginId){
            throw new BusinessException("您无权对该agent操作");
        }

        //异常调用过滤
        Message message = messageService.getOne(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getAgentId, agentId)
                        .orderByDesc(Message::getCreateTime)
                        .last("limit 1")
        );

        if (message == null || !message.getRole().equals("tool_confirm")){
            throw new BusinessException("只有工具调用确认才能调用该接口");
        }

        message.setAction(toolAllowDto.getChoice());
        message.setEditedContent(toolAllowDto.getEdition());
        messageService.updateById(message);

        //封装body
        ToolAllowVODto toolAllowVODto = new ToolAllowVODto();
        toolAllowVODto.setUserId(loginId);
        toolAllowVODto.setAgentId(agentId);
        toolAllowVODto.setChoice(toolAllowDto.getChoice());
        toolAllowVODto.setEdition(toolAllowDto.getEdition());

        //封装sse协议
        SseEmitter sseEmitter = new SseEmitter(120_000L);
        webClient.post()
                .uri("/api/v1/chat/tool_allow")
                .bodyValue(toolAllowVODto)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(response -> !response.trim().isEmpty())
                .subscribe(
                        response -> {
                            try {
                                if (response.startsWith("data:")){
                                    sseEmitter.send(response);
                                } else if (response.startsWith("event: interrupt")) {

                                    String interrupt_value_json = response.split("data:")[1].trim();
                                    Message tool_message = new Message();
                                    tool_message.setAgentId(agentId);
                                    tool_message.setRole("tool_confirm");
                                    tool_message.setContent(interrupt_value_json);
                                    messageService.save(tool_message);

                                    sseEmitter.send(response);
                                }
                            }catch (Exception e){
                                sseEmitter.completeWithError(e);
                            }
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete
                );

        return sseEmitter;
    }
}
