package thinkunderstar.aura.aurabackendserver.service.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface CaptchaService {
    /**
     * 获取人机验证图片
     *
     * @param response 响应对象
     * @param tempKey 临时Key
     */
    void getCaptcha(HttpServletRequest request, HttpServletResponse response, String tempKey);
}
