package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.PromptDto;
import thinkunderstar.aura.aurabackendserver.dto.request.UpdateUserDto;
import thinkunderstar.aura.aurabackendserver.entity.User;
import thinkunderstar.aura.aurabackendserver.exception.AuthException;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.service.core.AuthService;
import thinkunderstar.aura.aurabackendserver.service.core.SysUserService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;
import thinkunderstar.aura.aurabackendserver.util.RedisUtils;
import thinkunderstar.aura.aurabackendserver.util.ValidateUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class SysUserServiceImpl implements SysUserService {
    private static final long MAX_SIZE = 5*1024*1024;

    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final UserService userService;
    private final RedisUtils redisUtils;
    private final AuthService authService;
    private final WebClient webClient;

    public SysUserServiceImpl(
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            UserService userService, RedisUtils redisUtils,
            AuthService authService,
            WebClient webClient) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.userService = userService;
        this.redisUtils = redisUtils;
        this.authService = authService;
        this.webClient = webClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(UpdateUserDto updateUserDto) {
        //type只包括“username”，“password”，“phone”，“email”
        if (updateUserDto.getType() == null || updateUserDto.getType().isEmpty()) {
            throw new AuthException("修改用户信息的类型参数有问题");
        }

        boolean updateLimiter = redisTokenBucketLimiter.tryAcquireByUser(
                StpUtil.getLoginIdAsString(),
                5,
                1
        );

        if (!updateLimiter) {
            throw new AuthException("修改操作过于频繁，请稍后再试");
        }

        switch (updateUserDto.getType()) {
            case "username" -> {
                //修改用户昵称
                if (updateUsername(updateUserDto.getUsername())) {
                    return Result.success();
                } else {
                    return Result.error("修改用户昵称失败");
                }
                //修改用户昵称
            }
            case "password" -> {
                //修改用户密码
                if (updatePassword(updateUserDto.getPassword(), updateUserDto.getRepeatPassword())) {
                    return Result.success();
                } else {
                    return Result.error("修改用户密码失败");
                }
                //修改用户密码
            }
            case "phone" -> {
                //修改账号绑定的手机号
                if (updatePhone(updateUserDto.getPhone(), updateUserDto.getCode())) {
                    return Result.success();
                } else {
                    return Result.error("换绑用户手机号失败");
                }
                //修改账号绑定的手机号
            }
            case "email" -> {
                //修改账号绑定的邮箱地址
                if (updateEmail(updateUserDto.getEmail(), updateUserDto.getCode())) {
                    return Result.success();
                } else {
                    return Result.error("修改用户邮箱地址失败");
                }
                //修改账号绑定的邮箱地址
            }
            default -> throw new AuthException("修改用户信息的类型参数有问题");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> avatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传的头像文件为空");
        }

        if (file.getSize() > MAX_SIZE ) {
            throw new BusinessException("上传的头像文件过大");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new BusinessException("头像文件名不能为空");
        }

        if (!fileName.contains(".")) {
            throw new BusinessException("头像文件缺少扩展名");
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        if(!List.of("jpg","png","jpeg","webp").contains(ext)){
            throw new BusinessException("头像文件格式只支持:\".jpg\",\".png\",\".jpeg\",\".webp\"");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        //令牌桶算法，按用户限流
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),5,1)){
            throw new BusinessException("修改logo过于频繁，请稍后再试");
        }

        User user = userService.getById(loginId);

        if (!(user.getAvatar() == null || user.getAvatar().isEmpty())) {
            String oldAvatar = "./docs"+user.getAvatar();
            try {
                Files.deleteIfExists(Path.of(oldAvatar));
            } catch (IOException e) {
                log.warn("用户:"+loginId+"的旧头像文件删除失败");
            }
        }

        String avatar = "/avatars/"+StpUtil.getLoginIdAsString()+"-"+System.currentTimeMillis()+"-aura"+ext;
        try {
            file.transferTo(Path.of("./docs"+avatar).toFile());
        } catch (IOException e) {
            log.error("用户:"+StpUtil.getLoginIdAsString()+"的头像文件上传失败");
            throw new BusinessException("头像文件上传失败");
        }

        user.setAvatar(avatar);
        userService.updateById(user);
        return Result.success(avatar);
    }

    @Override
    public Result<String> generate(PromptDto promptDto) {
        if (promptDto == null || promptDto.getPrompt() == null) {
            throw new BusinessException("ai生成头像接口的提示词参数接收异常");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        if (!redisTokenBucketLimiter.tryAcquireByUser(String.valueOf(loginId),2,0.1)){
            throw new BusinessException("Ai生成头像过于频繁，请稍后再试");
        }

        //调用python接口，用comfyUI生成头像文件
        Result<String> result = webClient.post()
                .uri("/api/v1/avatar/generate")
                .bodyValue(promptDto)
                .retrieve()
                .bodyToMono(Result.class)
                .block();

        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("生成图片失败");
        }

        return Result.success("/temp_images/"+result.getData());
    }

    //修改用户的昵称
    private boolean updateUsername(String username) {
        if (username == null) {
            throw new AuthException("修改后的用户昵称不能为空");
        }

        if (!ValidateUtils.usernameValidate(username)) {
            throw new AuthException("修改后的用户昵称不符合命名规范");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);

        user.setUsername(username);
        userService.updateById(user);
        return true;
    }

    //修改用户的账户密码
    private boolean updatePassword(String password, String repeatPassword) {
        if (password == null) {
            throw new AuthException("修改后的用户密码不能为空");
        }

        if (!ValidateUtils.passwordValidate(password)) {
            throw new AuthException("修改后的用户密码不符合要求");
        }

        if (!password.equals(repeatPassword)) {
            throw new AuthException("第二次输入的密码与第一次不同");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);

        if (BCrypt.checkpw(password, user.getPassword())) {
            throw new AuthException("修改后的密码不能与原密码相同");
        }

        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(12)));
        userService.updateById(user);
        return true;
    }

    //换绑该账号的手机号
    private boolean updatePhone(String phone, String code) {
        if (phone == null) {
            throw new AuthException("换绑的手机号为空");
        }

        if (!ValidateUtils.phoneValidate(phone)) {
            throw new AuthException("换绑的手机号不合规");
        }

        if (code == null || !code.equals(redisUtils.get(phone+":aura:codeExistLock:reset"))) {
            throw new AuthException("验证码错误");
        }

        User one = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));

        if (one != null && one.getDeleted() != 1) {
            throw new AuthException("该手机号已被其他账号绑定");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);
        user.setPhone(phone);
        userService.updateById(user);

        if(one != null){
            authService.deleteUserAccount(one);
        }
        return true;
    }

    //修改该账户绑定的邮箱地址
    private boolean updateEmail(String email, String code) {
        if (email == null) {
            throw new AuthException("换绑的邮箱地址为空");
        }

        if (!ValidateUtils.emailValidate((email))) {
            throw new AuthException("换绑的邮箱地址不合规");
        }

        if (code == null || !code.equals(redisUtils.get(email+":aura:codeExistLock:reset"))) {
            throw new AuthException("验证码错误");
        }

        User one = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (one != null && one.getDeleted() != 1) {
            throw new AuthException("该邮箱已被其他账户绑定");
        }

        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);
        user.setEmail(email);
        userService.updateById(user);

        if(one != null){
            authService.deleteUserAccount(one);
        }
        return true;
    }
}
