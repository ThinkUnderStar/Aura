package thinkunderstar.aura.aurabackendserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateUserDto;
import thinkunderstar.aura.aurabackendserver.service.core.SysUserService;

@RestController
@RequestMapping("/user")
public class SysUserController {
    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 更新当前用户信息
     * <p>
     * 用于更新当前登录用户的基本信息，包括用户名、邮箱等。
     * 不支持修改密码（需使用 /password 接口）和头像（需使用 /avatar 接口）。
     * 更新成功后自动返回最新信息，部分字段（如手机号）有唯一性校验。
     *
     * @param updateUserDto 更新请求参数
     * @return Result 更新结果
     */
    @PutMapping("/update")
    @SaCheckLogin
    public Result<Void> update(@RequestBody UpdateUserDto updateUserDto){
        return sysUserService.update(updateUserDto);
    }

    /**
     * 上传头像
     * <p>
     * 当前登录用户上传头像文件，支持 JPG、PNG、GIF 格式，
     * 文件大小限制为 5MB。上传成功后自动更新用户头像 URL。
     *
     * @param file 上传的头像文件（multipart/form-data）
     * @return Result 上传结果，成功时返回头像访问路径
     */
    @PutMapping("/avatar")
    @SaCheckLogin
    public Result<Void> avatar(@RequestParam("file") MultipartFile file){
        return sysUserService.avatar(file);
    }
}
