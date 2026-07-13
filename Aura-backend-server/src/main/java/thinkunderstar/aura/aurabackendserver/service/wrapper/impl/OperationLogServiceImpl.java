package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.OperationLog;
import thinkunderstar.aura.aurabackendserver.mapper.OperationLogMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.OperationLogService;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {
}
