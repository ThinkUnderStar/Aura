package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.AgentKbBinding;
import thinkunderstar.aura.aurabackendserver.mapper.AgentKbBindingMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.AgentKbBindingService;

@Service
public class AgentKbBindingServiceImpl extends ServiceImpl<AgentKbBindingMapper, AgentKbBinding> implements AgentKbBindingService {
}
