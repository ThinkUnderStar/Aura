package thinkunderstar.aura.aurabackendserver.service.wrapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import thinkunderstar.aura.aurabackendserver.entity.User;
import thinkunderstar.aura.aurabackendserver.mapper.UserMapper;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;

public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
