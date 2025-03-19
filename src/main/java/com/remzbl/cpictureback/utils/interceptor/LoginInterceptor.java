package com.remzbl.cpictureback.utils.interceptor;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uri = request.getRequestURI();
        String token = request.getHeader("Authorization");
        // 因为查询图片接口是复用接口 , 即需要开放给所有用户 , 所以在这个需要登录的拦截器中放行未登录用户
        if (StrUtil.isBlank(token) && UserHolder.getUser()==null && uri.contains("list/page/vo/cache") )  {
            return true;
        }


        // 直接检查 UserHolder（由 RefreshTokenInterceptor 填充）
        log.debug("检查用户是否登录{}" , UserHolder.getUser());
        if (UserHolder.getUser() == null) {
            response.setStatus(40100);
            return false;
        }
        // 日志记录当前方法调用的接口信息
        log.info("拦截器2从UserHolder获取用户：{}", UserHolder.getUser());
        return true;
    }
}
