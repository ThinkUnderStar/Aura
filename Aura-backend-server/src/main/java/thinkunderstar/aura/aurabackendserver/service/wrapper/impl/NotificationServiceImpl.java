package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.entity.Notification;
import thinkunderstar.aura.aurabackendserver.mapper.NotificationMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.NotificationService;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {
}
