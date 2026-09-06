package thinkunderstar.aura.aurabackendserver.service.core.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import thinkunderstar.aura.aurabackendserver.exception.BusinessException;
import thinkunderstar.aura.aurabackendserver.service.core.CaptchaService;
import thinkunderstar.aura.aurabackendserver.util.IpUtils;
import thinkunderstar.aura.aurabackendserver.util.RedisTokenBucketLimiter;
import thinkunderstar.aura.aurabackendserver.util.RedisUtils;
import thinkunderstar.aura.aurabackendserver.util.ValidateUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    private final RedisTokenBucketLimiter redisTokenBucketLimiter;
    private final RedisUtils redisUtils;

    public CaptchaServiceImpl(RedisTokenBucketLimiter redisTokenBucketLimiter, RedisUtils redisUtils) {
        this.redisTokenBucketLimiter = redisTokenBucketLimiter;
        this.redisUtils = redisUtils;
    }

    @Override
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response, String tempKey) {
        if (!ValidateUtils.uuidValidate(tempKey)) {
            throw new BusinessException("临时人机验证码的临时Key异常");
        }

        if (!redisTokenBucketLimiter.tryAcquireByIp(IpUtils.getClientIp(request),10,1)){
            throw new BusinessException("刷新过于频繁，请稍后再试");
        }

        //定义图形验证码的长和宽
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(200, 100);

        //存入redis
        redisUtils.set(tempKey,lineCaptcha.getCode(),30, TimeUnit.MINUTES);

        //图形验证码写出，可以写出到文件，也可以写出到流
        try {
            lineCaptcha.write(response.getOutputStream());
        }catch (IOException e){
            throw new BusinessException("获取验证码失败");
        }
    }
}
