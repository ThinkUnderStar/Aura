package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.WorkspaceMemberService;

@Service
public class WorkspaceMemberServiceImpl extends ServiceImpl<WorkspaceMemberMapper, WorkspaceMember> implements WorkspaceMemberService {
}
