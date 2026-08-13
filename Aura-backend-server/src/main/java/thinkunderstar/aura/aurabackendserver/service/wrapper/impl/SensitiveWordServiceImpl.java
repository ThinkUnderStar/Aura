package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.SensitiveWord;
import thinkunderstar.aura.aurabackendserver.mapper.SensitiveWordMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.SensitiveWordService;

@Service
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord> implements SensitiveWordService {
}
