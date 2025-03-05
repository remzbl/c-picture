
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

    @Resource
    RefreshTokenInterceptor refreshTokenInterceptor;

    @Resource
    LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先注册 Token 刷新拦截器（order=0）
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
        registry.addInterceptor(refreshTokenInterceptor)

                .order(0)
                .addPathPatterns(
                        "/**"
//                        "/api/picture/list/page/vo/privatecache",
//                        "/api/**"
                )
                .excludePathPatterns(
                        //接口文档释放
                        "/**/webjars/**",
                        "/**/v2/**",
                        "/**/swagger-ui.html/**",
                        "/**/doc.html",
                        "/**/swagger-resources",
                        "/**/v2/api-docs",

                        //功能接口释放
                        "/api/user/register",
                        "/api/user/login",
                        "/api/picture/list/page/vo",
                        "/api/picture/list/page/vo/cache",
                        "/api/picture/get/vo",
                        "/api/picture/tag_category"

                );

        // 2. 再注册登录拦截器（order=1）
        registry.addInterceptor(loginInterceptor)
                .order(1)
                .addPathPatterns(
////                      "/api/file/test/**",
//                        "/api/picture/**",
//                        //"/api/space/**",
//                        "/api/user/**",
//                        "/api/picture/list/page/vo/privatecache",

                        "/api/picture/upload",
                        "/api/picture/upload/url",
                        "/api/picture/upload/batch",
                        "/api/picture/delete",
                        "/api/picture/edit",
                        "/api/picture/update",
                        "/api/picture/get/vo",
//                        "/api/picture/list/page/vo",
//                        "/api/picture/list/page/vo/cache",
//                        "/api/picture/list/page/vo/privatecache",
                        "/api/picture/review",

                        "/api/space/add",
                        "/api/space/delete",
                        "/api/space/edit",

                        "/api/user/get/login",
                        "/api/space/add",
                        "/api/space/add"


                );

    }


    public void addResourceHandlers(ResourceHandlerRegistry registry){
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }


}
