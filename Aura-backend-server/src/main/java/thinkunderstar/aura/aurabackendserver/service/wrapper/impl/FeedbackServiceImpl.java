package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.Feedback;
import thinkunderstar.aura.aurabackendserver.mapper.FeedbackMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.FeedbackService;

@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {
}
