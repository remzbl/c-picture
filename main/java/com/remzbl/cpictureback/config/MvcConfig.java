package com.remzbl.cpictureback.config;

import com.remzbl.cpictureback.utils.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

//@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {


        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .order(1)
                .addPathPatterns(
                        "/file/test/**",
                        "/picture/**",
                        "/space/**",
                        "/user/**"
                )
                .excludePathPatterns(
                        "/health",
                        "/user/login",
                        "/user/register",
                        "/picture/tag_category",
                        "/picture/list/page/vo",
                        "/picture/get/vo",
                        "/space/list/level"
                );

    }

}
