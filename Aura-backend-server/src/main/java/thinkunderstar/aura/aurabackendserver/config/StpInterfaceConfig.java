package thinkunderstar.aura.aurabackendserver.config;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.context.annotation.Configuration;
import thinkunderstar.aura.aurabackendserver.entity.User;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class StpInterfaceConfig implements StpInterface {
    private final UserService userService;

    public StpInterfaceConfig(UserService userService) {
        this.userService = userService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        long userId = Long.parseLong(loginId.toString());
        User user = userService.getById(userId);
        List<String> list = new ArrayList<>();

        if (user == null) {
            return List.of();
        }

        if (user.getRole() == 1) {
            list.add("user");
        } else if (user.getRole() == 2) {
            list.add("admin");
        }

        return list;
    }
}
