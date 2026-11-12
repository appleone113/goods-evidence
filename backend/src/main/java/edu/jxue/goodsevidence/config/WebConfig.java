package edu.jxue.goodsevidence.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Web 配置：注册 JWT 拦截器，放行登录/注册/验真等公开接口
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        List<String> exclude = new ArrayList<>();
        exclude.add("/api/auth/login");
        exclude.add("/api/auth/register");
        exclude.add("/api/verify/**");
        exclude.add("/api/file/**");
        exclude.add("/doc.html");
        exclude.add("/webjars/**");
        exclude.add("/swagger-resources/**");
        exclude.add("/v3/api-docs/**");

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(exclude);
    }
}
