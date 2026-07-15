package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.*;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.LoginDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterAdminDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterUserDto;
import thinkunderstar.aura.aurabackendserver.dto.response.UserVODto;
import thinkunderstar.aura.aurabackendserver.service.core.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户密码登录
     * <p>
     * 支持用户名、手机号或邮箱作为登录凭证，校验密码是否正确，
     * 验证通过后生成并返回登录Token
     *
     * @param loginDto 登录请求参数，包含账号和密码
     * @return Result 登录结果，成功时返回用户信息及Token
     */
    @PostMapping("/login")
    public Result<UserVODto> login(@RequestBody LoginDto loginDto){
        return authService.login(loginDto);
    }

    /**
     * 发送登录验证码
     * <p>
     * 用户通过手机号或邮箱获取登录验证码，验证码将发送至用户绑定的手机或邮箱，
     * 有效期为5分钟，可用于验证码登录。
     *
     * @param username 手机号或邮箱地址（根据格式自动识别）
     * @return Result 发送结果，成功时返回"验证码已发送"，失败时返回错误信息
     */
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam String username,@RequestParam String way){
        return authService.sendCode(username,way);
    }

    /**
     * 用户注册
     * <p>
     * 通过手机号注册新用户，注册成功后自动登录并返回用户信息。
     * 注册完成后会自动创建用户的个人空间。
     *
     * @param registerDto 注册请求参数，包含用户名、密码、确认密码、手机号、验证码
     * @return Result 注册结果，成功时返回用户信息及Token，失败时返回错误信息
     */
    @PostMapping("/register/user")
    public Result<Void> registerUser(@RequestBody RegisterUserDto registerDto){
        return authService.registerUser(registerDto);
    }

    /**
     * 管理员注册接口
     * <p>
     * 用于创建新的管理员账号，仅限已有管理员权限的用户调用。
     * 注册时需提供用户名、密码、手机号、邮箱及验证码。
     * 创建成功后可直接登录，无需再次激活。
     * 管理员账号默认拥有系统管理权限。
     *
     * @param registerDto 管理员注册参数，包含用户名、密码、确认密码、手机号、邮箱、验证码
     * @return Result 注册结果，成功时返回成功消息，失败时返回错误信息
     */
    @PostMapping("/register/admin")
    @SaCheckLogin
    @SaCheckRole("admin")
    public Result<Void> registerAdmin(@RequestBody RegisterAdminDto registerDto){
        return authService.registerAdmin(registerDto);
    }

    /**
     * 用户退登
     * <p>
     * 清除当前用户的登录状态，使Token失效。
     * 调用后客户端需要清除本地存储的Token。
     *
     * @return Result 退登结果，成功返回 success
     */
    @DeleteMapping("/logout")
    @SaCheckLogin
    public Result<Void> logout(){
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 注销账户
     * <p>
     * 永久注销当前登录账户，执行软删除（deleted=1），
     * 注销后该账号无法登录，但历史数据保留。
     * 前端需在调用前二次确认用户意愿。
     *
     * @return Result 注销结果
     */
    @DeleteMapping("/delete")
    @SaCheckLogin
    public Result<Void> delete(){
        return authService.delete();
    }
}
