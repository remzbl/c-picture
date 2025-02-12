package com.remzbl.cpictureback.config;

import com.remzbl.cpictureback.utils.interceptor.LoginInterceptor;
import com.remzbl.cpictureback.utils.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

// MvcConfig.java
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    RefreshTokenInterceptor refreshTokenInterceptor;

    @Autowired
    LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先注册 Token 刷新拦截器（order=0）
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);

        // 2. 再注册登录拦截器（order=1）
        registry.addInterceptor(new LoginInterceptor())
                .order(1)
                .addPathPatterns(
                        "/api/file/test/**",
                        "/api/picture/**",
                        "/api/space/**",
                        "/api/user/**"
                )
                .excludePathPatterns(
                        "/api/doc.html",
                        "/api/health",
                        "/api/user/login",
                        "/api/user/register",
                        "/api/picture/tag_category",
                        "/api/picture/list/page/vo",
                        "/api/picture/get/vo",
                        "/api/space/list/level"
                );
    }
}
