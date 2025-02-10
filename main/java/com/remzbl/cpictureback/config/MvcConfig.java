package com.remzbl.cpictureback.config;

import com.remzbl.cpictureback.utils.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {


        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .order(0)
                .addPathPatterns(
                        "/api/file/test/**",
                        "/api/picture/**",
                        "/api/space/**",
                        "/api/user/**"
                )
                .excludePathPatterns(
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
