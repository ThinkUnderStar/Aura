package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceOperationLog;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceOperationLogMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceOperationLogService;

@Service
public class WorkspaceOperationLogServiceImpl extends ServiceImpl<WorkspaceOperationLogMapper, WorkspaceOperationLog> implements WorkspaceOperationLogService {
}
