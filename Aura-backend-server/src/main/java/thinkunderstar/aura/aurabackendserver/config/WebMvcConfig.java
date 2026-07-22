package thinkunderstar.aura.aurabackendserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    //资源路径映射
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:./docs/avatars/");

        registry.addResourceHandler("/uploads/workspace_logos/**")
                .addResourceLocations("file:./docs/workspace_logos/");
    }
}
