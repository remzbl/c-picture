package com.remzbl.cpictureback.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域是指浏览器访问的 URL（前端地址）和后端接口地址的域名（或端口号）不一致导致的，浏览器为了安全，默认禁止跨域请求访问。
 * 为了开发调试方便，我们可以通过全局跨域配置，让整个项目所有的接口支持跨域，解决跨域报错。
 * 全局跨域配置
 */
//@Configuration
//public class CorsConfig implements WebMvcConfigurer {
//
//    //CorsRegistry registry 是 注册和管理跨域资源共享（CORS）的配置规则的对象
//    @Override                      // registry翻译为注册表
//    public void addCorsMappings(CorsRegistry registry ) {
//        // 覆盖所有请求
//        registry.addMapping("/**")
//                // 允许发送 Cookie
//                .allowCredentials(true)
//                // 放行哪些域名（必须用 patterns，否则 * 会和 allowCredentials 冲突）
//                .allowedOriginPatterns("*")
//                // 放行哪些请求方式
//                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
//                // 放行哪些原始请求头部信息
//                .allowedHeaders("*")
//                // 暴露哪些头部信息（因为跨域访问默认不能获取全部头部信息）
//                .exposedHeaders("*")
//                .maxAge(3600);
//    }
//}

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization","*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
