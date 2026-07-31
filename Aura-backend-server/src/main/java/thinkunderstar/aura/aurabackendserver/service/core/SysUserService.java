package thinkunderstar.aura.aurabackendserver.service.core;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.AiImageDto;
import thinkunderstar.aura.aurabackendserver.dto.request.PromptDto;
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

    /**
     * AI生成头像（调用ComfyUI服务）
     * <p>
     * 用户提供提示词，系统调用ComfyUI生成一张1024x1024的头像图片。
     * 生成的图片会临时保存在服务器临时目录中，供前端预览。
     * 前端预览后可选择“设为头像”或“取消”，临时图片会在最终确认或取消时被清理。
     * <p>
     * <b>流程说明：</b>
     * <ol>
     *     <li>用户提供提示词</li>
     *     <li>Java端调用Python服务，Python再调用ComfyUI生成图片</li>
     *     <li>图片生成后返回临时访问URL给前端</li>
     *     <li>前端展示图片供用户预览</li>
     *     <li>用户确认或取消后，调用对应接口清理临时文件</li>
     * </ol>
     * <p>
     * <b>权限要求：</b>用户必须已登录
     * <p>
     * <b>限流说明：</b>生成图片属于高消耗操作（显存、GPU），同一用户每10秒仅允许1次请求，突发峰值不超过2次。
     * <p>
     * <b>错误码：</b>
     * <ul>
     *     <li>200 - 生成成功，返回图片临时访问URL</li>
     *     <li>400 - 提示词为空或格式不正确</li>
     *     <li>401 - 用户未登录</li>
     *     <li>429 - 请求过于频繁，触发限流</li>
     *     <li>500 - 服务器内部错误（ComfyUI服务不可用、生成超时等）</li>
     * </ul>
     *
     * @param promptDto 提示词请求体，包含 prompt 字段
     * @return Result 包含临时图片访问URL，如：/temp_avatars/20260730_143022_a1b2c3d4.png
     */
    Result<String> generate( PromptDto promptDto);

    /**
     * 保存AI生成的临时头像为正式头像
     * <p>
     * 用户预览AI生成的临时头像后，调用此接口将其保存为正式头像。
     * 保存操作会执行以下三步流程：
     * <ol>
     *     <li>从临时目录复制图片到正式头像目录</li>
     *     <li>更新当前用户的头像URL</li>
     *     <li>删除临时目录中的图片文件</li>
     * </ol>
     * <p>
     * <b>调用前提：</b>用户必须先调用生成接口（/avatar/generate）获取临时图片，
     * 否则传入的临时文件名将无法找到对应文件。
     * <p>
     * <b>权限要求：</b>用户必须已登录
     * <p>
     * <b>限流说明：</b>保存头像属于文件操作（复制+删除），虽消耗较低但涉及用户数据更新，
     * 同一用户每1秒仅允许1次请求，突发峰值不超过5次。
     * <p>
     * <b>错误码：</b>
     * <ul>
     *     <li>200 - 保存成功</li>
     *     <li>400 - 临时文件名为空或格式错误</li>
     *     <li>401 - 用户未登录</li>
     *     <li>404 - 临时文件不存在</li>
     *     <li>429 - 请求过于频繁，触发限流</li>
     *     <li>500 - 服务器内部错误（文件复制/删除失败）</li>
     * </ul>
     *
     * @param aiImageDto 包含临时文件名的请求体
     * @return Result 保存结果，成功返回新头像的访问URL
     */
    Result<String> saveGeneratedImage( AiImageDto aiImageDto);
}
