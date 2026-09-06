package thinkunderstar.aura.aurabackendserver.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SaInterceptor saInterceptor = new SaInterceptor();

        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                            throws Exception {
                        // 异步分发（SSE/SseEmitter）阶段跳过重复鉴权，
                        if (request.getDispatcherType() == DispatcherType.ASYNC) {
                            return true;
                        }
                        return saInterceptor.preHandle(request, response, handler);
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/code",
                        "/auth/register/user",
                        "/captcha/get"
                );
    }
}
