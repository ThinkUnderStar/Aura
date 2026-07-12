package thinkunderstar.aura.aurabackendserver.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thinkunderstar.aura.aurabackendserver.common.Result;

@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    @PostMapping("/login/password")
    public Result login(@RequestParam String username, @RequestParam String password){

    }

}
