package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import thinkunderstar.aura.aurabackendserver.entity.Message;
import thinkunderstar.aura.aurabackendserver.mapper.MessageMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.MessageService;

public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
}
