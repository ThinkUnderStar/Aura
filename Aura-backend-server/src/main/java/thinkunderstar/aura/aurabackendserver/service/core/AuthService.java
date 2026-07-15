package thinkunderstar.aura.aurabackendserver.service.core;

import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.LoginDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterAdminDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterUserDto;
import thinkunderstar.aura.aurabackendserver.dto.response.UserVODto;
import thinkunderstar.aura.aurabackendserver.entity.User;

public interface AuthService {
    /**
     * 用户密码登录
     * <p>
     * 支持用户名、手机号或邮箱作为登录凭证，校验密码是否正确，
     * 验证通过后生成并返回登录Token
     *
     * @param loginDto 登录请求参数，包含账号和密码
     * @return Result 登录结果，成功时返回用户信息及Token
     */
    Result<UserVODto> login(LoginDto loginDto);

    /**
     * 发送登录验证码
     * <p>
     * 用户通过手机号或邮箱获取登录验证码，验证码将发送至用户绑定的手机或邮箱，
     * 有效期为5分钟，可用于验证码登录。
     *
     * @param username 手机号或邮箱地址（根据格式自动识别）
     * @return Result 发送结果，成功时返回"验证码已发送"，失败时返回错误信息
     */
    Result<Void> sendCode(String username,String way);

    /**
     * 用户注册
     * <p>
     * 通过手机号注册新用户，注册成功后自动登录并返回用户信息。
     * 注册完成后会自动创建用户的个人空间。
     *
     * @param registerDto 注册请求参数，包含用户名、密码、确认密码、手机号、验证码
     * @return Result 注册结果，成功时返回用户信息及Token，失败时返回错误信息
     */
    Result<Void> registerUser(RegisterUserDto registerDto);

    /**
     * 注销账户
     * <p>
     * 永久注销当前登录账户，执行软删除（deleted=1），
     * 注销后该账号无法登录，但历史数据保留。
     * 前端需在调用前二次确认用户意愿。
     *
     * @return Result 注销结果
     */
    Result<Void> delete();

    /**
     * 彻底删除之前软删除的账户
     *
     * @param user 之前被软删除的账户对象
     */
    void deleteUserAccount(User user);

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
    Result<Void> registerAdmin(RegisterAdminDto registerDto);
}


