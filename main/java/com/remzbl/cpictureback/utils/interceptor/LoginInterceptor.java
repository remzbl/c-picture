package com.remzbl.cpictureback.utils.interceptor;

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
        // 放行 OPTIONS 请求
        if("OPTIONS".equals(request.getMethod().toUpperCase())) {
            System.out.println("Method:OPTIONS");
            return true;
        }


        // 直接检查 UserHolder（由 RefreshTokenInterceptor 填充）
        log.debug("检查用户是否登录{}" , UserHolder.getUser());
        if (UserHolder.getUser() == null) {
            response.setStatus(40100000);
            return false;
        }
        return true;
    }
}
