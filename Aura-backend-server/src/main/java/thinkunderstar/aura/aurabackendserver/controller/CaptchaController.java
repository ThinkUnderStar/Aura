package thinkunderstar.aura.aurabackendserver.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import thinkunderstar.aura.aurabackendserver.service.core.CaptchaService;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {
    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    /**
     * 获取人机验证图片
     *
     * @param response 响应对象
     * @param tempKey 临时Key
     */
    @GetMapping("/get")
    public void getCaptcha(HttpServletRequest request, HttpServletResponse response, @RequestParam String tempKey) {
        captchaService.getCaptcha(request,response,tempKey);
    }
}
