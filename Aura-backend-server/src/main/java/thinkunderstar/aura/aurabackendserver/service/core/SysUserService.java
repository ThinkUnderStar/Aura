package thinkunderstar.aura.aurabackendserver.service.core;

import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateUserDto;

public interface SysUserService {
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
    Result<Void> update(UpdateUserDto updateUserDto);

    /**
     * 上传头像
     * <p>
     * 当前登录用户上传头像文件，支持 JPG、PNG、GIF 格式，
     * 文件大小限制为 5MB。上传成功后自动更新用户头像 URL。
     *
     * @param file 上传的头像文件（multipart/form-data）
     * @return Result 上传结果，成功时返回头像访问路径
     */
    Result<String> avatar(MultipartFile file);
}
