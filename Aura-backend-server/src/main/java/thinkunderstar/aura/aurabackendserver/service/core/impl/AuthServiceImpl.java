package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import thinkunderstar.aura.aurabackendserver.common.Result;
import thinkunderstar.aura.aurabackendserver.dto.request.LoginDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterAdminDto;
import thinkunderstar.aura.aurabackendserver.dto.request.RegisterUserDto;
import thinkunderstar.aura.aurabackendserver.dto.response.UserVODto;
import thinkunderstar.aura.aurabackendserver.entity.User;
import thinkunderstar.aura.aurabackendserver.entity.Workspace;
import thinkunderstar.aura.aurabackendserver.entity.WorkspaceMember;
import thinkunderstar.aura.aurabackendserver.exception.AuthException;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMapper;
import thinkunderstar.aura.aurabackendserver.mapper.WorkspaceMemberMapper;
import thinkunderstar.aura.aurabackendserver.service.core.AuthService;
import thinkunderstar.aura.aurabackendserver.service.wrapper.UserService;
import thinkunderstar.aura.aurabackendserver.util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final HttpServletRequest httpServletRequest;
    private final UserService userService;
    private final RedisUtils redisUtils;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;

    public AuthServiceImpl(
            RedisTokenBucketLimiter redisTokenBucketLimiter,
            HttpServletRequest httpServletRequest,
            UserService userService,
            RedisUtils redisUtils,
            WorkspaceMapper workspaceMapper, WorkspaceMemberMapper workspaceMemberMapper) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.httpServletRequest = httpServletRequest;
        this.userService = userService;
        this.redisUtils = redisUtils;
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
    }

    @Override
    public Result<UserVODto> login(LoginDto loginDto) {
        // 1:密码登录，2:验证码登录
        if (loginDto.getLoginWay() == 1){
            return loginWithPassword(loginDto);
        } else if (loginDto.getLoginWay() == 2) {
            return loginWithCode(loginDto);
        }else {
            throw new BusinessException("登录方式码不符合规定");
        }
    }

    @Override
    public Result<Void> sendCode(String username,String way) {
        if(!(way.equals("login") || way.equals("register") || way.equals("reset"))) {
            throw new AuthException("验证码用途有问题");
        }

        //1为手机号，2为邮箱地址,0为格式错误
        int whichName = 0;

        if(ValidateUtils.phoneValidate(username)){
            whichName = 1;
        }else if(ValidateUtils.emailValidate(username)){
            whichName = 2;
        }

        if(whichName == 0){
            throw new AuthException("用户名格式有误");
        }

        String sendCodeLock = username + ":aura:sendCodeLock:" + way;
        String codeExistLock = username + ":aura:codeExistLock:" + way;
        String sixDigitCode = CodeUtils.getSixDigitCode();

        if(!redisUtils.hasKey(sendCodeLock)){
            if(whichName == 1){
                SmsUtils.sendCode(username, sixDigitCode);
            } else {
                MailUtils.sendCode(username, sixDigitCode);
            }

            redisUtils.set(sendCodeLock,"sleeping",60, TimeUnit.SECONDS);
            redisUtils.set(codeExistLock,sixDigitCode,5, TimeUnit.MINUTES);
        }

        return  Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> registerUser( RegisterUserDto registerDto) {
        //格式验证
        if (registerDto.getUsername() == null || registerDto.getUsername().isEmpty()) {
            throw new AuthException("用户昵称不能为空");
        }

        if (!ValidateUtils.usernameValidate(registerDto.getUsername())) {
            throw new AuthException("用户昵称格式不符合规定");
        }

        if (registerDto.getPassword() == null || registerDto.getPassword().isEmpty()) {
            throw new AuthException("用户密码不能为空");
        }

        if (!ValidateUtils.passwordValidate(registerDto.getPassword())) {
            throw new AuthException("用户密码格式不符合规定");
        }

        if (registerDto.getPhone() == null || registerDto.getPhone().isEmpty()) {
            throw new AuthException("手机号不能为空");
        }

        if (!ValidateUtils.phoneValidate(registerDto.getPhone())) {
            throw new AuthException("用户手机号不合规");
        }

        if (!registerDto.getPassword().equals(registerDto.getRepeatPassword())) {
            throw new AuthException("两次输入的密码不一致");
        }

        boolean IpLimiter = redisTokenBucketLimiter.tryAcquireByIp(IpUtils.getClientIp(httpServletRequest), 3, 1);
        if (!IpLimiter) {
            throw new AuthException("注册过于繁忙，请稍后再试");
        }

        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, registerDto.getPhone()));

        if (user != null && user.getDeleted() != 1) {
            throw new AuthException("该手机号已被其他账户绑定");
        }

        if (
                !redisUtils.hasKey(registerDto.getPhone()+":aura:codeExistLock:register")
                ||
                !registerDto.getCode()
                .equals(redisUtils.get(registerDto.getPhone()+":aura:codeExistLock:register"))
        ){
            throw new AuthException("验证码有误");
        }

        //覆盖软删除且同手机号的用户对象
        if(user!=null){
            deleteUserAccount(user);
        }

        //注册成功清除验证码缓存
        redisUtils.delete(registerDto.getPhone()+":aura:codeExistLock:register");

        user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(BCrypt.hashpw(registerDto.getPassword(), BCrypt.gensalt(12)));
        user.setPhone(registerDto.getPhone());
        user.setRole(1);
        user.setStatus(1);

        userService.save(user);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delete() {
        long loginId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        User user = userService.getById(loginId);
        if (user == null) {
            return Result.success();
        }

        //若此账号是某个团队的创建者，则拦截注销请求
        Long count = workspaceMapper.selectCount(
                new LambdaQueryWrapper<Workspace>()
                        .eq(Workspace::getOwnerId, user.getId())
        );

        if (count > 0) {
            throw new BusinessException("请先转让创建者身份，再注销该账号");
        }

        //清除所有团队中有关此账号的信息
        workspaceMemberMapper.delete(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, user.getId())
        );

        user.setDeleted(1);
        userService.updateById(user);
        return Result.success();
    }

    //1:密码登录
    private Result<UserVODto> loginWithPassword(LoginDto loginDto) {
        User user = null;
        //1为手机号,2为邮箱地址
        int whichName = validateAndRateLimit(loginDto);

        if (whichName == 1) {
            user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone,loginDto.getUsername()));
        }else if (whichName == 2) {
            user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail,loginDto.getUsername()));
        }

        if (user == null || !BCrypt.checkpw(loginDto.getPassword(), user.getPassword()) || user.getDeleted() == 1) {
            throw new AuthException("用户名或密码错误");
        }

        return justifyBan(loginDto, user);
    }

    //2:验证码登录
    private Result<UserVODto> loginWithCode(LoginDto loginDto) {
        User user = null;
        //1为手机号,2为邮箱地址
        int whichName = validateAndRateLimit(loginDto);

        if (whichName == 1) {
            user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone,loginDto.getUsername()));
        }else if (whichName == 2) {
            user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail,loginDto.getUsername()));
        }

        if(user == null || user.getDeleted() == 1){
            throw new AuthException("该用户不存在");
        }

        String code = redisUtils.get(loginDto.getUsername() + ":aura:codeExistLock:login");
        if(code == null || !code.equals(loginDto.getCode())){
            throw new AuthException("验证码不对");
        }

        redisUtils.delete(loginDto.getUsername()+":aura:codeExistLock:login");
        return justifyBan(loginDto, user);
    }

    //验证登陆的用户名格式并对登录进行IP限流
    private int validateAndRateLimit(LoginDto loginDto) {
        //1为手机号,2为邮箱地址,0为用户名格式有问题
        int whichName = 0;

        if(loginDto.getUsername() == null || (loginDto.getPassword() == null && loginDto.getCode() == null)){
            if(loginDto.getLoginWay() == 1) {
                throw new AuthException("用户名或密码不能为空");
            }else {
                throw new AuthException("用户名或验证码不能为空");
            }
        }

        if (ValidateUtils.phoneValidate(loginDto.getUsername())) {
            whichName = 1;
        } else if (ValidateUtils.emailValidate(loginDto.getUsername())) {
            whichName = 2;
        }

        if(whichName == 0){
            throw new AuthException("用户名格式有问题");
        }

        boolean right = redisTokenBucketLimiter.tryAcquireByIp(IpUtils.getClientIp(httpServletRequest), 5, 2);

        if(!right){
            throw new AuthException("登陆过于频繁");
        }

        return whichName;
    }

    @NonNull
    private Result<UserVODto> justifyBan(LoginDto loginDto, User user) {
        String phone = DesensitizeUtils.desensitizePhone(user.getPhone());
        String email = null;
        if (user.getEmail() != null) {
            email = DesensitizeUtils.desensitizeEmail(user.getEmail());
        }

        UserVODto userVODto = new UserVODto(
                user.getId(),
                user.getUsername(),
                phone,
                email,
                user.getAvatar(),
                user.getRole(),
                user.getStatus(),
                user.getBanStartTime(),
                user.getBanEndTime(),
                user.getBanReason(),
                user.getBanBy()
        );

        //是否被封禁
        if (user.getStatus() == 0) {
            if (LocalDateTime.now().isAfter(userVODto.getBanEndTime())) {
                unBanUser(user);
                //登录
                StpUtil.login(user.getId(), loginDto.isRemember());

                userVODto.setStatus(1);
                userVODto.setBanBy(null);
                userVODto.setBanReason(null);
                userVODto.setBanStartTime(null);
                userVODto.setBanEndTime(null);
                userVODto.setToken(StpUtil.getTokenValue());

                return Result.success(userVODto);
            }
            //登陆失败,token为null
            return Result.error(403,"账号已被封禁", userVODto);
        } else {
            StpUtil.login(user.getId(), loginDto.isRemember());
            userVODto.setToken(StpUtil.getTokenValue());
            return Result.success(userVODto);
        }
    }

    //解封账号
    private void unBanUser(User user) {
        user.setStatus(1);
        user.setBanBy(null);
        user.setBanReason(null);
        user.setBanStartTime(null);
        user.setBanEndTime(null);

        userService.updateById(user);
    }

    //彻底删除一个账号
    @Override
    public void deleteUserAccount(User user) {
        //删除头像文件
        if (!(user.getAvatar() == null || user.getAvatar().isEmpty())) {
            String oldAvatar = "./docs"+user.getAvatar();
            try {
                Files.deleteIfExists(Path.of(oldAvatar));
            } catch (IOException e) {
                log.warn("旧用户:"+user.getId()+"头像文件删除失败");
            }
        }

        userService.removeById(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> registerAdmin(RegisterAdminDto registerDto) {
        //格式验证
        if (registerDto.getUsername() == null || registerDto.getUsername().isEmpty()) {
            throw new AuthException("管理员昵称不能为空");
        }

        if (!ValidateUtils.usernameValidate(registerDto.getUsername())) {
            throw new AuthException("管理员昵称格式不符合规定");
        }

        if (registerDto.getPassword() == null || registerDto.getPassword().isEmpty()) {
            throw new AuthException("管理员密码不能为空");
        }

        if (!ValidateUtils.passwordValidate(registerDto.getPassword())) {
            throw new AuthException("管理员密码格式不符合规定");
        }

        if (registerDto.getPhone() == null || registerDto.getPhone().isEmpty()) {
            throw new AuthException("手机号不能为空");
        }

        if (!ValidateUtils.phoneValidate(registerDto.getPhone())) {
            throw new AuthException("管理员手机号不合规");
        }

        if (registerDto.getEmail() == null || registerDto.getEmail().isEmpty()) {
            throw new AuthException("邮箱地址不能为空");
        }

        if (!ValidateUtils.emailValidate(registerDto.getEmail())) {
            throw new AuthException("管理员邮箱地址不合规");
        }

        if (!registerDto.getPassword().equals(registerDto.getRepeatPassword())) {
            throw new AuthException("两次输入的密码不一致");
        }

        boolean IpLimiter = redisTokenBucketLimiter.tryAcquireByIp(
                IpUtils.getClientIp(httpServletRequest),
                3,
                1
        );
        if (!IpLimiter) {
            throw new AuthException("注册过于繁忙，请稍后再试");
        }

        User userPhone = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, registerDto.getPhone()));

        if (userPhone != null && userPhone.getDeleted() != 1) {
            throw new AuthException("该手机号已被其他账号绑定");
        }

        if (
                !redisUtils.hasKey(registerDto.getPhone()+":aura:codeExistLock:register")
                        ||
                        !registerDto.getPhoneCode()
                                .equals(redisUtils.get(registerDto.getPhone()+":aura:codeExistLock:register"))
        ){
            throw new AuthException("手机验证码有误");
        }

        //覆盖软删除且同手机号的用户对象
        if(userPhone !=null){
            deleteUserAccount(userPhone);
        }

        User userEmail = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, registerDto.getEmail()));

        if (userEmail != null && userEmail.getDeleted() != 1) {
            throw new AuthException("该邮箱已被其他账号绑定");
        }

        if (
                !redisUtils.hasKey(registerDto.getEmail()+":aura:codeExistLock:register")
                        ||
                        !registerDto.getEmailCode()
                                .equals(redisUtils.get(registerDto.getEmail()+":aura:codeExistLock:register"))
        ){
            throw new AuthException("邮箱验证码有误");
        }

        //覆盖软删除且同邮箱的用户对象
        if (userEmail !=null){
            deleteUserAccount(userEmail);
        }

        //注册成功清除验证码缓存
        redisUtils.delete(registerDto.getPhone()+":aura:codeExistLock:register");
        redisUtils.delete(registerDto.getEmail()+":aura:codeExistLock:register");

        User user = new User();
        user.setUsername(registerDto.getUsername());
        user.setPassword(BCrypt.hashpw(registerDto.getPassword(), BCrypt.gensalt(12)));
        user.setEmail(registerDto.getEmail());
        user.setPhone(registerDto.getPhone());
        user.setRole(2);
        user.setStatus(1);

        userService.save(user);
        return Result.success();
    }
}



