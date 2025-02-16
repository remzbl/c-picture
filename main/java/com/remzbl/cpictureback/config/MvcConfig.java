package com.remzbl.cpictureback.config;

import com.remzbl.cpictureback.utils.interceptor.LoginInterceptor;
import com.remzbl.cpictureback.utils.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

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
                .excludePathPatterns(
                        //接口文档释放
                        "/**/webjars/**",
                        "/**/v2/**",
                        "/**/swagger-ui.html/**",
                        "/**/doc.html",
                        "/**/swagger-resources",
                        "/**/v2/api-docs",

                        //功能接口释放
                        "/api/picture/list/page/vo",
                        "/api/picture/list/page/vo/cache",
                        "/api/picture/get/vo",
                        "/api/picture/tag_category"

                )
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
                        "/api/health",
                        "/api/user/login",
                        "/api/user/register",
                        "/api/picture/tag_category",
                        "/api/picture/list/page/vo",
                        "/api/picture/list/page/vo/cache",
                        "/api/picture/get/vo",
                        "/api/space/list/level"

                );

    }


    public void addResourceHandlers(ResourceHandlerRegistry registry){
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }


}
