package org.jeecg.config;

import org.jeecg.config.sign.interceptor.TimeExpirationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Description: 注册系统过期拦截器
 */
@Configuration
public class TimeExpirationWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册拦截器
        registry.addInterceptor(new TimeExpirationInterceptor())
                .addPathPatterns("/**") // 拦截所有请求
                // 可选：排除一些不需要拦截的路径，例如静态资源或特定的无需授权接口
                .excludePathPatterns(
                    "/sys/common/static/**", // 静态资源
                    "/doc.html",             // Swagger UI
                    "/webjars/**",
                    "/swagger-resources/**",
                    "/error"
                );
    }
}